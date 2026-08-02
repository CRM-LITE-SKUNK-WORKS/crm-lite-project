package com.crm.product.catalog.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * prod_spec_char_use: which characteristics a given spec uses, and whether each is
 * mandatory. This is the join that turns "an offer" into "the fields the Product
 * Configuration screen must render" (AC-SALE-01-10/20) — the offer itself declares
 * no characteristics, they are reached through its spec.
 */
@Entity
@Table(name = "prod_spec_char_use")
@Getter
@Setter
@NoArgsConstructor
public class ProductSpecCharUse extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_spec_id", nullable = false)
    private ProductSpec spec;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_spec_char_id", nullable = false)
    private ProductSpecChar characteristic;

    /** AC-SALE-01-18: a mandatory field left blank is MSG-VAL-CHAR-REQUIRED. */
    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;
}
