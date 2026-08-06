package com.crm.product.product.idempotency;

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
 * sale_operation: the replay ledger for {@code POST /api/products} (ADR-015
 * idempotency addendum, 2026-08-06). Project addition, not a workbook table.
 *
 * <p>{@link #responseSnapshot} is NULL between {@link SaleOperationPersistence#reserve}
 * and {@link SaleOperationPersistence#complete} — that window is what
 * {@link SaleOperationCoordinator} reports as "in progress" to a concurrent caller.
 */
@Entity
@Table(name = "sale_operation")
@Getter
@Setter
@NoArgsConstructor
public class SaleOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_operation_id", nullable = false, unique = true, updatable = false, length = 64)
    private String saleOperationId;

    @Column(name = "response_snapshot")
    private String responseSnapshot;

    @Column(name = "main_product_id")
    private Long mainProductId;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @Column(name = "updated_date")
    private Instant updatedDate;
}
