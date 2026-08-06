package com.crm.product.product.entity;

import com.crm.product.catalog.entity.Campaign;
import com.crm.product.catalog.entity.ProductOffer;
import com.crm.product.catalog.entity.ProductSpec;
import com.crm.product.common.entity.StatusAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * prod: a product instance. The public product identifier IS the internal
 * {@code id} (no KR-11-style number rule exists for products). There is
 * deliberately NO customer/account column: the product ↔ billing-account link
 * lives only in account_db.cust_acct_prod_invl (ADR-013 §5).
 * {@code serviceAddressId} (FK-less external reference to a customer-service
 * address) is only filled on the MAIN product; children display their parent's
 * address (FR-PROD-02). {@code transactionId} is a workbook column the FR is
 * silent about (kept nullable, unused).
 */
@Entity
@Table(name = "prod")
@Getter
@Setter
@NoArgsConstructor
public class Product extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_prod_id")
    private Product parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_offer_id", nullable = false)
    private ProductOffer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_spec_id", nullable = false)
    private ProductSpec spec;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "service_start_date")
    private LocalDate serviceStartDate;

    @Column(name = "service_address_id")
    private Long serviceAddressId;

    /**
     * Project addition (ADR-015 idempotency addendum): the sale operation (order-service's
     * Idempotency-Key) that created this row. The ownership key
     * {@code POST /api/products/compensate} checks before passivating anything, so a
     * caller can never touch a product that belongs to a different sale operation.
     * NULL for anything created before this column existed (seed fixtures).
     */
    @Column(name = "sale_operation_id")
    private String saleOperationId;
}
