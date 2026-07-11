package com.crm.customer.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Common audit columns per the Final entity workbook. The *_by columns hold the
 * acting principal; until authentication exists this is the constant "system",
 * sized (VARCHAR 64) to later fit a Keycloak subject claim (ADR-004).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity {

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "updated_date")
    private Instant updatedDate;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "deleted_date")
    private Instant deletedDate;

    @Column(name = "deleted_by", length = 64)
    private String deletedBy;

    public void markCreated(String by) {
        this.createdDate = Instant.now();
        this.createdBy = by;
    }

    public void markUpdated(String by) {
        this.updatedDate = Instant.now();
        this.updatedBy = by;
    }

    /** Soft-delete metadata; also touches updated_* per the soft-delete invariant. */
    public void markDeleted(String by) {
        this.deletedDate = Instant.now();
        this.deletedBy = by;
        markUpdated(by);
    }
}
