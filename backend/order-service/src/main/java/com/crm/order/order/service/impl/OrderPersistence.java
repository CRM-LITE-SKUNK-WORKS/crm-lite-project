package com.crm.order.order.service.impl;

import com.crm.messaging.outbox.OutboxRecorder;
import com.crm.observability.starter.MdcKeys;
import com.crm.order.account.AccountSummary;
import com.crm.order.common.CurrentActorProvider;
import com.crm.order.lookup.LookupCatalogService;
import com.crm.order.lookup.LookupContract;
import com.crm.order.messaging.OrderEventContracts;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final OutboxRecorder outboxRecorder;

    /**
     * Step 1 (ADR-016 §5.1): BSN_INTER + CUST_ORD + CUST_ORD_ITEM in ONE transaction,
     * status MIDLWARE. The KR-12 number is allocated inside it, so a rollback takes
     * the sequence increment with it (gapless — ADR-016 §4.4).
     *
     * <p>Items are written with NULL product_id/amount: the products do not exist
     * yet. That intermediate state is never observable — it lives only inside this
     * request, and a request that dies here leaves the order CANCELLED.
     *
     * <p><b>The Outbox record at the end is part of this same transaction</b> (ADR-017
     * §8.1). It is not an afterthought placed here for convenience: {@code OutboxRecorder}
     * is {@code Propagation.MANDATORY}, so it can only ever run inside a caller's
     * transaction, and this is the transaction the message is a fact about. Either the
     * order and its message both exist or neither does.
     *
     * <p>Recording is off in shipped configuration ({@code crm.messaging.outbox.enabled},
     * default false — ADR-017 §11), so today this call writes nothing and
     * {@code POST /api/orders} is unchanged. Nothing consumes the message either: the
     * synchronous orchestration in ADR-016 §5 is still the live SALE route.
     */
    @Transactional
    public CustomerOrder persistOrder(OrderCreateRequest request, AccountSummary account,
                                      String idempotencyKey) {
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
        CustomerOrder saved = orderRepository.save(order);

        // Same transaction, no broker involved (ADR-017 §8.1). The sagaId is the client's
        // Idempotency-Key rather than a new id: the sale already HAS an identity the
        // client chose and can retry with (ADR-016 §10), and minting a second one would
        // mean two ids for one sale that nobody can correlate afterwards.
        outboxRecorder.record(
                OrderEventContracts.ORDER_SUBMITTED_DESTINATION,
                OrderEventContracts.MESSAGE_TYPE,
                OrderEventContracts.AGGREGATE_TYPE,
                saved.getOrderNumber(),
                idempotencyKey,
                toSubmittedPayload(saved, request, account));
        return saved;
    }

    private OrderEventContracts.OrderSubmittedPayload toSubmittedPayload(
            CustomerOrder order, OrderCreateRequest request, AccountSummary account) {
        List<OrderEventContracts.OrderSubmittedPayload.Item> items = new ArrayList<>();
        for (OrderItemRequest item : request.getItems()) {
            Map<Long, String> characteristics = new LinkedHashMap<>();
            item.getCharacteristics().forEach(c -> characteristics.put(c.getCharacteristicId(), c.getValue()));
            items.add(new OrderEventContracts.OrderSubmittedPayload.Item(item.getOfferId(), characteristics));
        }
        return new OrderEventContracts.OrderSubmittedPayload(
                order.getOrderNumber(),
                request.getAccountNumber(),
                account.customerNumber(),
                request.getServiceAddressId(),
                request.getCampaignId(),
                items);
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

    /**
     * The asynchronous counterpart of {@link #attachProducts(long, ProductCreationResult)}
     * (ADR-018 §5, step 2 reply): the same local write, addressed by order NUMBER and fed
     * from a message rather than from an HTTP response.
     *
     * <p>Two methods and not one overload of a shared type, deliberately: the synchronous
     * path's {@code ProductCreationResult} is an HTTP client DTO owned by
     * {@code com.crm.order.product}, and making the saga depend on it would tie the
     * message contract to the shape of a REST response that ADR-018 exists to stop using.
     *
     * <p>Runs in the caller's transaction — which for the saga is the one holding the
     * Inbox claim and the outgoing command — so the order lines, the saga transition and
     * the next command commit together.
     */
    @Transactional
    public void attachProducts(String orderNumber, List<ProductSnapshot> products, BigDecimal totalAmount) {
        CustomerOrder order = orderRepository.findByOrderNumberWithItems(orderNumber).orElseThrow(
                () -> new IllegalStateException("No order " + orderNumber + " to attach products to"));
        Map<Long, ProductSnapshot> byOffer = new LinkedHashMap<>();
        products.forEach(product -> byOffer.put(product.offerId(), product));

        String actor = actorProvider.get();
        for (CustomerOrderItem item : order.getItems()) {
            ProductSnapshot created = byOffer.get(item.getProductOfferId());
            if (created == null) {
                // product-service answered for a different offer set than was ordered.
                // Throwing rolls back the whole reply — claim included — so the message
                // is redelivered rather than leaving lines pointing at nothing.
                throw new IllegalStateException("No product was reported for offer "
                        + item.getProductOfferId() + " (order " + orderNumber + ")");
            }
            item.setProductId(created.productId());
            item.setAmount(created.amount());
            item.markUpdated(actor);
        }
        order.setTotalAmount(totalAmount == null ? sumOf(order) : totalAmount);
        order.markUpdated(actor);
        orderRepository.save(order);
    }

    /** One order line's authoritative facts as the preparing service reported them. */
    public record ProductSnapshot(Long offerId, Long productId, BigDecimal amount) {
    }

    /**
     * Cancel by public number, <b>propagating failure</b> — the asynchronous counterpart
     * of {@link #cancelOrder(long, String)}.
     *
     * <p>The difference is the whole point. The synchronous version swallows, because it
     * runs while a real exception is already on its way to the user and replacing that
     * cause would misreport what went wrong. Here the cancel IS the work: a failure must
     * roll the transaction back so the reply is redelivered, rather than committing a
     * FAILED saga over an order still claiming to be MIDLWARE.
     */
    @Transactional
    public void cancelByOrderNumber(String orderNumber, String reason) {
        long cancelledStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_CANCELLED, LookupContract.STATUS_DOMAIN_ORDER);
        CustomerOrder order = orderRepository.findByOrderNumberWithItems(orderNumber).orElseThrow(
                () -> new IllegalStateException("No order " + orderNumber + " to cancel"));
        String actor = actorProvider.get();
        order.setStatusId(cancelledStatusId);
        order.markUpdated(actor);
        order.getBusinessInteraction().setStatusId(cancelledStatusId);
        order.getBusinessInteraction().markUpdated(actor);
        orderRepository.save(order);
        log.warn("Order {} cancelled: {}", orderNumber, reason);
    }

    /** The order's amount snapshot, for the terminal SaleCompleted fact. */
    @Transactional(readOnly = true)
    public BigDecimal totalAmountOf(String orderNumber) {
        return orderRepository.findByOrderNumberWithItems(orderNumber)
                .map(CustomerOrder::getTotalAmount)
                .orElse(null);
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
