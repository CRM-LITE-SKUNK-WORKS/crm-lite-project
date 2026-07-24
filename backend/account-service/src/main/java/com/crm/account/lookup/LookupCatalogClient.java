package com.crm.account.lookup;

import java.util.Optional;

/**
 * Transport boundary to the central catalog owner (lookup-service, ADR-002).
 * Implementations return {@code Optional.empty()} for unknown codes and throw
 * {@link LookupCatalogUnavailableException} for any transport/availability failure.
 * Mockable in tests; production code must never bypass this boundary with hardcoded
 * catalog values.
 */
public interface LookupCatalogClient {

    Optional<LookupStatusResponse> fetchStatus(String shortCode);
}
