package com.crm.product.catalog.entity;

import com.crm.product.common.entity.StatusAwareEntity;
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
 * cmpg: a campaign bundling member offers (cmpg_prod_ofr). The PUBLIC identifier
 * is {@code campaignCode} (e.g. CMP-ADSL-01) — API responses never expose the
 * internal {@code id}. There is deliberately NO price column: the campaign price
 * is derived as the sum of its member offers' prices.
 */
@Entity
@Table(name = "cmpg")
@Getter
@Setter
@NoArgsConstructor
public class Campaign extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "campaign_code", nullable = false, unique = true, length = 20)
    private String campaignCode;

    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "activation_end_date")
    private Instant activationEndDate;
}
