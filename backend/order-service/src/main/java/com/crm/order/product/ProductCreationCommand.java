package com.crm.order.product;

import java.util.List;
import java.util.Map;

/**
 * The create-products request, assembled from the sale request without
 * interpretation. Characteristic values travel as raw strings keyed by
 * characteristic id — order-service does not know what a NUMBER or a DATE is, and
 * deliberately does not learn (ADR-015 §6).
 *
 * <p>{@code customerNumber} is not part of the client's sale request: it is read
 * from the billing account at step 0 and forwarded so product-service can verify
 * the service address belongs to this customer (ADR-015 §5.9). Taking it from the
 * account rather than from the client is the point — a caller cannot widen its own
 * reach by claiming a different customer.
 *
 * <p>{@code saleOperationId} is the client's {@code Idempotency-Key} (ADR-016
 * idempotency addendum), forwarded verbatim as the stable operation identifier
 * product-service dedups product creation against — see
 * {@link ProductServiceClient#createProducts}.
 */
public record ProductCreationCommand(
        Long customerNumber,
        Long serviceAddressId,
        String campaignId,
        List<Item> items,
        String saleOperationId) {

    public record Item(Long offerId, Map<Long, String> characteristics) {
    }
}
