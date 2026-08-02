package com.crm.order.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * product-service's create response. The per-item {@code amount} is the offer price
 * AT CREATION TIME — order-service snapshots it into {@code cust_ord_item.amount}
 * rather than reading the catalog back later, so a future price change cannot
 * rewrite the history of a past order (ADR-016 §2.4).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductCreationResult(
        String campaignId,
        String campaignName,
        Long mainProductId,
        BigDecimal totalAmount,
        List<CreatedProduct> products) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatedProduct(
            Long offerId,
            String offerName,
            Long productId,
            boolean main,
            BigDecimal amount) {
    }

    public List<Long> productIds() {
        return products.stream().map(CreatedProduct::productId).toList();
    }
}
