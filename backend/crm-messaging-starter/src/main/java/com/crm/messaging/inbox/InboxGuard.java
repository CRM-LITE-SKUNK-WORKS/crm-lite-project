package com.crm.messaging.inbox;

import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.contract.MessageTypes;
import com.crm.messaging.contract.SchemaVersionNotSupportedException;
import com.crm.observability.starter.MdcKeys;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claim-then-handle: the consumer side of exactly-once-in-effect (ADR-017 §8.2).
 *
 * <p>{@link #claimAndHandle} is ONE transaction containing both the duplicate claim and
 * the business change. Nothing else is required of the handler for the guarantee to hold:
 *
 * <ul>
 *   <li>first delivery — claim inserted, handler runs, both commit;</li>
 *   <li>redelivery — the INSERT violates {@code uq_inbox_message_id_group} and the whole
 *       attempt is abandoned as a duplicate, having changed nothing;</li>
 *   <li>handler throws — the claim rolls back with it, so the message is genuinely
 *       unprocessed and the next delivery is a first delivery, not a duplicate.</li>
 * </ul>
 *
 * <p>The last case is why the claim cannot be committed separately "to be safe": a
 * separately committed claim would swallow every message whose processing failed.
 *
 * <p>{@code flush()} is called explicitly after the claim insert. Without it JPA would
 * defer the INSERT to commit time, so the constraint violation would surface only after
 * the handler had already done its work — the duplicate would be detected, but only after
 * paying for it, and the resulting exception would be far from its cause.
 */
public class InboxGuard {

    private static final Logger log = LoggerFactory.getLogger(InboxGuard.class);

    private final InboxMessageRepository repository;
    private final EntityManager entityManager;
    private final InboxMetrics metrics;
    private final Clock clock;

    public InboxGuard(InboxMessageRepository repository, EntityManager entityManager,
                      InboxMetrics metrics, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Process {@code envelope} at most once for {@code consumerGroup}.
     *
     * @return {@code true} if the handler ran, {@code false} if this was a duplicate
     * @throws SchemaVersionNotSupportedException if this build cannot read the payload
     *         version — checked BEFORE the claim, so the message is not marked processed
     *         by a build that could not process it. The caller routes it to the DLQ.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean claimAndHandle(EventEnvelope envelope, String consumerGroup, MessageHandler handler) {
        if (!MessageTypes.isSupported(envelope.messageType(), envelope.schemaVersion())) {
            throw new SchemaVersionNotSupportedException(envelope.messageType(), envelope.schemaVersion());
        }

        // A cheap pre-check that avoids burning a database error (and a rolled-back
        // transaction) on the common redelivery case. It is NOT the guard: two concurrent
        // deliveries can both pass it, which is exactly what the UNIQUE constraint below
        // is there to settle.
        if (repository.existsByMessageIdAndConsumerGroup(envelope.messageId(), consumerGroup)) {
            metrics.duplicate(envelope.messageType());
            log.debug("Inbox duplicate ignored: {} for group {}", envelope.messageId(), consumerGroup);
            return false;
        }

        InboxMessage claim = new InboxMessage();
        claim.setMessageId(envelope.messageId());
        claim.setConsumerGroup(consumerGroup);
        claim.setMessageType(envelope.messageType());
        claim.setSchemaVersion(envelope.schemaVersion());
        claim.setSagaId(envelope.sagaId());
        claim.setCorrelationId(envelope.correlationId());
        claim.setProcessedAt(Instant.now(clock));

        try {
            repository.save(claim);
            entityManager.flush();
        } catch (DataIntegrityViolationException e) {
            // The FINAL duplicate guard fired: another delivery of this exact message won
            // the race. Nothing was applied by this attempt.
            metrics.duplicate(envelope.messageType());
            log.debug("Inbox duplicate rejected by uniqueness: {} for group {}",
                    envelope.messageId(), consumerGroup);
            return false;
        }

        // Every log line the handler writes now carries the saga and event ids that
        // crm-observability-starter reserved for exactly this flow (MdcKeys).
        String previousSaga = MDC.get(MdcKeys.SAGA_ID);
        String previousEvent = MDC.get(MdcKeys.EVENT_ID);
        try {
            if (envelope.sagaId() != null) {
                MDC.put(MdcKeys.SAGA_ID, envelope.sagaId());
            }
            MDC.put(MdcKeys.EVENT_ID, envelope.messageId());
            handler.handle(envelope);
        } finally {
            restore(MdcKeys.SAGA_ID, previousSaga);
            restore(MdcKeys.EVENT_ID, previousEvent);
        }
        return true;
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previous);
        }
    }
}
