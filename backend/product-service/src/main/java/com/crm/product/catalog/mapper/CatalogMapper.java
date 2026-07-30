package com.crm.product.catalog.mapper;

import com.crm.product.catalog.dto.CampaignOfferResponse;
import com.crm.product.catalog.dto.CampaignResponse;
import com.crm.product.catalog.dto.OfferResponse;
import com.crm.product.catalog.entity.Campaign;
import com.crm.product.catalog.entity.CampaignOffer;
import com.crm.product.catalog.entity.ProductOffer;
import com.crm.product.lookup.LookupContract;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CatalogMapper {

    /** Must run inside a transaction (the lazy spec association is read here). */
    public OfferResponse toOfferResponse(ProductOffer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getName(),
                LookupContract.serviceTypeCode(offer.getSpec().getServiceTypeId()),
                offer.getTotalPrice());
    }

    /**
     * The campaign's total price is DERIVED from its member offers (a missing
     * member price simply contributes nothing — prices are a fixture deviation
     * pending analyst approval, never fabricated per member).
     */
    public CampaignResponse toCampaignResponse(Campaign campaign, List<CampaignOffer> members) {
        List<CampaignOfferResponse> offers = members.stream()
                .map(member -> new CampaignOfferResponse(
                        member.getOffer().getId(),
                        member.getOffer().getName(),
                        LookupContract.serviceTypeCode(member.getOffer().getSpec().getServiceTypeId()),
                        member.getOffer().getTotalPrice(),
                        member.isMain()))
                .toList();
        BigDecimal totalPrice = members.stream()
                .map(member -> member.getOffer().getTotalPrice())
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CampaignResponse(
                campaign.getCampaignCode(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getActivationEndDate(),
                offers,
                totalPrice);
    }
}
