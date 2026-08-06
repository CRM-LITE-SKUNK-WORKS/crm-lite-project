package com.crm.order.order.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * idempotency_key: one row per client-supplied {@code Idempotency-Key} on
 * {@code POST /api/orders}. Project addition (not a workbook table), exactly like
 * {@code order_number_seq} (V1).
 *
 * <p>The row is inserted FIRST, before any order-domain write ({@link IdempotencyPersistence}),
 * in its own short transaction — the UNIQUE constraint on {@code idempotencyKey} is
 * the final concurrency guard, not the {@link #status} field (which is read-after-write
 * and can never by itself rule out a race, ADR-016 idempotency addendum).
 */
@Entity
@Table(name = "idempotency_key")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKeyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "order_number", length = 10)
    private String orderNumber;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @Column(name = "updated_date")
    private Instant updatedDate;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
