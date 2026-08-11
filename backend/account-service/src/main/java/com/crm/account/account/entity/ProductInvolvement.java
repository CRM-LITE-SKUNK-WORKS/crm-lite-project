package com.crm.account.account.entity;

import com.crm.account.common.entity.StatusAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * cust_acct_prod_invl (ADR-013 §5): the account-service-local projection of
 * account-product involvement — the sole source of truth for the AC-ACCT-04-03
 * delete guard. Active involvement ⇔ status_id = ACTV AND deleted_date IS NULL.
 * {@code productId} is an external reference to a product-service product (no FK by
 * design, and deliberately NOT verified against product-service — ADR-013 §8.5).
 *
 * <p>Population happens exclusively through this service's own command endpoint
 * (ADR-013 §8), invoked by order-service at the commit point of a sale — never by
 * another service writing {@code account_db}. {@code shortCode} is the workbook's
 * per-table constant {@code ACCT_PROD} ({@link com.crm.account.account.AccountContract
 * #INVOLVEMENT_SHORT_CODE}), not a campaign or offer code.
 */
@Entity
@Table(name = "cust_acct_prod_invl")
@Getter
@Setter
@NoArgsConstructor
public class ProductInvolvement extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_account_id", nullable = false)
    private Long customerAccountId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;

    /**
     * The SALE saga that created this row — the KR-12 order number (ADR-018 §7).
     *
     * <p>NULL for every row written by the V3 seed or by the synchronous ADR-016 §5
     * orchestration, and that is the safe default rather than a gap: the saga-scoped
     * compensation matches on this column exclusively, so a row with no owner can never
     * be passivated by any saga.
     *
     * <p>An ownership guard, not a feature. Nothing reads it except that compensation,
     * and no endpoint exposes it — there is still no user-facing way to unlink a product
     * from an account (ADR-013 §8.6).
     */
    @Column(name = "sale_operation_id", length = 64)
    private String saleOperationId;
}
