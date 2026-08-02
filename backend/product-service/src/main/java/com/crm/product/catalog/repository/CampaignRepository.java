package com.crm.product.catalog.repository;

import com.crm.product.catalog.entity.Campaign;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /** Catalog read: ACTIVE campaigns only (local soft-delete invariant, ADR-002). */
    List<Campaign> findByStatusIdAndDeletedDateIsNullOrderByIdAsc(Long statusId);

    /**
     * Resolves the PUBLIC campaign code a sale claims (ADR-015 §6). Looked up by
     * code, never by internal id — `cmpg.id` never leaves this service, so a caller
     * could not send it even if it wanted to.
     */
    Optional<Campaign> findByCampaignCode(String campaignCode);
}
