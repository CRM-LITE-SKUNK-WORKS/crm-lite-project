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
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * prod_ofr: a sellable offer over one {@link ProductSpec}. {@code totalPrice} is a
 * seed fixture pending analyst approval (the workbook leaves it empty — recorded
 * deviation); campaign prices are DERIVED from member offers, never stored.
 */
@Entity
@Table(name = "prod_ofr")
@Getter
@Setter
@NoArgsConstructor
public class ProductOffer extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_spec_id", nullable = false)
    private ProductSpec spec;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "parent_offer_id")
    private Long parentOfferId;

    @Column(name = "product_offer_total_price", precision = 12, scale = 2)
    private BigDecimal totalPrice;
}
