package com.crm.lookup.catalog.repository;

import com.crm.lookup.catalog.entity.GeneralType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneralTypeRepository extends JpaRepository<GeneralType, Long> {

    Optional<GeneralType> findByShortCodeAndDeletedDateIsNull(String shortCode);

    List<GeneralType> findByDeletedDateIsNullOrderById();

    List<GeneralType> findByTypeDomainAndDeletedDateIsNullOrderById(String typeDomain);
}
