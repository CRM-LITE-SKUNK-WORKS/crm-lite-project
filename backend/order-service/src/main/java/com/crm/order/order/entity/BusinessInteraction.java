package com.crm.order.order.entity;

import com.crm.order.common.entity.StatusAwareEntity;
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
 * bsn_inter: the business interaction a customer order belongs to — the sale "event"
 * itself (AC-SALE-01-01).
 *
 * <p>{@code customerAccountNumber} is the KR-11 billing-account number the sale was
 * started from, stored as the PUBLIC business number rather than the workbook's
 * internal {@code customer_account_id} (ADR-016 §2.3): internal ids never leave
 * account-service.
 *
 * <p>{@code businessInteractionTypeId} is populated with NEWSALE and nothing else
 * this phase — TRANSFER/CANCEL have no FR (ADR-016 §6/§8.2). The column exists so a
 * future flow needs no migration, not because a future flow is planned.
 */
@Entity
@Table(name = "bsn_inter")
@Getter
@Setter
@NoArgsConstructor
public class BusinessInteraction extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_account_number", nullable = false, length = 10)
    private String customerAccountNumber;

    @Column(name = "bsn_inter_type_id", nullable = false)
    private Long businessInteractionTypeId;

    @Column(name = "description", length = 250)
    private String description;
}
