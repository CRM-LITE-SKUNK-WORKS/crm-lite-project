package com.crm.customer.address.entity;

import com.crm.customer.common.entity.StatusAwareEntity;
import com.crm.customer.customer.entity.Party;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * ADDR — multiple rows per party; at most one active primary (enforced by the
 * ux_addr_active_primary partial unique index in addition to service rules).
 */
@Entity
@Table(name = "addr")
@Getter
@Setter
@NoArgsConstructor
public class Address extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @ManyToOne
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @ManyToOne
    @JoinColumn(name = "district_id", nullable = false)
    private District district;

    @Column(name = "street", nullable = false, length = 200)
    private String street;

    @Column(name = "house_flat_no", nullable = false, length = 20)
    private String houseFlatNo;

    @Column(name = "address_description", nullable = false, length = 200)
    private String addressDescription;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;
}
