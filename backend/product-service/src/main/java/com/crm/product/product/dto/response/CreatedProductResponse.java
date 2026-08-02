package com.crm.product.product.dto.response;

import java.math.BigDecimal;

/**
 * One created product, echoed back so order-service can fill
 * {@code cust_ord_item.product_id} and snapshot the amount without a second catalog
 * round trip (ADR-016 §5.2).
 *
 * <p>{@code amount} is the offer's price <b>at creation time</b>. It is returned
 * precisely so the caller can freeze it: reading the catalog price back later would
 * silently rewrite the history of a past order whenever a price changes
 * (ADR-016 §2.4). The value itself is still a provisional fixture pending analyst
 * approval (document-delta P1/P5).
 */
public record CreatedProductResponse(
        Long offerId,
        String offerName,
        Long productId,
        boolean main,
        BigDecimal amount) {
}
