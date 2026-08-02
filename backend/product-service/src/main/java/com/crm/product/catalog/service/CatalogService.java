package com.crm.product.catalog.service;

import com.crm.product.catalog.dto.CampaignResponse;
import com.crm.product.catalog.dto.CharacteristicResponse;
import com.crm.product.catalog.dto.OfferResponse;
import com.crm.product.catalog.mapper.CatalogMapper;
import com.crm.product.catalog.repository.CampaignOfferRepository;
import com.crm.product.catalog.repository.CampaignRepository;
import com.crm.product.catalog.repository.ProductOfferRepository;
import com.crm.product.catalog.repository.ProductSpecCharUseRepository;
import com.crm.product.common.exception.BusinessException;
import com.crm.product.common.exception.MessageKeys;
import com.crm.product.lookup.LookupContract;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final ProductSpecCharUseRepository specCharUseRepository;
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

    /**
     * AC-SALE-01-10/17/20: the configurable fields of one offer, for the Product
     * Configuration screen. Reached through the offer's SPEC — an offer declares no
     * characteristics of its own (the same "derive through the spec" rule the service
     * type follows).
     *
     * <p>An offer with no characteristics returns an empty list, which is
     * AC-SALE-01-21's case: the screen still renders a product block, just without
     * fields. An unknown offer id is 404 — the caller reached a configuration screen
     * for something that is not in the catalog.
     */
    public List<CharacteristicResponse> listOfferCharacteristics(long offerId) {
        if (!offerRepository.existsById(offerId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.PROD_NOT_FOUND,
                    "Offer " + offerId + " not found");
        }
        return specCharUseRepository.findActiveByOfferId(offerId, LookupContract.STATUS_ACTIVE_ID)
                .stream()
                .map(mapper::toCharacteristicResponse)
                .toList();
    }
}
