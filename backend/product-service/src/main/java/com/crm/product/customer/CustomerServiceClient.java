package com.crm.product.customer;

import java.util.List;
import java.util.Optional;

/**
 * Transport boundary to customer-service (ADR-010: direct via Eureka with the
 * user's token propagated, never through the gateway). Two distinct jobs:
 * resolving {@code prod.service_address_id} for the FR-PROD-02 detail modal, and
 * validating a submitted service address on the FR-SALE write path.
 *
 * Implementations throw {@link CustomerServiceUnavailableException} for any
 * transport/availability failure — both callers fail closed on that (503).
 * Mockable in tests; production code must never bypass this boundary.
 */
public interface CustomerServiceClient {

    /**
     * One address by id, for display. {@code Optional.empty()} for an unknown or
     * soft-deleted address — the detail modal then renders without a service
     * address rather than failing the whole read.
     */
    Optional<CustomerAddress> fetchAddress(long addressId);

    /**
     * The customer's ACTIVE address list — the authority for AC-SALE-01-11's
     * "the service address is one of the customer's addresses" (ADR-015 §5.9).
     *
     * <p>Deliberately customer-scoped rather than {@link #fetchAddress(long)}:
     * asking "does address 7 exist?" cannot answer "does address 7 belong to THIS
     * customer?", and only the second question stops a sale from attaching one
     * customer's address to another customer's product. Same endpoint and same
     * reasoning account-service uses for billing addresses (ADR-013 §2.4).
     */
    List<CustomerAddress> fetchAddresses(long customerNumber);
}
