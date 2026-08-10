package com.crm.messaging.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * The single wire format for every CRM Lite command and event (ADR-017 §5).
 *
 * <p><b>This type is broker-agnostic on purpose.</b> There is no Kafka type, no
 * {@code ConsumerRecord}, no header map and no partition here. A handler that takes an
 * {@code EventEnvelope} can be constructed and called from a plain JUnit test with no
 * broker anywhere in sight — which is the whole point of the layering ADR-017 §4
 * describes, and what the architecture guard tests actually enforce.
 *
 * <p><b>Why the payload is a {@code String} and not a typed generic.</b> The payload
 * travels as JSON text and is decoded by the CONSUMER into a DTO the consumer owns
 * (ADR-017 §6). Putting a shared payload class here would recreate the shared domain
 * model this project deliberately does not have: every producer field addition would
 * become a lock-step release across three services. Keeping it as text means a
 * consumer that does not care about a new field simply never sees it.
 *
 * @param messageId      globally unique id of THIS message. Doubles as the eventId and is
 *                       the value the receiving Inbox uniqueness constraint keys on
 *                       (ADR-017 §8) — a redelivery carries the same messageId.
 * @param messageType    the contract name, e.g. {@code crm.sale.order-submitted}. Never
 *                       a Java class name: a class name is a refactoring hazard on the wire.
 * @param schemaVersion  contract version of {@code payload}. Consumers accept a range and
 *                       reject anything outside it loudly rather than guessing (§5.3).
 * @param aggregateType  the owning aggregate, e.g. {@code order} — also the Debezium
 *                       Outbox Event Router's routing field (§9).
 * @param aggregateId    the aggregate's business identity, e.g. the KR-12 order number.
 * @param sagaId         the sale-scoped orchestration id. For the SALE saga this is the
 *                       client's Idempotency-Key, so the saga id and the idempotency key
 *                       are the SAME value rather than two ids meaning the same thing.
 * @param correlationId  the end-to-end request id ({@code X-Correlation-Id}); already an
 *                       MDC field in every service today.
 * @param causationId    the messageId of the message that CAUSED this one; null for a
 *                       message originating from a synchronous request.
 * @param occurredAt     when the business fact happened — NOT when it was published.
 * @param payload        the versioned contract body as JSON text.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
        String messageId,
        String messageType,
        int schemaVersion,
        String aggregateType,
        String aggregateId,
        String sagaId,
        String correlationId,
        String causationId,
        Instant occurredAt,
        String payload) {

    public EventEnvelope {
        require(messageId, "messageId");
        require(messageType, "messageType");
        require(aggregateType, "aggregateType");
        require(aggregateId, "aggregateId");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1, was " + schemaVersion);
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    /**
     * The per-sale ordering key (ADR-017 §7.6). Ordering is only ever required WITHIN one
     * sale, never across sales, so the key is the sagaId when there is one and the
     * aggregateId (the order number, for the order aggregate) otherwise. Using a
     * broader key — say, the customer number — would serialize unrelated sales behind
     * each other for no ordering benefit.
     */
    public String partitionKey() {
        return sagaId != null && !sagaId.isBlank() ? sagaId : aggregateId;
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
