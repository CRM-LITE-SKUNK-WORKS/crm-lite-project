package com.crm.customer.customer.entity;

import com.crm.customer.common.entity.StatusAwareEntity;
import com.crm.customer.contact.entity.ContactMedium;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "party")
@Getter
@Setter
@NoArgsConstructor
public class Party extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // External GNL_TP reference (INDV) — central catalog ID, validated through the
    // lookup API before persist; deliberately no FK (ADR-002).
    @Column(name = "party_type_id", nullable = false)
    private Long partyTypeId;

    // Inverse sides, single-valued only: let search queries navigate
    // Customer -> PartyRole -> Party -> Individual / ContactMedium without extra roots.
    @OneToOne(mappedBy = "party")
    private Individual individual;

    @OneToOne(mappedBy = "party")
    private ContactMedium contactMedium;
}
