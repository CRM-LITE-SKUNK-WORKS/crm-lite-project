package com.crm.product.catalog.repository;

import com.crm.product.catalog.entity.CampaignOffer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CampaignOfferRepository extends JpaRepository<CampaignOffer, Long> {

    /**
     * ACTIVE membership rows of one campaign, offer + spec fetch-joined (the
     * member's service type is derived through the spec, and the campaign's total
     * price through the members' offer prices).
     */
    @Query("""
            SELECT co FROM CampaignOffer co
            JOIN FETCH co.offer o
            JOIN FETCH o.spec
            WHERE co.campaign.id = :campaignId AND co.statusId = :statusId AND co.deletedDate IS NULL
            ORDER BY co.id
            """)
    List<CampaignOffer> findActiveMembers(Long campaignId, Long statusId);
}
