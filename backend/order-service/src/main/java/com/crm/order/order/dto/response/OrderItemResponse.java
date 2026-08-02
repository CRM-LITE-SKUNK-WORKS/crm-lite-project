package com.crm.order.order.dto.response;

import java.math.BigDecimal;

/**
 * One line of the order: which offer was bought, which product instance it became,
 * and what it cost.
 *
 * <p>No {@code offerName}: it belongs to product-service's catalog, and the client
 * that submitted this offer already knows it (see {@link OrderResponse}).
 *
 * <p>{@code amount} is the stored SNAPSHOT, not a current catalog price — a past
 * order's total must not change when the price list does (ADR-016 §2.4). The value
 * itself is provisional pending analyst approval (document-delta P1/P5).
 */
public record OrderItemResponse(
        Long offerId,
        Long productId,
        BigDecimal amount) {
}
