package com.crm.lookup.catalog.repository;

import com.crm.lookup.catalog.entity.GeneralStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneralStatusRepository extends JpaRepository<GeneralStatus, Long> {

    Optional<GeneralStatus> findByShortCodeAndDeletedDateIsNull(String shortCode);

    List<GeneralStatus> findByDeletedDateIsNullOrderById();

    List<GeneralStatus> findByStatusDomainAndDeletedDateIsNullOrderById(String statusDomain);
}
