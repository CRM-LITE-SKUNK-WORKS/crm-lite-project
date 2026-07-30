package com.crm.product.catalog.dto;

import java.math.BigDecimal;

/**
 * One catalog offer (GET /api/offers). {@code offerId} is prod_ofr.id — the public
 * offer identifier this domain exposes (FR-PROD-02 shows it as Product Offer ID);
 * {@code serviceType} is derived through the offer's spec (INTERNET / RESOURCE /
 * ACTIVATION — the offer has no service-type column of its own); {@code price} is
 * the seed fixture value pending analyst approval (recorded deviation).
 */
public record OfferResponse(
        Long offerId,
        String offerName,
        String serviceType,
        BigDecimal price) {
}
