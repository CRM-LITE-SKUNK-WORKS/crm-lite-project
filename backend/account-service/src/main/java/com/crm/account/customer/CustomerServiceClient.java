package com.crm.account.customer;

import java.util.List;
import java.util.Optional;

/**
 * Transport boundary to customer-service (ADR-010 addendum / ADR-013 §2.4).
 * Implementations return {@code Optional.empty()} for an unknown (or invisible,
 * i.e. soft-deleted) customer and throw {@link CustomerServiceUnavailableException}
 * for any transport/availability failure — writes fail closed on that (503).
 * Mockable in tests; production code must never bypass this boundary.
 */
public interface CustomerServiceClient {

    Optional<CustomerSummary> fetchCustomer(long customerNumber);

    /** The customer's ACTIVE addresses, exactly as its public address API lists them. */
    List<CustomerAddress> fetchAddresses(long customerNumber);
}
