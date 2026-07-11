package com.crm.customer.customer.entity;

import com.crm.customer.common.entity.StatusAwareEntity;
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

@Entity
@Table(name = "cust")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends StatusAwareEntity {

    /** Internal database key — never exposed through the public API. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Externally visible business identifier ("Customer ID" on screens; seeds start
     * at 1001). Assigned from the cust_customer_number_seq database sequence at
     * create time — concurrency-safe, unique, never reused.
     */
    @Column(name = "customer_number", nullable = false, unique = true)
    private Long customerNumber;

    @OneToOne
    @JoinColumn(name = "party_role_id", nullable = false, unique = true)
    private PartyRole partyRole;
}
