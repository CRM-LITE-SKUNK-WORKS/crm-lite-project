package com.crm.order.common.entity;

import com.crm.order.lookup.LookupContract;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Adds the stored external status reference (central GNL_ST ID — ADR-002; no local
 * or cross-database FK).
 *
 * <p>Note the order domain's asymmetry with the other services: a CUST_ORD row's
 * "healthy" status is MIDLWARE, not ACTV, so {@link #isActive()} is the right
 * question only for CUST_ORD_ITEM rows. The order's own lifecycle is asked through
 * its entity (ADR-016 §6) — a generic isActive() on an order would quietly answer
 * "no" for every perfectly valid order.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class StatusAwareEntity extends AuditableEntity {

    @Column(name = "status_id", nullable = false)
    private Long statusId;

    public boolean isActive() {
        return getDeletedDate() == null && Long.valueOf(LookupContract.STATUS_ACTIVE_ID).equals(statusId);
    }

    /**
     * Applies the full soft-delete invariant: PASV status reference + deleted/updated
     * audit metadata. The passive status id is passed in by the caller because it must
     * come from (and be validated by) the shared catalog contract, not be invented here.
     */
    public void passivate(long passiveStatusId, String by) {
        this.statusId = passiveStatusId;
        markDeleted(by);
    }
}
