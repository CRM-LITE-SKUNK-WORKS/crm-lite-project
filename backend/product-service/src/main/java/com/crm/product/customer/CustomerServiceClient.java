package com.crm.product.customer;

import java.util.Optional;

/**
 * Transport boundary to customer-service (ADR-010: direct via Eureka with the
 * user's token propagated, never through the gateway). Used to resolve
 * {@code prod.service_address_id} — an FK-less external reference into
 * customer_db — for the FR-PROD-02 detail modal.
 *
 * Implementations return {@code Optional.empty()} for an unknown (or
 * soft-deleted) address and throw {@link CustomerServiceUnavailableException} for
 * any transport/availability failure — the detail fails closed on that (503).
 * Mockable in tests; production code must never bypass this boundary.
 */
public interface CustomerServiceClient {

    Optional<CustomerAddress> fetchAddress(long addressId);
}
