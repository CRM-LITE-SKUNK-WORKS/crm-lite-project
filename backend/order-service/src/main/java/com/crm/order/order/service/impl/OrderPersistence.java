package com.crm.order.order.service.impl;

import com.crm.observability.starter.MdcKeys;
import com.crm.order.account.AccountSummary;
import com.crm.order.common.CurrentActorProvider;
import com.crm.order.lookup.LookupCatalogService;
import com.crm.order.lookup.LookupContract;
import com.crm.order.order.OrderContract;
import com.crm.order.order.dto.request.OrderCreateRequest;
import com.crm.order.order.dto.request.OrderItemRequest;
import com.crm.order.order.dto.response.OrderResponse;
import com.crm.order.order.entity.BusinessInteraction;
import com.crm.order.order.entity.CustomerOrder;
import com.crm.order.order.entity.CustomerOrderItem;
import com.crm.order.order.mapper.OrderMapper;
import com.crm.order.order.number.OrderNumberGenerator;
import com.crm.order.order.repository.BusinessInteractionRepository;
import com.crm.order.order.repository.CustomerOrderRepository;
import com.crm.order.product.ProductCreationResult;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The order_db half of the sale orchestration: every local write, each in its own
 * short transaction.
 *
 * <p><b>Why this is a separate bean and not private methods on
 * {@link OrderServiceImpl}.</b> Spring's {@code @Transactional} is proxy-based, so a
 * self-invocation ({@code this.persistOrder(...)}) bypasses the proxy entirely and
 * the annotation silently does nothing — the writes would run with no transaction at
 * all, and the KR-12 sequence increment would no longer roll back with a failed
 * order. Crossing a bean boundary is what makes the transactional guarantees this
 * design depends on actually apply.
 *
 * <p>It also states the transaction boundary honestly: {@link OrderServiceImpl#submit}
 * deliberately holds NO transaction across its three synchronous HTTP calls (that
 * would pin a connection for the duration of the slowest downstream), which is
 * exactly why compensation steps exist instead of a rollback.
 */
@Service
@RequiredArgsConstructor
public class OrderPersistence {

    private static final Logger log = LoggerFactory.getLogger(OrderPersistence.class);

    private final CustomerOrderRepository orderRepository;
    private final BusinessInteractionRepository interactionRepository;
    private final OrderNumberGenerator numberGenerator;
    private final OrderMapper mapper;
    private final LookupCatalogService lookupCatalog;
    private final CurrentActorProvider actorProvider;

    /**
     * Step 1 (ADR-016 §5.1): BSN_INTER + CUST_ORD + CUST_ORD_ITEM in ONE transaction,
     * status MIDLWARE. The KR-12 number is allocated inside it, so a rollback takes
     * the sequence increment with it (gapless — ADR-016 §4.4).
     *
     * <p>Items are written with NULL product_id/amount: the products do not exist
     * yet. That intermediate state is never observable — it lives only inside this
     * request, and a request that dies here leaves the order CANCELLED.
     */
    @Transactional
    public CustomerOrder persistOrder(OrderCreateRequest request, AccountSummary account) {
        // All three catalog values are resolved BEFORE anything is written: a catalog
        // outage must fail the sale closed, not leave a half-built order (ADR-002 §7).
        long newSaleTypeId = lookupCatalog.resolveTypeId("bsnInterType",
                LookupContract.TYPE_NEW_SALE, LookupContract.TYPE_DOMAIN_BSN_INTER);
        long inProgressStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_MIDDLEWARE, LookupContract.STATUS_DOMAIN_ORDER);
        long activeStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_ACTIVE, LookupContract.STATUS_DOMAIN_GENERAL);
        String actor = actorProvider.get();

        BusinessInteraction interaction = new BusinessInteraction();
        interaction.setCustomerAccountNumber(request.getAccountNumber());
        interaction.setBusinessInteractionTypeId(newSaleTypeId);
        interaction.setDescription(OrderContract.NEW_SALE_DESCRIPTION);
        interaction.setStatusId(inProgressStatusId);
        interaction.markCreated(actor);
        interactionRepository.save(interaction);

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(numberGenerator.next(OrderContract.SEGMENT_NEW_SALE));
        // Available to every log statement for the rest of THIS request (compensation
        // included) from here on; OrderController clears it once the response is built.
        MDC.put(MdcKeys.ORDER_NUMBER, order.getOrderNumber());
        order.setCustomerNumber(account.customerNumber());
        order.setBusinessInteraction(interaction);
        order.setStatusId(inProgressStatusId);
        order.markCreated(actor);

        for (OrderItemRequest item : request.getItems()) {
            CustomerOrderItem orderItem = new CustomerOrderItem();
            orderItem.setProductOfferId(item.getOfferId());
            orderItem.setStatusId(activeStatusId);
            orderItem.markCreated(actor);
            order.addItem(orderItem);
        }
        return orderRepository.save(order);
    }

    /**
     * Step 3 (ADR-016 §5.2): fill {@code product_id} and the amount snapshots only
     * step 2 could produce, plus the total. This second local transaction exists
     * solely because the workbook's CUST_ORD_ITEM carries a product id that cannot
     * exist when the header is written.
     */
    @Transactional
    public void attachProducts(long orderId, ProductCreationResult creation) {
        CustomerOrder order = orderRepository.findById(orderId).orElseThrow();
        Map<Long, ProductCreationResult.CreatedProduct> byOffer = new LinkedHashMap<>();
        creation.products().forEach(product -> byOffer.put(product.offerId(), product));

        String actor = actorProvider.get();
        for (CustomerOrderItem item : order.getItems()) {
            ProductCreationResult.CreatedProduct created = byOffer.get(item.getProductOfferId());
            if (created == null) {
                // product-service answered with a different offer set than we sent.
                // Never expected — but silently leaving product_id NULL would produce
                // an order whose lines point at nothing.
                throw new IllegalStateException("product-service returned no product for offer "
                        + item.getProductOfferId() + " (order " + order.getOrderNumber() + ")");
            }
            item.setProductId(created.productId());
            item.setAmount(created.amount());
            item.markUpdated(actor);
        }
        order.setTotalAmount(creation.totalAmount() == null ? sumOf(order) : creation.totalAmount());
        order.markUpdated(actor);
        orderRepository.save(order);
    }

    /**
     * Compensation: the order becomes CANCELLED (5) — the ONLY use of that status
     * (ADR-016 §6). It is never reachable by a user action; KR-7 keeps order
     * cancellation out of phase. The number is NOT released: a compensated order
     * keeps it forever (ADR-016 §4.6), matching KR-11's permanence.
     *
     * <p>Failures are logged and swallowed: this runs while another exception is
     * already propagating, and replacing the real cause with a compensation failure
     * would tell the user the wrong thing about what went wrong.
     */
    @Transactional
    public void cancelOrder(long orderId, String reason) {
        try {
            long cancelledStatusId = lookupCatalog.resolveStatusId("status",
                    LookupContract.STATUS_CANCELLED, LookupContract.STATUS_DOMAIN_ORDER);
            CustomerOrder order = orderRepository.findById(orderId).orElseThrow();
            String actor = actorProvider.get();
            order.setStatusId(cancelledStatusId);
            order.markUpdated(actor);
            order.getBusinessInteraction().setStatusId(cancelledStatusId);
            order.getBusinessInteraction().markUpdated(actor);
            orderRepository.save(order);
            log.warn("Order {} cancelled: {}", order.getOrderNumber(), reason);
        } catch (RuntimeException e) {
            log.error("Could not cancel order id {} after: {}", orderId, reason, e);
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse read(long orderId) {
        return orderRepository.findById(orderId).map(mapper::toResponse).orElseThrow();
    }

    /** Fallback total if the upstream did not derive one — never leave it NULL on a live order. */
    private BigDecimal sumOf(CustomerOrder order) {
        return order.getItems().stream()
                .map(CustomerOrderItem::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
