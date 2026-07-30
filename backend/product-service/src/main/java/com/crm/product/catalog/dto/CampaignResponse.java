package com.crm.product.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One campaign with its member offers (GET /api/campaigns). {@code campaignId} is
 * the PUBLIC campaign code (cmpg.campaign_code, e.g. CMP-ADSL-01) — the internal
 * cmpg.id is never exposed. {@code totalPrice} is DERIVED (sum of the member
 * offers' prices); CMPG stores no price of its own (recorded deviation decision).
 */
public record CampaignResponse(
        String campaignId,
        String campaignName,
        String description,
        Instant activationEndDate,
        List<CampaignOfferResponse> offers,
        BigDecimal totalPrice) {
}
