package com.crm.product.catalog.entity;

import com.crm.product.common.entity.StatusAwareEntity;
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
 * prod_spec_char: the definition of a configurable characteristic ("Download Speed
 * (Mbps)", "Static IP", ...). {@code dataType} is one of NUMBER, BOOLEAN, TEXT, DATE
 * and is what AC-SALE-01-17/19 validate the submitted value against.
 *
 * <p>Created and seeded since Phase A but endpoint-less until the FR-SALE write
 * slice — the §2.7 Product Configuration screens are its first consumer
 * (ADR-015 §6).
 */
@Entity
@Table(name = "prod_spec_char")
@Getter
@Setter
@NoArgsConstructor
public class ProductSpecChar extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    @Column(name = "description", length = 250)
    private String description;
}
