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
 * cmpg_prod_ofr: campaign membership. {@code main} marks the campaign's main offer
 * (the internet-service offer in the AC-SALE-01-09 sense).
 */
@Entity
@Table(name = "cmpg_prod_ofr")
@Getter
@Setter
@NoArgsConstructor
public class CampaignOffer extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_offer_id", nullable = false)
    private ProductOffer offer;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;
}
