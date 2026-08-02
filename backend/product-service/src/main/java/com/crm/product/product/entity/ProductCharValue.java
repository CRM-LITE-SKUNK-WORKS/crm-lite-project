package com.crm.product.product.entity;

import com.crm.product.catalog.entity.ProductSpecChar;
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
 * prod_char_val: the value a user entered for one characteristic of one product
 * instance (AC-SALE-01-10). The workbook stores every type in a single {@code val}
 * string column; the declared {@link ProductSpecChar#getDataType()} is what the
 * value was validated against on the way in (AC-SALE-01-19) — it is deliberately
 * NOT re-parsed on the way out.
 *
 * <p>Follows the product's lifecycle: created PNDG with the product and promoted or
 * passivated together with it (ADR-015 §5).
 */
@Entity
@Table(name = "prod_char_val")
@Getter
@Setter
@NoArgsConstructor
public class ProductCharValue extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_spec_char_id", nullable = false)
    private ProductSpecChar characteristic;

    @Column(name = "val", nullable = false, length = 250)
    private String val;
}
