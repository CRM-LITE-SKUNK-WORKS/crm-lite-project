package com.crm.customer.common.entity;

import com.crm.customer.lookup.LookupContract;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Adds the stored external status reference (central GNL_ST ID — ADR-002; no local
 * or cross-database FK). Active check is fully local:
 * {@code status_id = ACTV AND deleted_date IS NULL}.
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
