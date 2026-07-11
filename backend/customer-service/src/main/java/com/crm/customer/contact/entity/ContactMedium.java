package com.crm.customer.contact.entity;

import com.crm.customer.common.entity.StatusAwareEntity;
import com.crm.customer.customer.entity.Party;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CNTC_MEDIUM — one contact row per party in this phase (DB UNIQUE on party_id). */
@Entity
@Table(name = "cntc_medium")
@Getter
@Setter
@NoArgsConstructor
public class ContactMedium extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "party_id", nullable = false, unique = true)
    private Party party;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "home_phone", length = 11)
    private String homePhone;

    @Column(name = "mobile_phone", nullable = false, length = 11)
    private String mobilePhone;

    @Column(name = "fax", length = 11)
    private String fax;
}
