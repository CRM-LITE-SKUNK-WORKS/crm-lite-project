package com.crm.product.lookup;

import java.util.Optional;

/**
 * Transport boundary to the central catalog owner (lookup-service, ADR-002).
 * Implementations return {@code Optional.empty()} for unknown codes and throw
 * {@link LookupCatalogUnavailableException} for any transport/availability failure.
 * Mockable in tests; production code must never bypass this boundary with hardcoded
 * catalog values.
 *
 * <p>Introduced with the FR-SALE write slice (ADR-015 §4.1). Phase A had no client at
 * all because nothing was persisted; the moment a status is written, the full
 * client → service → cache boundary is mandatory.
 */
public interface LookupCatalogClient {

    Optional<LookupStatusResponse> fetchStatus(String shortCode);
}
