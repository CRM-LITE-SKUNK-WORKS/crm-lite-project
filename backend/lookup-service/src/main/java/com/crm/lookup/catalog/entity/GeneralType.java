package com.crm.lookup.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GNL_TP — the shared type catalog. IDs are contract data (seeded explicitly by
 * Flyway, never generated, never renumbered — see ADR-002).
 */
@Entity
@Table(name = "gnl_tp")
@Getter
@Setter
@NoArgsConstructor
public class GeneralType {

    @Id
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 20)
    private String shortCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type_domain", nullable = false, length = 30)
    private String typeDomain;

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
}
