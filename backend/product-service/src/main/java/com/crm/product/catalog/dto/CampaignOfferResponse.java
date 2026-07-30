package com.crm.product.catalog.dto;

import java.math.BigDecimal;

/**
 * One member offer inside a campaign (GET /api/campaigns). {@code main} marks the
 * campaign's main offer (the internet-service offer in the AC-SALE-01-09 sense).
 */
public record CampaignOfferResponse(
        Long offerId,
        String offerName,
        String serviceType,
        BigDecimal price,
        boolean main) {
}
