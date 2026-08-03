package com.crm.order.order.service.impl;

import com.crm.order.account.AccountServiceClient;
import com.crm.order.account.AccountSummary;
import com.crm.order.common.exception.BusinessException;
import com.crm.order.common.exception.MessageKeys;
import com.crm.order.order.dto.request.OrderCharacteristicRequest;
import com.crm.order.order.dto.request.OrderCreateRequest;
import com.crm.order.order.dto.request.OrderItemRequest;
import com.crm.order.order.dto.response.OrderResponse;
import com.crm.order.order.entity.CustomerOrder;
import com.crm.order.order.mapper.OrderMapper;
import com.crm.order.order.repository.CustomerOrderRepository;
import com.crm.order.order.service.OrderService;
import com.crm.order.product.ProductCreationCommand;
import com.crm.order.product.ProductCreationResult;
import com.crm.order.product.ProductServiceClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The FR-SALE-01 sale orchestration (ADR-016 §5).
 *
 * <p>The flow writes to three databases with no distributed transaction, no saga
 * framework and no broker. The design principle is: <b>one commit point, everything
 * before it discardable by construction, everything after it non-destructive.</b>
 *
 * <p>Note what is deliberately NOT transactional: {@link #submit}. Holding a database
 * transaction open across three synchronous HTTP calls would pin a connection for the
 * duration of the slowest downstream and turn a product-service hiccup into
 * connection-pool exhaustion. Each local write is its own short transaction in
 * {@link OrderPersistence} — which is exactly why compensation steps exist here
 * instead of a rollback.
 *
 * <p>This class holds NO basket or catalog logic: composition and characteristic
 * rules live in product-service (ADR-015 §6), and errors are relayed with the
 * upstream's own message key unchanged (ADR-016 §7).
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderPersistence persistence;
    private final CustomerOrderRepository orderRepository;
    private final OrderMapper mapper;
    private final AccountServiceClient accountClient;
    private final ProductServiceClient productClient;

    @Override
    public OrderResponse submit(OrderCreateRequest request) {
        // ---- Step 0: preconditions (reads only; nothing written anywhere) --------
        AccountSummary account = accountClient.fetchAccount(request.getAccountNumber())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.ACCT_NOT_FOUND,
                        "Account " + request.getAccountNumber() + " not found"));
        if (!account.isActive()) {
            // AC-SALE-02-01 enforced server-side. The UI states this only as a
            // disabled action, and a disabled button is not a check.
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.ACCT_NOT_ACTIVE,
                    "Account " + request.getAccountNumber() + " is passive and cannot start a new sale");
        }

        // ---- Step 1: the local write — one transaction, all-or-nothing -----------
        // If this fails the caller gets an error and NO external call was ever made,
        // so there is nothing to unwind. The whole design rests on this property,
        // which is why the order is written first (ADR-016 §5.2).
        CustomerOrder order = persistence.persistOrder(request, account);
        long orderId = order.getId();

        // ---- Step 2: create the products (PNDG) ---------------------------------
        ProductCreationResult creation;
        try {
            creation = productClient.createProducts(toCreationCommand(request, account));
        } catch (RuntimeException e) {
            // Includes ProductValidationException: the basket was rejected, so the
            // order just written must not linger as a half-made sale. The original
            // exception (and its message key) propagates unchanged.
            persistence.cancelOrder(orderId, "product creation failed");
            throw e;
        }

        // ---- Step 3: fill in what only step 2 could know (local) -----------------
        try {
            persistence.attachProducts(orderId, creation);
        } catch (RuntimeException e) {
            compensate(orderId, creation, "order/product linking failed");
            throw e;
        }

        // ---- Step 4: promote the products to ACTV -------------------------------
        // Deliberately BEFORE the involvement write, so the only compensation the
        // cancel command ever performs is on never-committed rows. An endpoint able
        // to passivate a COMMITTED product would be the KR-7 cancellation that is out
        // of phase, arriving by the back door (ADR-015 §5.7).
        try {
            productClient.confirmProducts(creation.productIds());
        } catch (RuntimeException e) {
            compensate(orderId, creation, "product confirmation failed");
            throw e;
        }

        // ---- Step 5: THE COMMIT POINT -------------------------------------------
        // Linking the products to the billing account is what makes the sale real and
        // visible (AC-SALE-01-01; FR-PROD-01 is involvement-driven). Nothing follows
        // it, so nothing ever needs to undo it — which is precisely why ADR-013 §8.6
        // declines to create an involvement-delete command.
        try {
            accountClient.linkProducts(request.getAccountNumber(), creation.productIds());
        } catch (RuntimeException e) {
            compensate(orderId, creation, "account involvement write failed");
            throw e;
        }

        return persistence.read(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumberWithItems(orderNumber)
                .map(mapper::toResponse)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.ORDER_NOT_FOUND,
                        "Order " + orderNumber + " not found"));
    }

    /**
     * Undo everything this request created: discard the products, then mark the order
     * CANCELLED. The products are still PNDG (or, after step 4, ACTV but linked to no
     * account — invisible to FR-PROD-01 either way), so nothing the customer can see
     * is being removed.
     *
     * <p>A failing compensation is logged and swallowed, never rethrown: it runs while
     * the real exception is already propagating, and replacing that cause with a
     * compensation failure would tell the user the wrong thing about what went wrong.
     * The residue it leaves is identifiable by a single query and is recorded as an
     * operational follow-up (ADR-015 §8.4) rather than papered over with a
     * speculative reconciliation job.
     */
    private void compensate(long orderId, ProductCreationResult creation, String reason) {
        try {
            productClient.cancelProducts(creation.productIds());
        } catch (RuntimeException e) {
            log.error("Could not discard products {} after: {}", creation.productIds(), reason, e);
        }
        persistence.cancelOrder(orderId, reason);
    }

    private ProductCreationCommand toCreationCommand(OrderCreateRequest request, AccountSummary account) {
        List<ProductCreationCommand.Item> items = new ArrayList<>();
        for (OrderItemRequest item : request.getItems()) {
            Map<Long, String> characteristics = new LinkedHashMap<>();
            for (OrderCharacteristicRequest characteristic : item.getCharacteristics()) {
                characteristics.put(characteristic.getCharacteristicId(), characteristic.getValue());
            }
            items.add(new ProductCreationCommand.Item(item.getOfferId(), characteristics));
        }
        // customerNumber comes from the ACCOUNT, never from the request body: it is
        // what product-service checks the service address against (ADR-015 §5.9), so
        // letting a caller supply it would defeat the check it enables.
        return new ProductCreationCommand(account.customerNumber(), request.getServiceAddressId(),
                request.getCampaignId(), items);
    }
}
