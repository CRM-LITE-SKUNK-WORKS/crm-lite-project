package com.crm.account.lookup;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Validation + caching layer over {@link LookupCatalogClient} (ADR-002), trimmed to
 * the account domain's needs (GNL_ST GENERAL statuses only).
 *
 * Semantics:
 * - unknown code, soft-deleted code, or wrong domain -> {@link UnknownLookupCodeException} (400)
 * - catalog unreachable and value not cached -> {@link LookupCatalogUnavailableException} (503)
 * - contract codes (ACTV, PASV) whose central ID no longer matches
 *   {@link LookupContract} -> IllegalStateException (500, loud) — a renumbered central
 *   catalog must never be silently persisted against.
 *
 * The cache is a tiny bounded TTL map: entries are contract-immutable so cache hits
 * are always safe; the TTL only bounds staleness for newly added codes.
 */
@Service
public class LookupCatalogService {

    private static final Map<String, Long> STATUS_CONTRACT_IDS = Map.of(
            LookupContract.STATUS_ACTIVE, LookupContract.STATUS_ACTIVE_ID,
            LookupContract.STATUS_PASSIVE, LookupContract.STATUS_PASSIVE_ID);

    private record CachedStatus(LookupStatusResponse value, Instant expiresAt) {
    }

    private final LookupCatalogClient client;
    private final LookupCatalogProperties properties;
    private final Map<String, CachedStatus> statusCache = new ConcurrentHashMap<>();

    public LookupCatalogService(LookupCatalogClient client, LookupCatalogProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /** Resolves a GNL_ST short code to its central ID, enforcing the expected domain. */
    public long resolveStatusId(String field, String shortCode, String expectedDomain) {
        CachedStatus cached = statusCache.get(shortCode);
        LookupStatusResponse status;
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            status = cached.value();
        } else {
            status = client.fetchStatus(shortCode)
                    .orElseThrow(() -> new UnknownLookupCodeException(field, "Unknown status code: " + shortCode));
            assertContract(shortCode, status.id());
            cacheStatus(shortCode, status);
        }
        if (!expectedDomain.equals(status.statusDomain())) {
            throw new UnknownLookupCodeException(field,
                    "Status code " + shortCode + " belongs to domain " + status.statusDomain()
                            + ", expected " + expectedDomain);
        }
        return status.id();
    }

    private void assertContract(String shortCode, Long centralId) {
        Long expected = STATUS_CONTRACT_IDS.get(shortCode);
        if (expected != null && !expected.equals(centralId)) {
            throw new IllegalStateException("Central catalog contract violation: GNL_ST."
                    + shortCode + " has id " + centralId + " but the contract fixes it at " + expected
                    + ". Refusing to persist against a renumbered catalog.");
        }
    }

    private void cacheStatus(String shortCode, LookupStatusResponse status) {
        // The catalog has a dozen rows; the cap is a safety net, not an LRU policy.
        if (statusCache.size() >= properties.cacheMaxSize()) {
            statusCache.clear();
        }
        statusCache.put(shortCode, new CachedStatus(status, Instant.now().plus(properties.cacheTtl())));
    }
}
