package com.crm.messaging.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * One row per (message, consumer group) that this service has processed (ADR-017 §8.2).
 *
 * <p>The row is inserted in the SAME transaction as the business change the message
 * causes. That is what makes the pair exactly-once in effect on an at-least-once
 * transport: either both the claim and the business change commit, or neither does, so a
 * redelivery either finds the claim (and is a no-op) or finds nothing (and is genuinely
 * unprocessed). A "mark processed" written after the business commit would leave a window
 * where a crash makes the message look unprocessed and apply it twice.
 *
 * <p><b>The uniqueness constraint is the guard, not a lookup.</b> The claim is an INSERT
 * whose failure means duplicate; a SELECT-then-INSERT would leave a race between the two
 * statements that two concurrent consumers can and eventually do hit.
 *
 * <p>The key is (messageId, consumerGroup) rather than messageId alone: two different
 * services legitimately process the same message once each, while one service must never
 * process it twice.
 */
@Entity
@Table(name = "inbox_message")
@Getter
@Setter
public class InboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    /** {@code <consumer-service>.<destination>} — see {@code Destinations#consumerGroup}. */
    @Column(name = "consumer_group", nullable = false, length = 160)
    private String consumerGroup;

    @Column(name = "message_type", nullable = false, length = 128)
    private String messageType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "saga_id", length = 64)
    private String sagaId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
