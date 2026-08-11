package com.crm.order.order.service.impl;

import com.crm.observability.starter.MdcKeys;
import com.crm.order.account.AccountSummary;
import com.crm.order.common.CurrentActorProvider;
import com.crm.order.common.exception.BusinessException;
import com.crm.order.common.exception.MessageKeys;
import com.crm.order.lookup.LookupCatalogService;
import com.crm.order.lookup.LookupContract;
import com.crm.order.order.OrderContract;
import com.crm.order.order.dto.request.OrderCharacteristicRequest;
import com.crm.order.order.dto.request.OrderItemRequest;
import com.crm.order.order.dto.request.OrderSubmitRequest;
import com.crm.order.order.entity.BusinessInteraction;
import com.crm.order.order.entity.CustomerOrder;
import com.crm.order.order.entity.CustomerOrderItem;
import com.crm.order.order.number.OrderNumberGenerator;
import com.crm.order.order.repository.BusinessInteractionRepository;
import com.crm.order.order.repository.CustomerOrderRepository;
import com.crm.order.saga.SaleRequestSnapshot;
import com.crm.order.saga.SaleSagaOrchestrator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The order_db writes of the DRAFT lifecycle (ADR-018 §6): create, submit, abandon,
 * expire.
 *
 * <p>A separate bean from {@link OrderPersistence} for the same proxy reason that class
 * documents — {@code @Transactional} is proxy-based and self-invocation silently bypasses
 * it — and because these three writes belong to a different lifecycle: they move an order
 * BETWEEN business statuses, where {@code OrderPersistence} fills an order in.
 *
 * <p><b>{@link #submit} is the transaction the whole asynchronous design rests on.</b>
 * The order lines, the WAIT → MIDLWARE transition, the saga row and the saga's first
 * command are one local commit in one database. That is what makes the 202 honest: by the
 * time the client is told the sale is processing, the fact that it must be processed is
 * durable, and it stays durable across a broker outage, a consumer restart and a restart
 * of this service.
 */
@Service
@RequiredArgsConstructor
public class SaleDraftPersistence {

    private static final Logger log = LoggerFactory.getLogger(SaleDraftPersistence.class);

    private final CustomerOrderRepository orderRepository;
    private final BusinessInteractionRepository interactionRepository;
    private final OrderNumberGenerator numberGenerator;
    private final LookupCatalogService lookupCatalog;
    private final CurrentActorProvider actorProvider;
    private final SaleSagaOrchestrator orchestrator;

    /**
     * Start New Sale (AC-SALE-01-12, analyst decision of 2026-08-10): the BSN_INTER and
     * CUST_ORD rows exist from here on, with status WAIT, and the KR-12 number is
     * allocated inside this transaction so a rollback takes the sequence increment with
     * it (gapless — ADR-016 §4.4).
     *
     * <p><b>No item, no product, no saga and no message.</b> The draft is inert by
     * construction: nothing consumes a WAIT order, no command exists that names one, and
     * the recovery job only ever looks at saga rows — of which a draft has none. That is
     * what makes AC-SALE-01-16 ("an abandoned sale must never be processed later") true
     * of a draft that the browser simply walks away from, without relying on the browser
     * to tell us it did.
     */
    @Transactional
    public CustomerOrder createDraft(String accountNumber, AccountSummary account) {
        long newSaleTypeId = lookupCatalog.resolveTypeId("bsnInterType",
                LookupContract.TYPE_NEW_SALE, LookupContract.TYPE_DOMAIN_BSN_INTER);
        long waitingStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_WAITING, LookupContract.STATUS_DOMAIN_ORDER);
        String actor = actorProvider.get();

        BusinessInteraction interaction = new BusinessInteraction();
        interaction.setCustomerAccountNumber(accountNumber);
        interaction.setBusinessInteractionTypeId(newSaleTypeId);
        interaction.setDescription(OrderContract.NEW_SALE_DESCRIPTION);
        interaction.setStatusId(waitingStatusId);
        interaction.markCreated(actor);
        interactionRepository.save(interaction);

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(numberGenerator.next(OrderContract.SEGMENT_NEW_SALE));
        MDC.put(MdcKeys.ORDER_NUMBER, order.getOrderNumber());
        order.setCustomerNumber(account.customerNumber());
        order.setBusinessInteraction(interaction);
        order.setStatusId(waitingStatusId);
        order.markCreated(actor);
        return orderRepository.save(order);
    }

    /**
     * Confirm Submit: WAIT → MIDLWARE, the order lines, the saga and its first command —
     * one transaction (ADR-018 §6).
     *
     * <p>Items are written with NULL product_id/amount exactly as the synchronous path
     * writes them: the products do not exist yet, and the saga fills them in from
     * product-service's own reply.
     *
     * @throws BusinessException 409 {@code MSG-ORDER-NOT-DRAFT} if the order is not WAIT.
     *         This is the guard that makes double submission impossible and that keeps
     *         the legacy synchronous route and this one from ever both running for one
     *         sale: a legacy order is created MIDLWARE and can therefore never be
     *         submitted here.
     */
    @Transactional
    public SubmittedDraft submit(String orderNumber, OrderSubmitRequest request, AccountSummary account) {
        CustomerOrder order = orderRepository.findByOrderNumberWithItems(orderNumber)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.ORDER_NOT_FOUND,
                        "Order " + orderNumber + " not found"));
        requireDraft(order);
        // The draft fixes the account. A submit whose basket was built against a
        // different account is rejected rather than silently re-pointed.
        String accountNumber = order.getBusinessInteraction().getCustomerAccountNumber();
        if (!accountNumber.equals(account.accountNumber())) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.ORDER_NOT_DRAFT,
                    "Order " + orderNumber + " does not belong to account " + account.accountNumber());
        }

        long inProgressStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_MIDDLEWARE, LookupContract.STATUS_DOMAIN_ORDER);
        long activeStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_ACTIVE, LookupContract.STATUS_DOMAIN_GENERAL);
        String actor = actorProvider.get();

        for (OrderItemRequest item : request.getItems()) {
            CustomerOrderItem orderItem = new CustomerOrderItem();
            orderItem.setProductOfferId(item.getOfferId());
            orderItem.setStatusId(activeStatusId);
            orderItem.markCreated(actor);
            order.addItem(orderItem);
        }
        order.setStatusId(inProgressStatusId);
        order.markUpdated(actor);
        order.getBusinessInteraction().setStatusId(inProgressStatusId);
        order.getBusinessInteraction().markUpdated(actor);
        CustomerOrder saved = orderRepository.save(order);

        // Same transaction (the orchestrator is Propagation.MANDATORY, so it could not be
        // anywhere else): saga row + first Outbox command.
        orchestrator.start(orderNumber, accountNumber, toSnapshot(request));
        log.info("Order {} submitted; saga started", orderNumber);
        return new SubmittedDraft(saved.getOrderNumber(), LookupContract.STATUS_MIDDLEWARE,
                saved.getUpdatedDate());
    }

    /**
     * Explicit Cancel-before-submit (ADR-018 §6). WAIT only, and idempotent.
     *
     * <p>It is emphatically NOT an order-cancellation feature: a MIDLWARE order is
     * refused with 409, so this can never become the user-facing post-submit cancel that
     * KR-7 keeps out of phase. Repeating it on a draft that is already CANCELLED is a
     * success with no write — a client that lost the response must be able to ask again.
     */
    @Transactional
    public void abandon(String orderNumber) {
        CustomerOrder order = orderRepository.findByOrderNumberWithItems(orderNumber)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.ORDER_NOT_FOUND,
                        "Order " + orderNumber + " not found"));
        long cancelledStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_CANCELLED, LookupContract.STATUS_DOMAIN_ORDER);
        if (Long.valueOf(cancelledStatusId).equals(order.getStatusId())) {
            return;   // already abandoned — idempotent no-op
        }
        requireDraft(order);
        String actor = actorProvider.get();
        order.setStatusId(cancelledStatusId);
        order.markUpdated(actor);
        order.getBusinessInteraction().setStatusId(cancelledStatusId);
        order.getBusinessInteraction().markUpdated(actor);
        orderRepository.save(order);
        log.info("Draft order {} abandoned before submit", orderNumber);
    }

    /**
     * Expire drafts the browser never came back for (ADR-018 §6).
     *
     * <p>It CANCELS and nothing else. There is no code path here — and none anywhere —
     * that can submit or fulfil a draft, which is the property AC-SALE-01-16 needs and
     * the reason this job is safe to run unattended.
     *
     * @return how many drafts were cancelled
     */
    @Transactional
    public int cancelStaleDrafts(Instant staleBefore, int batchSize) {
        long waitingStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_WAITING, LookupContract.STATUS_DOMAIN_ORDER);
        long cancelledStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_CANCELLED, LookupContract.STATUS_DOMAIN_ORDER);
        List<CustomerOrder> stale = orderRepository.findStaleDrafts(waitingStatusId, staleBefore,
                org.springframework.data.domain.Limit.of(batchSize));
        if (stale.isEmpty()) {
            return 0;
        }
        String actor = actorProvider.get();
        for (CustomerOrder order : stale) {
            order.setStatusId(cancelledStatusId);
            order.markUpdated(actor);
            order.getBusinessInteraction().setStatusId(cancelledStatusId);
            order.getBusinessInteraction().markUpdated(actor);
            orderRepository.save(order);
        }
        log.info("Cancelled {} stale WAIT draft(s) older than {}", stale.size(), staleBefore);
        return stale.size();
    }

    private void requireDraft(CustomerOrder order) {
        long waitingStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_WAITING, LookupContract.STATUS_DOMAIN_ORDER);
        if (!Long.valueOf(waitingStatusId).equals(order.getStatusId())) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.ORDER_NOT_DRAFT,
                    "Order " + order.getOrderNumber() + " is not a draft awaiting submission");
        }
    }

    private SaleRequestSnapshot toSnapshot(OrderSubmitRequest request) {
        List<SaleRequestSnapshot.Item> items = new ArrayList<>();
        for (OrderItemRequest item : request.getItems()) {
            Map<Long, String> characteristics = new LinkedHashMap<>();
            for (OrderCharacteristicRequest characteristic : item.getCharacteristics()) {
                characteristics.put(characteristic.getCharacteristicId(), characteristic.getValue());
            }
            items.add(new SaleRequestSnapshot.Item(item.getOfferId(), characteristics));
        }
        return new SaleRequestSnapshot(request.getServiceAddressId(), request.getCampaignId(), items);
    }

    /** What the 202 needs to know, read while the transaction that produced it is open. */
    public record SubmittedDraft(String orderNumber, String orderStatus, Instant updatedAt) {
    }
}
