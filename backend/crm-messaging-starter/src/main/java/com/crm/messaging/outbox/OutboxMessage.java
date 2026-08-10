package com.crm.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * One row per message a service has decided to send (ADR-017 §8.1).
 *
 * <p>The table lives in the SERVICE'S OWN database and is written by the SAME local
 * transaction as the business change that justifies the message. That single property is
 * the entire reason this table exists: "the order was written but the event was lost"
 * and "the event was published but the order rolled back" both become impossible,
 * without a distributed transaction and without a broker being available at write time.
 *
 * <p>The row is NOT deleted on publish. It is marked {@code published_at} and removed
 * later by retention ({@link OutboxRetentionJob}), which keeps two things that a
 * delete-on-publish design gives up: a short audit trail of what was actually sent, and
 * a meaningful backlog metric (a table that empties itself cannot tell you it is behind).
 *
 * <p>{@code id} is the envelope's messageId, not a surrogate key. The publisher, the
 * receiving Inbox and the operator grepping a log therefore all name the same message by
 * the same string.
 */
@Entity
@Table(name = "outbox_message")
@Getter
@Setter
public class OutboxMessage {

    @Id
    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "message_type", nullable = false, length = 128)
    private String messageType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    /** Debezium's Outbox Event Router routes on this column (ADR-017 §9). */
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    /** Logical destination per {@link com.crm.messaging.contract.Destinations} — never a raw topic. */
    @Column(name = "destination", nullable = false, length = 160)
    private String destination;

    /** {@code sagaId} when present, else {@code aggregateId} (ADR-017 §7.6). */
    @Column(name = "partition_key", nullable = false, length = 64)
    private String partitionKey;

    @Column(name = "saga_id", length = 64)
    private String sagaId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "causation_id", length = 64)
    private String causationId;

    /** The complete {@link com.crm.messaging.contract.EventEnvelope} as JSON. */
    @Column(name = "envelope", nullable = false, columnDefinition = "text")
    private String envelope;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** NULL means "not yet on the wire" — the backlog metric counts exactly these. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    public boolean isPublished() {
        return publishedAt != null;
    }
}
