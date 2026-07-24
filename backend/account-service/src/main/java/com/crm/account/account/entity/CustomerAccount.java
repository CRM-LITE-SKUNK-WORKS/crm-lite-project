package com.crm.account.account.entity;

import com.crm.account.common.entity.StatusAwareEntity;
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
 * cust_acct (ADR-013): a customer's account. The internal {@code id} NEVER leaves
 * this service; the public identifier is {@code accountNumber} (KR-11, immutable —
 * updatable=false at the JPA level on top of the application rules).
 * {@code customerNumber} is the public business customer number and
 * {@code addressId} a customer-service address id — both external references
 * without FKs by design.
 */
@Entity
@Table(name = "cust_acct")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAccount extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_number", nullable = false, updatable = false)
    private Long customerNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_type_id", nullable = false, updatable = false)
    private AccountType accountType;

    @Column(name = "account_number", nullable = false, unique = true, updatable = false, length = 10)
    private String accountNumber;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "address_id", nullable = false)
    private Long addressId;
}
