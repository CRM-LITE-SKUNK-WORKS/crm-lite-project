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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ind")
@Getter
@Setter
@NoArgsConstructor
public class Individual extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "party_id", nullable = false, unique = true)
    private Party party;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "middle_name", length = 50)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "father_name", length = 50)
    private String fatherName;

    @Column(name = "mother_name", length = 50)
    private String motherName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    // External GNL_TP reference (MALE/FEMALE) — central catalog ID (ADR-002).
    @Column(name = "gender_id", nullable = false)
    private Long genderId;

    // ADR-003: globally and permanently unique (DB UNIQUE over all rows, including
    // soft-deleted ones). A passive customer never releases its Nationality ID.
    @Column(name = "nationality_id", nullable = false, length = 11, unique = true)
    private String nationalityId;
}
