package com.crm.messaging.outbox;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.contract.MessageTypes;
import com.crm.observability.starter.MdcKeys;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The application-facing way to send a message: record it in the Outbox (ADR-017 §8.1).
 *
 * <p><b>The whole class is one enforced invariant.</b> Every method is
 * {@code Propagation.MANDATORY}, so calling it outside a transaction is not a code-review
 * finding, it is an {@code IllegalTransactionStateException} at runtime and a failing
 * test at build time. There is no way to write an outbox row that is not part of the
 * caller's business transaction, which is what makes "the state changed but the message
 * was lost" unreachable rather than merely unlikely.
 *
 * <p>Note what is absent: no broker, no send, no flush, no network. This class can be
 * called from a plain unit test with nothing running. Publishing is a separate, later,
 * retryable concern handled by the relay (§9).
 */
public class OutboxRecorder {

    private final OutboxMessageRepository repository;
    private final EnvelopeCodec codec;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxRecorder(OutboxMessageRepository repository, EnvelopeCodec codec,
                          OutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.codec = codec;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Record a message in the caller's transaction.
     *
     * @param destination  a {@link com.crm.messaging.contract.Destinations} value
     * @param messageType  a {@link MessageTypes} constant; its CURRENT schema version is used
     * @param aggregateType/aggregateId  the owning aggregate
     * @param sagaId       the sale-scoped id (the SALE saga reuses the Idempotency-Key), or null
     * @param payload      any object serializable to the contract's JSON shape; the caller's own
     *                     producer-side DTO, never a type borrowed from a consumer
     * @return the recorded envelope, whose {@code messageId} the caller may log
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope record(String destination, String messageType, String aggregateType,
                                String aggregateId, String sagaId, Object payload) {
        return record(destination, messageType, aggregateType, aggregateId, sagaId, null, payload);
    }

    /** As above, plus an explicit {@code causationId} for a message caused by another message. */
    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope record(String destination, String messageType, String aggregateType,
                                String aggregateId, String sagaId, String causationId, Object payload) {
        if (!properties.isEnabled()) {
            // Recording is off (the pre-cutover default — ADR-017 §11). Returning the
            // envelope the caller WOULD have produced keeps every call site's shape
            // identical in both modes, so turning this on at cutover changes
            // configuration only, never code.
            return buildEnvelope(destination, messageType, aggregateType, aggregateId,
                    sagaId, causationId, payload).envelope();
        }

        Recorded recorded = buildEnvelope(destination, messageType, aggregateType, aggregateId,
                sagaId, causationId, payload);
        EventEnvelope envelope = recorded.envelope();

        OutboxMessage row = new OutboxMessage();
        row.setMessageId(envelope.messageId());
        row.setMessageType(envelope.messageType());
        row.setSchemaVersion(envelope.schemaVersion());
        row.setAggregateType(envelope.aggregateType());
        row.setAggregateId(envelope.aggregateId());
        row.setDestination(destination);
        row.setPartitionKey(envelope.partitionKey());
        row.setSagaId(envelope.sagaId());
        row.setCorrelationId(envelope.correlationId());
        row.setCausationId(envelope.causationId());
        row.setEnvelope(recorded.json());
        row.setOccurredAt(envelope.occurredAt());
        row.setPublishAttempts(0);
        repository.save(row);
        return envelope;
    }

    private Recorded buildEnvelope(String destination, String messageType, String aggregateType,
                                   String aggregateId, String sagaId, String causationId, Object payload) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination is required");
        }
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                messageType,
                MessageTypes.currentVersion(messageType),
                aggregateType,
                aggregateId,
                sagaId,
                // Reuse the request's existing correlation id rather than inventing a
                // second one: the log line about the HTTP request and the log line about
                // the message it produced are then the same search (crm-observability-starter).
                MDC.get(MdcKeys.CORRELATION_ID),
                causationId,
                Instant.now(clock),
                codec.encodePayload(payload));
        return new Recorded(envelope, codec.encodeToString(envelope));
    }

    private record Recorded(EventEnvelope envelope, String json) {
    }
}
