package com.crm.order.order.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * The single order representation — create result AND detail (ADR-016 §3).
 *
 * <p><b>It carries exactly what order_db owns, and nothing else.</b> The Submit
 * screen also shows offer names, the campaign and the Service Address
 * (AC-SALE-01-12), but those are not order-domain facts: the frontend assembled the
 * basket and already holds them in session state. Echoing them back would mean
 * either storing catalog data this service does not own — a second copy that drifts
 * the moment a name changes — or re-fetching product-service on every order lookup
 * just to decorate a response nobody needs decorated. The one thing the client
 * genuinely cannot know is what this service just created: the order number, the
 * status, and which product each offer became.
 *
 * <p>Business numbers only: {@code orderNumber} (KR-12), {@code accountNumber}
 * (KR-11) and {@code customerNumber} are public identifiers. Internal ids
 * ({@code cust_ord.id}, {@code bsn_inter.id}, {@code cust_ord_item.id}) never leave
 * the service.
 *
 * <p>{@code orderStatus} is the catalog SHORT CODE ("MIDLWARE"), not a rendered
 * label: AC-SALE-01-15's user-facing text — "Sipariş Alındı, İşleniyor…" — is a
 * localization catalog entry (FR-LANG), and this backend returns language-neutral
 * keys everywhere else too.
 */
public record OrderResponse(
        String orderNumber,
        String orderStatus,
        String accountNumber,
        Long customerNumber,
        BigDecimal totalAmount,
        List<OrderItemResponse> items) {
}
