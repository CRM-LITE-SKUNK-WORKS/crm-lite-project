package com.crm.order.product;

import java.util.List;
import java.util.Map;

/**
 * The create-products request, assembled from the sale request without
 * interpretation. Characteristic values travel as raw strings keyed by
 * characteristic id — order-service does not know what a NUMBER or a DATE is, and
 * deliberately does not learn (ADR-015 §6).
 */
public record ProductCreationCommand(
        Long serviceAddressId,
        String campaignId,
        List<Item> items) {

    public record Item(Long offerId, Map<Long, String> characteristics) {
    }
}
