package com.crm.order.order.service.impl;

import com.crm.messaging.outbox.OutboxProperties;
import com.crm.order.account.AccountServiceClient;
import com.crm.order.account.AccountSummary;
import com.crm.order.common.exception.BusinessException;
import com.crm.order.common.exception.MessageKeys;
import com.crm.order.lookup.LookupContract;
import com.crm.order.order.ProcessingStatus;
import com.crm.order.order.dto.request.OrderDraftCreateRequest;
import com.crm.order.order.dto.request.OrderSubmitRequest;
import com.crm.order.order.dto.response.OrderStatusResponse;
import com.crm.order.order.entity.CustomerOrder;
import com.crm.order.order.mapper.OrderMapper;
import com.crm.order.order.repository.CustomerOrderRepository;
import com.crm.order.order.service.SaleDraftService;
import com.crm.order.saga.SaleSaga;
import com.crm.order.saga.SaleSagaRepository;
import com.crm.order.saga.SaleSagaState;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The asynchronous SALE flow (ADR-018 §6).
 *
 * <p>Compare this class with {@link OrderServiceImpl} and the change is the whole point
 * of ADR-018: there is no product-service call, no account involvement call, no
 * compensation ladder and no comment explaining what happens when the third of five
 * synchronous writes fails. What is left is a precondition read, a local transaction, and
 * a status resource. Everything that used to make a user wait now happens behind the
 * saga.
 */
@Service
@RequiredArgsConstructor
public class SaleDraftServiceImpl implements SaleDraftService {

    private final SaleDraftPersistence persistence;
    private final CustomerOrderRepository orderRepository;
    private final SaleSagaRepository sagaRepository;
    private final OrderMapper mapper;
    private final AccountServiceClient accountClient;
    private final OutboxProperties outboxProperties;

    @Override
    public OrderStatusResponse createDraft(OrderDraftCreateRequest request) {
        AccountSummary account = requireActiveAccount(request.getAccountNumber());
        CustomerOrder draft = persistence.createDraft(request.getAccountNumber(), account);
        return new OrderStatusResponse(draft.getOrderNumber(), LookupContract.STATUS_WAITING,
                ProcessingStatus.DRAFT, null, draft.getCreatedDate());
    }

    @Override
    public void abandonDraft(String orderNumber) {
        persistence.abandon(orderNumber);
    }

    @Override
    public OrderStatusResponse submitDraft(String orderNumber, OrderSubmitRequest request) {
        requireAsyncSaleAvailable();
        // Revalidated at Submit, not merely at draft creation: an account can be
        // passivated in between, and AC-SALE-02-01 is a rule about selling, not about
        // starting a screen. The saga re-asks the same question as its first step —
        // this check is what turns a passive account into an immediate, actionable 409
        // instead of a sale the user watches fail.
        AccountSummary account = requireActiveAccount(accountNumberOf(orderNumber));
        SaleDraftPersistence.SubmittedDraft submitted = persistence.submit(orderNumber, request, account);
        return new OrderStatusResponse(submitted.orderNumber(), submitted.orderStatus(),
                ProcessingStatus.PROCESSING, null, submitted.updatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatusResponse status(String orderNumber) {
        CustomerOrder order = orderRepository.findByOrderNumberWithItems(orderNumber)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.ORDER_NOT_FOUND,
                        "Order " + orderNumber + " not found"));
        String orderStatus = mapper.statusCode(order);
        Optional<SaleSaga> saga = sagaRepository.findById(orderNumber);
        if (saga.isPresent()) {
            SaleSaga current = saga.get();
            return new OrderStatusResponse(orderNumber, orderStatus, processingStatusOf(current.getCurrentState()),
                    clientVisibleFailureKey(current), current.getUpdatedAt());
        }
        return new OrderStatusResponse(orderNumber, orderStatus, withoutSaga(orderStatus),
                LookupContract.STATUS_CANCELLED.equals(orderStatus) ? MessageKeys.SALE_DRAFT_ABANDONED : null,
                lastTouched(order));
    }

    /**
     * The internal → public collapse (ADR-018 §6). Ten internal states become four public
     * ones; nothing about which compensation is outstanding crosses this boundary.
     */
    private static ProcessingStatus processingStatusOf(SaleSagaState state) {
        return switch (state) {
            case COMPLETED -> ProcessingStatus.COMPLETED;
            case FAILED, MANUAL_INTERVENTION -> ProcessingStatus.FAILED;
            default -> ProcessingStatus.PROCESSING;
        };
    }

    /**
     * An order with no saga row. Three cases, and they are not guesses:
     * <ul>
     *   <li>WAIT — a draft nobody submitted;</li>
     *   <li>CANCELLED — a draft that was abandoned or expired;</li>
     *   <li>MIDLWARE — an order created by the LEGACY synchronous route, which finished
     *       its whole orchestration before it answered, so it is complete by the time it
     *       exists at all.</li>
     * </ul>
     */
    private static ProcessingStatus withoutSaga(String orderStatus) {
        if (LookupContract.STATUS_WAITING.equals(orderStatus)) {
            return ProcessingStatus.DRAFT;
        }
        if (LookupContract.STATUS_CANCELLED.equals(orderStatus)) {
            return ProcessingStatus.FAILED;
        }
        return ProcessingStatus.COMPLETED;
    }

    /** Only a terminal failure carries a key; a running saga must not leak an interim one. */
    private static String clientVisibleFailureKey(SaleSaga saga) {
        return processingStatusOf(saga.getCurrentState()) == ProcessingStatus.FAILED
                ? saga.getFailureMessageKey() : null;
    }

    private static Instant lastTouched(CustomerOrder order) {
        return order.getUpdatedDate() != null ? order.getUpdatedDate() : order.getCreatedDate();
    }

    private String accountNumberOf(String orderNumber) {
        return orderRepository.findByOrderNumberWithItems(orderNumber)
                .map(order -> order.getBusinessInteraction().getCustomerAccountNumber())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.ORDER_NOT_FOUND,
                        "Order " + orderNumber + " not found"));
    }

    private AccountSummary requireActiveAccount(String accountNumber) {
        AccountSummary account = accountClient.fetchAccount(accountNumber)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.ACCT_NOT_FOUND,
                        "Account " + accountNumber + " not found"));
        if (!account.isActive()) {
            // AC-SALE-02-01, enforced server-side. The UI states this only as a disabled
            // action, and a disabled button is not a check.
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.ACCT_NOT_ACTIVE,
                    "Account " + accountNumber + " is passive and cannot start a new sale");
        }
        return account;
    }

    /**
     * Refuse Submit when the Outbox is off (ADR-018 §10).
     *
     * <p>Without this the recorder would accept the first command and write nothing (its
     * documented disabled behaviour, ADR-017 §11), the transaction would commit, and the
     * client would get a 202 for a sale that can never progress — a MIDLWARE order with a
     * saga waiting forever for a reply to a command that was never sent. Failing closed
     * with the general unavailability key is the honest answer to "asynchronous
     * processing is not switched on here".
     */
    private void requireAsyncSaleAvailable() {
        if (!outboxProperties.isEnabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, MessageKeys.SERVICE_UNAVAILABLE,
                    "Asynchronous sale processing is not enabled in this environment");
        }
    }
}
