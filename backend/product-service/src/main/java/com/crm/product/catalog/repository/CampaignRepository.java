package com.crm.product.catalog.repository;

import com.crm.product.catalog.entity.Campaign;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /** Catalog read: ACTIVE campaigns only (local soft-delete invariant, ADR-002). */
    List<Campaign> findByStatusIdAndDeletedDateIsNullOrderByIdAsc(Long statusId);
}
