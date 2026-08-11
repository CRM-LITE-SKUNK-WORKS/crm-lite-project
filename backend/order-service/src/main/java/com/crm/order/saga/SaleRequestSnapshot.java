package com.crm.order.saga;

import java.util.List;
import java.util.Map;

/**
 * The basket exactly as Submit accepted it, stored on the saga row (ADR-018 §4).
 *
 * <p>It exists because the saga must be able to reissue the product-preparation command
 * after a restart, and {@code cust_ord_item} cannot supply it: the order line records the
 * offer, the product it became and the amount, but not the characteristic VALUES the user
 * typed — those are product-service's data, forwarded verbatim (ADR-015 §6) and never
 * stored in order_db. Without this snapshot a crash between Submit and preparation would
 * leave a sale nothing could resume.
 *
 * <p>It is a snapshot, not a live reference: the sale is fulfilled from what was
 * submitted, not from whatever the catalog says later.
 */
public record SaleRequestSnapshot(
        Long serviceAddressId,
        String campaignId,
        List<Item> items) {

    public record Item(Long offerId, Map<Long, String> characteristics) {
    }
}
