package com.crm.account.account.entity;

import com.crm.account.common.entity.StatusAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Local account-type catalog acct_tp (ADR-013 §2.5) — NOT a shared GNL catalog.
 * Rows are contract-fixed by the Flyway seed (1 = 223 Customer Account,
 * 2 = 224 Billing Account); IDs are assigned, never generated.
 */
@Entity
@Table(name = "acct_tp")
@Getter
@Setter
@NoArgsConstructor
public class AccountType extends StatusAwareEntity {

    @Id
    private Long id;

    @Column(name = "account_type_code", nullable = false, unique = true, length = 8)
    private String accountTypeCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;
}
