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
 * prod_spec: the technical specification a product/offer derives from. The service
 * type ({@code serviceTypeId}) is an FK-less external reference to the central
 * GNL_TP SERVICE_TYPE catalog (ADR-002); offers/products have no service-type
 * column of their own — it is always derived through the spec.
 * {@code isDev} is a workbook column the FR is silent about (kept unmapped-meaning,
 * nullable).
 */
@Entity
@Table(name = "prod_spec")
@Getter
@Setter
@NoArgsConstructor
public class ProductSpec extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "service_type_id", nullable = false)
    private Long serviceTypeId;

    @Column(name = "is_dev")
    private Short isDev;
}
