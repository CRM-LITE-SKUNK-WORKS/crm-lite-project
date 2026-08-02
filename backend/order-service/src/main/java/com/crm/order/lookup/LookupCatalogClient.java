package com.crm.order.lookup;

import java.util.Optional;

/**
 * Transport boundary to the central catalog owner (lookup-service, ADR-002).
 * Implementations return {@code Optional.empty()} for unknown codes and throw
 * {@link LookupCatalogUnavailableException} for any transport/availability failure.
 * Mockable in tests; production code must never bypass this boundary with hardcoded
 * catalog values.
 *
 * <p>Unlike the account/product clients this one also reads <b>GNL_TP</b>: the order
 * domain persists {@code bsn_inter.bsn_inter_type_id} (NEWSALE), which is a catalog
 * value like any other and is therefore resolved rather than assumed (ADR-002 §5).
 */
public interface LookupCatalogClient {

    Optional<LookupStatusResponse> fetchStatus(String shortCode);

    Optional<LookupTypeResponse> fetchType(String shortCode);
}
