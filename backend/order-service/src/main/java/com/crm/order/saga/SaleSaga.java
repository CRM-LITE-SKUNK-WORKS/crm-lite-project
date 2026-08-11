package com.crm.order.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The persisted SALE process manager (ADR-018 §4). One row per sale, keyed by the
 * KR-12 order number.
 *
 * <p><b>Why this is not an {@code AuditableEntity}.</b> The audit superclass models a
 * business record's created/updated/deleted-by actor. A saga row has no actor after
 * Submit: every mutation after that is caused by a message, not by a user, and a
 * {@code created_by} of "system" repeated on every row would be audit theatre. What
 * matters operationally is which MESSAGE caused the last transition, which is what
 * {@link #lastMessageId} records.
 *
 * <p><b>{@code @Version} is the concurrency contract.</b> Two replies for the same saga
 * can be delivered to two consumer threads at once (a redelivered stale reply racing a
 * fresh one, most plausibly). Both read the row, both compute a transition, and without
 * a version both would write — advancing one sale twice. With it, the loser's whole
 * transaction rolls back, including its Inbox claim and its Outbox command, and the
 * message is redelivered to find a saga whose state has moved on.
 *
 * <p>{@code currentState} is stored as a STRING, never an ordinal: an ordinal makes the
 * meaning of every existing row depend on the declaration order of an enum, so inserting
 * a state in the middle silently rewrites history.
 */
@Entity
@Table(name = "sale_saga")
@Getter
@Setter
@NoArgsConstructor
public class SaleSaga {

    /** The sagaId — which IS the order number (ADR-018 §3). Assigned, never generated. */
    @Id
    @Column(name = "saga_id", length = 10, updatable = false)
    private String sagaId;

    @Column(name = "order_number", nullable = false, length = 10, updatable = false)
    private String orderNumber;

    @Column(name = "account_number", nullable = false, length = 10, updatable = false)
    private String accountNumber;

    /** Authoritative, from account-service's own reply — never from the browser. */
    @Column(name = "customer_number")
    private Long customerNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 40)
    private SaleSagaState currentState;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** The submitted basket as JSON — everything needed to reissue the preparation command. */
    @Column(name = "request_snapshot", nullable = false, columnDefinition = "text")
    private String requestSnapshot;

    /** The ids product-service reported, as a JSON array. Null until preparation succeeds. */
    @Column(name = "product_ids", columnDefinition = "text")
    private String productIds;

    @Column(name = "last_message_id", length = 64)
    private String lastMessageId;

    @Column(name = "last_message_type", length = 128)
    private String lastMessageType;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** When the outstanding command may be reissued. NULL in a terminal state. */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    /** Operator-facing: which step failed. Never returned to a client. */
    @Column(name = "failure_code", length = 64)
    private String failureCode;

    /** Client-facing and SAFE: a message key, never an exception message (ADR-018 §8). */
    @Column(name = "failure_message_key", length = 64)
    private String failureMessageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Move to {@code state} and clear the retry budget for the next step.
     *
     * <p>The retry counter is reset on every FORWARD move on purpose: the budget is
     * per-step ("this command has gone unanswered N times"), not per-sale. A sale that
     * needed one retry at step 2 must not arrive at step 4 with less budget than a sale
     * that did not.
     */
    public void transitionTo(SaleSagaState state, Instant now, Instant nextRetryAt) {
        this.currentState = state;
        this.retryCount = 0;
        this.nextRetryAt = state.isTerminal() ? null : nextRetryAt;
        this.updatedAt = now;
        if (state.isTerminal()) {
            this.completedAt = now;
        }
    }

    /** Records which message caused the current state — the causation trail (ADR-018 §9). */
    public void causedBy(String messageId, String messageType) {
        this.lastMessageId = messageId;
        this.lastMessageType = messageType;
    }

    public void fail(String failureCode, String failureMessageKey) {
        this.failureCode = failureCode;
        this.failureMessageKey = failureMessageKey;
    }
}
