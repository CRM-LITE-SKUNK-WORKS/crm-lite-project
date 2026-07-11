package com.crm.customer.customer.repository;

import com.crm.customer.customer.entity.Individual;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndividualRepository extends JpaRepository<Individual, Long> {

    // ADR-003: uniqueness is global and permanent — both checks scan ALL rows
    // (active, passive and soft-deleted), which is exactly what the derived
    // queries below do (no status/deleted filter on purpose).

    boolean existsByNationalityId(String nationalityId);

    boolean existsByNationalityIdAndIdNot(String nationalityId, Long id);
}
