package com.crm.order.saga;

import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.outbox.OutboxRecorder;
import com.crm.observability.starter.MdcKeys;
import com.crm.order.common.exception.MessageKeys;
import com.crm.order.messaging.SaleCommandContracts;
import com.crm.order.messaging.SaleReplyContracts;
import com.crm.order.order.service.impl.OrderPersistence;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SALE process manager (ADR-018 §5). The one place that knows how far a sale got
 * and what to do next.
 *
 * <p><b>Every method here is {@code Propagation.MANDATORY}.</b> That is the design's
 * central invariant expressed as a container check rather than a convention: a saga
 * transition and the Outbox command it produces must commit together or not at all, so
 * this class may only ever run inside a caller's transaction — the Submit request's, or
 * the one {@code InboxGuard} opens around a reply. Calling it outside one is an
 * {@code IllegalTransactionStateException}, not a review comment.
 *
 * <p><b>No broker type appears anywhere below.</b> It takes an {@link EventEnvelope} and
 * a decoded, order-service-owned DTO; it emits by recording a row. It is constructible
 * and callable from a plain JUnit test with nothing running, which is what ADR-017 §4
 * asks of business code and what the architecture guard test enforces.
 *
 * <p><b>What makes it safe under at-least-once delivery.</b> Three independent
 * mechanisms, each covering what the others cannot:
 * <ol>
 *   <li>an exact duplicate never reaches this class — {@code InboxGuard}'s
 *       {@code (message_id, consumer_group)} constraint absorbs it;</li>
 *   <li>a DIFFERENT message that does not fit the saga's current state is counted and
 *       ignored ({@link #applyReply}'s state guard) — it cannot advance a sale twice
 *       and it cannot be silently dropped either, because the counter is a metric;</li>
 *   <li>two deliveries racing on ONE saga are settled by the {@code @Version} column:
 *       the loser's whole transaction — claim, saga mutation and outgoing command —
 *       rolls back and is redelivered.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SaleSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SaleSagaOrchestrator.class);

    /**
     * A message key is a catalog name, not free text. A producer that puts an exception
     * message in {@code failureMessageKey} gets it replaced by the generic key rather
     * than relayed: the status endpoint must never become a channel for infrastructure
     * detail (ADR-018 §8).
     */
    private static final Pattern SAFE_MESSAGE_KEY = Pattern.compile("MSG-[A-Z0-9-]{1,58}");

    private final SaleSagaRepository sagaRepository;
    private final OutboxRecorder outboxRecorder;
    private final OrderPersistence orderPersistence;
    private final SaleSagaCodec codec;
    private final SaleSagaMetrics metrics;
    private final SaleSagaProperties properties;
    private final Clock clock;

    // ------------------------------------------------------------------ starting

    /**
     * Creates the saga and records its FIRST command, in the caller's transaction —
     * which is the same transaction that moves the order WAIT → MIDLWARE (ADR-018 §6).
     *
     * <p>All three or none. A saga without its first command is a sale that never
     * starts; a command without a saga is a reply nothing can apply; a MIDLWARE order
     * without either is a sale the system has forgotten about while telling the user it
     * is processing.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SaleSaga start(String orderNumber, String accountNumber, SaleRequestSnapshot request) {
        Instant now = Instant.now(clock);
        SaleSaga saga = new SaleSaga();
        saga.setSagaId(orderNumber);
        saga.setOrderNumber(orderNumber);
        saga.setAccountNumber(accountNumber);
        saga.setRequestSnapshot(codec.write(request));
        saga.setCorrelationId(MDC.get(MdcKeys.CORRELATION_ID));
        saga.setCreatedAt(now);
        saga.transitionTo(SaleSagaState.AWAITING_ACCOUNT_CHECK, now, now.plus(properties.getReplyTimeout()));
        SaleSaga saved = sagaRepository.save(saga);
        issueCommandFor(saved, null);
        return saved;
    }

    // ------------------------------------------------------------------- replies

    /**
     * Apply a step outcome (ADR-018 §5). Runs inside {@code InboxGuard}'s transaction,
     * so the duplicate claim, this transition and the next command are one commit.
     *
     * <p>Returning without changing anything is a legitimate outcome and happens twice:
     * for a reply whose saga does not exist, and for a reply that does not fit the
     * current state. Both are counted. Neither throws — throwing would roll back the
     * Inbox claim and have the transport redeliver a message that will never fit,
     * blocking the partition behind it forever.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyReply(EventEnvelope envelope, SaleReplyContracts.SaleStepOutcome outcome) {
        String orderNumber = outcome.orderNumber();
        if (orderNumber == null || orderNumber.isBlank()) {
            metrics.unexpectedMessage(envelope.messageType(), null);
            log.warn("Reply {} ({}) carries no orderNumber; ignored", envelope.messageId(), envelope.messageType());
            return;
        }
        MDC.put(MdcKeys.ORDER_NUMBER, orderNumber);
        try {
            Optional<SaleSaga> found = sagaRepository.findById(orderNumber);
            if (found.isEmpty()) {
                // A reply for a sale this service has no saga for. Legitimately possible
                // after a database restore, and otherwise a producer defect — either way
                // there is nothing to advance and nothing to retry.
                metrics.unexpectedMessage(envelope.messageType(), null);
                log.warn("No saga for order {}; reply {} ({}) ignored", orderNumber,
                        envelope.messageId(), envelope.messageType());
                return;
            }
            SaleSaga saga = found.get();
            SaleSagaState expected = expectedStateFor(envelope.messageType());
            if (expected == null || saga.getCurrentState() != expected) {
                // Out of order, or a step answering for a phase the saga already left.
                // NOT silently ignored: the counter is what makes a persistent ordering
                // defect visible instead of looking like an idle system.
                metrics.unexpectedMessage(envelope.messageType(), saga.getCurrentState());
                log.warn("Ignoring {} for order {}: saga is {} but the message applies to {}",
                        envelope.messageType(), orderNumber, saga.getCurrentState(), expected);
                return;
            }
            saga.causedBy(envelope.messageId(), envelope.messageType());
            dispatch(saga, envelope, outcome);
        } finally {
            MDC.remove(MdcKeys.ORDER_NUMBER);
        }
    }

    private void dispatch(SaleSaga saga, EventEnvelope envelope, SaleReplyContracts.SaleStepOutcome outcome) {
        String causation = envelope.messageId();
        switch (envelope.messageType()) {
            case SaleReplyContracts.ACCOUNT_CHECKED -> {
                if (outcome.succeeded()) {
                    saga.setCustomerNumber(outcome.customerNumber());
                    advance(saga, SaleSagaState.AWAITING_PRODUCT_PREPARATION, causation);
                } else {
                    // Nothing exists downstream yet, so there is nothing to compensate:
                    // the sale simply did not start.
                    failTerminally(saga, outcome, "account-check");
                }
            }
            case SaleReplyContracts.PRODUCTS_PREPARED -> {
                if (outcome.succeeded()) {
                    attachProducts(saga, outcome);
                    advance(saga, SaleSagaState.AWAITING_INVOLVEMENT, causation);
                } else {
                    // product-service creates a whole installation in ONE transaction
                    // (ADR-015 §5.1), so a failed preparation persisted nothing. Issuing a
                    // compensation here would ask it to passivate rows that do not exist.
                    failTerminally(saga, outcome, "product-preparation");
                }
            }
            case SaleReplyContracts.PRODUCTS_LINKED -> {
                if (outcome.succeeded()) {
                    advance(saga, SaleSagaState.AWAITING_ACTIVATION, causation);
                } else {
                    // PNDG products exist and no account claims them: passivate them and
                    // stop. No involvement compensation — there is no involvement.
                    saga.fail("involvement", safeKey(outcome));
                    compensate(saga, SaleSagaState.COMPENSATING_PRODUCTS, causation);
                }
            }
            case SaleReplyContracts.PRODUCTS_ACTIVATED -> {
                if (outcome.succeeded()) {
                    complete(saga, causation);
                } else {
                    // The hard case: involvement rows exist. They go FIRST — passivating
                    // the products while an account still links to them would leave
                    // FR-PROD-01 composing over products that were withdrawn.
                    saga.fail("activation", safeKey(outcome));
                    compensate(saga, SaleSagaState.COMPENSATING_INVOLVEMENT, causation);
                }
            }
            case SaleReplyContracts.INVOLVEMENTS_COMPENSATED -> {
                if (outcome.succeeded()) {
                    compensate(saga, SaleSagaState.COMPENSATING_PRODUCTS, causation);
                } else {
                    escalate(saga, "involvement-compensation");
                }
            }
            case SaleReplyContracts.PRODUCTS_COMPENSATED -> {
                if (outcome.succeeded()) {
                    // Fully unwound: the order becomes CANCELLED and keeps its number
                    // forever (ADR-016 §4.6). The failure key recorded when the ORIGINAL
                    // step failed is preserved — the compensation succeeding is not what
                    // the client needs to be told about.
                    cancelOrder(saga, "saga compensated");
                    terminate(saga, SaleSagaState.FAILED);
                } else {
                    escalate(saga, "product-compensation");
                }
            }
            default -> metrics.unexpectedMessage(envelope.messageType(), saga.getCurrentState());
        }
    }

    // ------------------------------------------------------------------ recovery

    /**
     * Reissue the outstanding command of one saga that has waited too long (ADR-018 §8).
     * Its own transaction, one saga at a time: a batch in a single transaction would let
     * one optimistic-lock loss discard the progress of every other saga in the batch.
     *
     * <p>Safe to run against a saga that is in fact progressing: every command in this
     * flow is idempotent at the receiver (product creation through the sale-operation
     * coordinator, involvement per (account, product), activation and both compensations
     * by their own definition), so the worst case of a reissue racing a late reply is
     * work that is recognised and skipped downstream.
     */
    @Transactional
    public void reissueDueCommand(String sagaId) {
        SaleSaga saga = sagaRepository.findById(sagaId).orElse(null);
        if (saga == null || saga.getCurrentState().isTerminal() || saga.getNextRetryAt() == null) {
            return;
        }
        Instant now = Instant.now(clock);
        if (saga.getNextRetryAt().isAfter(now)) {
            return;   // another instance already moved it, or it is no longer due
        }
        if (saga.getRetryCount() >= properties.getMaxRetries()) {
            // The budget is spent. Escalating is the honest move: a seventh copy of a
            // command five copies failed to get answered is load, not recovery.
            log.error("Saga {} exhausted its retry budget in {}; escalating", sagaId, saga.getCurrentState());
            escalate(saga, "no-reply:" + saga.getCurrentState());
            return;
        }
        saga.setRetryCount(saga.getRetryCount() + 1);
        saga.setNextRetryAt(now.plus(backoff(saga.getRetryCount())));
        saga.setUpdatedAt(now);
        metrics.commandReissued(saga.getCurrentState());
        log.warn("Reissuing {} command for saga {} (attempt {})", saga.getCurrentState(), sagaId,
                saga.getRetryCount());
        issueCommandFor(saga, saga.getLastMessageId());
    }

    private Duration backoff(int attempt) {
        double factor = Math.pow(properties.getRetryBackoffMultiplier(), Math.max(0, attempt));
        Duration delay = Duration.ofMillis((long) (properties.getReplyTimeout().toMillis() * factor));
        return delay.compareTo(properties.getMaxRetryDelay()) > 0 ? properties.getMaxRetryDelay() : delay;
    }

    // ------------------------------------------------------------- state changes

    private void advance(SaleSaga saga, SaleSagaState next, String causationId) {
        Instant now = Instant.now(clock);
        saga.transitionTo(next, now, now.plus(properties.getReplyTimeout()));
        issueCommandFor(saga, causationId);
    }

    private void compensate(SaleSaga saga, SaleSagaState next, String causationId) {
        metrics.compensationAttempted();
        advance(saga, next, causationId);
    }

    /** No product or involvement exists yet: cancel the order and stop. */
    private void failTerminally(SaleSaga saga, SaleReplyContracts.SaleStepOutcome outcome, String step) {
        saga.fail(step, safeKey(outcome));
        cancelOrder(saga, "saga failed at " + step);
        terminate(saga, SaleSagaState.FAILED);
    }

    /**
     * Compensation itself failed, or the retry budget ran out.
     *
     * <p>The order is deliberately NOT cancelled here. CANCELLED asserts that the sale
     * was undone, and the whole point of this state is that it was not: something is
     * left behind that a person has to settle. Claiming otherwise would make the one
     * state an operator must find indistinguishable from a clean rollback.
     */
    private void escalate(SaleSaga saga, String failureCode) {
        metrics.compensationFailed();
        saga.fail(failureCode, saga.getFailureMessageKey() == null
                ? MessageKeys.SALE_FAILED : saga.getFailureMessageKey());
        terminate(saga, SaleSagaState.MANUAL_INTERVENTION);
        log.error("Saga {} needs manual intervention ({}); state left visible for operations",
                saga.getSagaId(), failureCode);
    }

    private void complete(SaleSaga saga, String causationId) {
        Instant now = Instant.now(clock);
        saga.transitionTo(SaleSagaState.COMPLETED, now, null);
        metrics.completed(saga.getCreatedAt());
        // The terminal fact is recorded ONLY here — after core consistency, in the same
        // transaction that records it. A consumer therefore cannot observe SaleCompleted
        // for a sale that is not complete (ADR-018 §7).
        outboxRecorder.record(
                SaleCommandContracts.SALE_COMPLETED_DESTINATION,
                com.crm.messaging.contract.MessageTypes.SALE_COMPLETED,
                SaleCommandContracts.AGGREGATE_TYPE,
                saga.getOrderNumber(),
                saga.getSagaId(),
                causationId,
                new SaleCommandContracts.SaleCompleted(
                        saga.getOrderNumber(),
                        saga.getAccountNumber(),
                        saga.getCustomerNumber(),
                        codec.readProductIds(saga.getProductIds()),
                        orderPersistence.totalAmountOf(saga.getOrderNumber())));
    }

    private void terminate(SaleSaga saga, SaleSagaState terminal) {
        saga.transitionTo(terminal, Instant.now(clock), null);
        metrics.failed(terminal, saga.getCreatedAt());
    }

    private void cancelOrder(SaleSaga saga, String reason) {
        // Deliberately NOT the swallow-and-log cancel the synchronous path uses: there,
        // a real exception was already propagating and had to reach the user. Here the
        // cancel is the work, and a failure must roll the whole transaction back so the
        // reply is redelivered rather than leaving a FAILED saga over a MIDLWARE order.
        orderPersistence.cancelByOrderNumber(saga.getOrderNumber(), reason);
    }

    private void attachProducts(SaleSaga saga, SaleReplyContracts.SaleStepOutcome outcome) {
        List<OrderPersistence.ProductSnapshot> snapshots = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (SaleReplyContracts.SaleStepOutcome.PreparedProduct product : outcome.products()) {
            snapshots.add(new OrderPersistence.ProductSnapshot(product.offerId(), product.productId(),
                    product.amount()));
            ids.add(product.productId());
        }
        saga.setProductIds(codec.writeProductIds(ids));
        orderPersistence.attachProducts(saga.getOrderNumber(), snapshots, outcome.totalAmount());
    }

    // ------------------------------------------------------------------ commands

    /**
     * Record the command the saga's CURRENT state is waiting for.
     *
     * <p>One method for both the first issue and every reissue, on purpose: a recovery
     * path that built its commands separately would be a second, less-exercised copy of
     * the mapping, and the first time the two disagreed would be during an incident.
     */
    private void issueCommandFor(SaleSaga saga, String causationId) {
        switch (saga.getCurrentState()) {
            case AWAITING_ACCOUNT_CHECK -> record(saga, causationId,
                    SaleCommandContracts.CHECK_ACCOUNT_DESTINATION,
                    com.crm.messaging.contract.MessageTypes.CHECK_SALE_ACCOUNT,
                    new SaleCommandContracts.CheckSaleAccount(saga.getOrderNumber(), saga.getAccountNumber()));
            case AWAITING_PRODUCT_PREPARATION -> record(saga, causationId,
                    SaleCommandContracts.PREPARE_PRODUCTS_DESTINATION,
                    com.crm.messaging.contract.MessageTypes.PREPARE_SALE_PRODUCTS,
                    prepareCommand(saga));
            case AWAITING_INVOLVEMENT -> record(saga, causationId,
                    SaleCommandContracts.LINK_PRODUCTS_DESTINATION,
                    com.crm.messaging.contract.MessageTypes.LINK_SALE_PRODUCTS,
                    new SaleCommandContracts.LinkSaleProducts(saga.getOrderNumber(), saga.getAccountNumber(),
                            codec.readProductIds(saga.getProductIds())));
            case AWAITING_ACTIVATION -> record(saga, causationId,
                    SaleCommandContracts.ACTIVATE_PRODUCTS_DESTINATION,
                    com.crm.messaging.contract.MessageTypes.ACTIVATE_SALE_PRODUCTS,
                    new SaleCommandContracts.ActivateSaleProducts(saga.getOrderNumber(),
                            codec.readProductIds(saga.getProductIds())));
            case COMPENSATING_INVOLVEMENT -> record(saga, causationId,
                    SaleCommandContracts.COMPENSATE_INVOLVEMENTS_DESTINATION,
                    com.crm.messaging.contract.MessageTypes.COMPENSATE_SALE_INVOLVEMENTS,
                    new SaleCommandContracts.CompensateSaleInvolvements(saga.getOrderNumber(),
                            saga.getAccountNumber()));
            case COMPENSATING_PRODUCTS -> record(saga, causationId,
                    SaleCommandContracts.COMPENSATE_PRODUCTS_DESTINATION,
                    com.crm.messaging.contract.MessageTypes.COMPENSATE_SALE_PRODUCTS,
                    new SaleCommandContracts.CompensateSaleProducts(saga.getOrderNumber(),
                            codec.readProductIds(saga.getProductIds())));
            default -> throw new IllegalStateException(
                    "No command is issued from state " + saga.getCurrentState() + " (saga " + saga.getSagaId() + ")");
        }
    }

    private SaleCommandContracts.PrepareSaleProducts prepareCommand(SaleSaga saga) {
        SaleRequestSnapshot snapshot = codec.read(saga.getRequestSnapshot());
        List<SaleCommandContracts.PrepareSaleProducts.Item> items = snapshot.items().stream()
                .map(item -> new SaleCommandContracts.PrepareSaleProducts.Item(item.offerId(),
                        item.characteristics()))
                .toList();
        return new SaleCommandContracts.PrepareSaleProducts(saga.getOrderNumber(), saga.getAccountNumber(),
                saga.getCustomerNumber(), snapshot.serviceAddressId(), snapshot.campaignId(), items);
    }

    private void record(SaleSaga saga, String causationId, String destination, String messageType, Object payload) {
        outboxRecorder.record(destination, messageType, SaleCommandContracts.AGGREGATE_TYPE,
                saga.getOrderNumber(), saga.getSagaId(), causationId, payload);
    }

    // ------------------------------------------------------------------- helpers

    /** Which state a given reply is an answer to. Anything else is out of order. */
    private static SaleSagaState expectedStateFor(String messageType) {
        return switch (messageType) {
            case SaleReplyContracts.ACCOUNT_CHECKED -> SaleSagaState.AWAITING_ACCOUNT_CHECK;
            case SaleReplyContracts.PRODUCTS_PREPARED -> SaleSagaState.AWAITING_PRODUCT_PREPARATION;
            case SaleReplyContracts.PRODUCTS_LINKED -> SaleSagaState.AWAITING_INVOLVEMENT;
            case SaleReplyContracts.PRODUCTS_ACTIVATED -> SaleSagaState.AWAITING_ACTIVATION;
            case SaleReplyContracts.INVOLVEMENTS_COMPENSATED -> SaleSagaState.COMPENSATING_INVOLVEMENT;
            case SaleReplyContracts.PRODUCTS_COMPENSATED -> SaleSagaState.COMPENSATING_PRODUCTS;
            default -> null;
        };
    }

    /**
     * The client-safe failure key. A producer's own MSG-* key is preserved — that is how
     * a rejected basket still reaches the user as MSG-SALE-* and a genuine outage as
     * MSG-SERVICE-UNAVAILABLE — while anything that is not a catalog-shaped key becomes
     * the generic one rather than being relayed.
     */
    private static String safeKey(SaleReplyContracts.SaleStepOutcome outcome) {
        String key = outcome.failureMessageKey();
        if (key != null && SAFE_MESSAGE_KEY.matcher(key).matches()) {
            return key;
        }
        return MessageKeys.SALE_FAILED;
    }

    /** Convenience for the status endpoint; a saga is never required to exist. */
    @Transactional(readOnly = true)
    public Optional<SaleSaga> find(String orderNumber) {
        return sagaRepository.findById(orderNumber);
    }
}
