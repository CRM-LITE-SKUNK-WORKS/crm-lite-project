package com.crm.product.catalog.service;

import com.crm.product.catalog.dto.CampaignResponse;
import com.crm.product.catalog.dto.OfferResponse;
import com.crm.product.catalog.mapper.CatalogMapper;
import com.crm.product.catalog.repository.CampaignOfferRepository;
import com.crm.product.catalog.repository.CampaignRepository;
import com.crm.product.catalog.repository.ProductOfferRepository;
import com.crm.product.lookup.LookupContract;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only catalog views (offer selection support for the future §2.7 sale flow).
 * Fully local reads: active filtering uses the stored external GNL_ST contract
 * IDs (ADR-002) — no remote catalog call.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductOfferRepository offerRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignOfferRepository campaignOfferRepository;
    private final CatalogMapper mapper;

    public List<OfferResponse> listOffers() {
        return offerRepository.findActiveWithSpec(LookupContract.STATUS_ACTIVE_ID)
                .stream()
                .map(mapper::toOfferResponse)
                .toList();
    }

    public List<CampaignResponse> listCampaigns() {
        return campaignRepository.findByStatusIdAndDeletedDateIsNullOrderByIdAsc(LookupContract.STATUS_ACTIVE_ID)
                .stream()
                .map(campaign -> mapper.toCampaignResponse(campaign,
                        campaignOfferRepository.findActiveMembers(campaign.getId(), LookupContract.STATUS_ACTIVE_ID)))
                .toList();
    }
}
