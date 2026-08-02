package com.crm.product.product.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of POST /api/products: every created product, plus the campaign name the
 * caller needs for its order representation and the summed amount.
 *
 * <p>{@code totalAmount} is DERIVED here (sum of the items' price snapshots) rather
 * than left to the caller — the same "campaign price is derived, never stored" rule
 * the read side already follows, and it keeps one arithmetic definition of
 * AC-SALE-01-12's Total Amount in one place.
 */
public record ProductCreateResponse(
        String campaignId,
        String campaignName,
        Long mainProductId,
        BigDecimal totalAmount,
        List<CreatedProductResponse> products) {
}
