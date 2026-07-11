package com.crm.customer.lookup;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Validation + caching layer over {@link LookupCatalogClient} (ADR-002).
 *
 * Semantics:
 * - unknown code, soft-deleted code, or wrong domain -> {@link UnknownLookupCodeException} (400)
 * - catalog unreachable and value not cached -> {@link LookupCatalogUnavailableException} (503)
 * - contract codes (ACTV, PASV, MALE, FEMALE, INDV) whose central ID no longer matches
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

    private static final Map<String, Long> TYPE_CONTRACT_IDS = Map.of(
            LookupContract.TYPE_MALE, LookupContract.TYPE_MALE_ID,
            LookupContract.TYPE_FEMALE, LookupContract.TYPE_FEMALE_ID,
            LookupContract.TYPE_INDIVIDUAL, LookupContract.TYPE_INDIVIDUAL_ID);

    private record CachedStatus(LookupStatusResponse value, Instant expiresAt) {
    }

    private record CachedType(LookupTypeResponse value, Instant expiresAt) {
    }

    private final LookupCatalogClient client;
    private final LookupCatalogProperties properties;
    private final Map<String, CachedStatus> statusCache = new ConcurrentHashMap<>();
    private final Map<String, CachedType> typeCache = new ConcurrentHashMap<>();

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
            assertContract(STATUS_CONTRACT_IDS, shortCode, status.id(), "GNL_ST");
            cacheStatus(shortCode, status);
        }
        if (!expectedDomain.equals(status.statusDomain())) {
            throw new UnknownLookupCodeException(field,
                    "Status code " + shortCode + " belongs to domain " + status.statusDomain()
                            + ", expected " + expectedDomain);
        }
        return status.id();
    }

    /** Resolves a GNL_TP short code to its central ID, enforcing the expected domain. */
    public long resolveTypeId(String field, String shortCode, String expectedDomain) {
        CachedType cached = typeCache.get(shortCode);
        LookupTypeResponse type;
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            type = cached.value();
        } else {
            type = client.fetchType(shortCode)
                    .orElseThrow(() -> new UnknownLookupCodeException(field, "Unknown type code: " + shortCode));
            assertContract(TYPE_CONTRACT_IDS, shortCode, type.id(), "GNL_TP");
            cacheType(shortCode, type);
        }
        if (!expectedDomain.equals(type.typeDomain())) {
            throw new UnknownLookupCodeException(field,
                    "Type code " + shortCode + " belongs to domain " + type.typeDomain()
                            + ", expected " + expectedDomain);
        }
        return type.id();
    }

    private void assertContract(Map<String, Long> contract, String shortCode, Long centralId, String catalog) {
        Long expected = contract.get(shortCode);
        if (expected != null && !expected.equals(centralId)) {
            throw new IllegalStateException("Central catalog contract violation: " + catalog + "."
                    + shortCode + " has id " + centralId + " but the contract fixes it at " + expected
                    + ". Refusing to persist against a renumbered catalog.");
        }
    }

    private void cacheStatus(String shortCode, LookupStatusResponse status) {
        evictIfFull(statusCache);
        statusCache.put(shortCode, new CachedStatus(status, Instant.now().plus(properties.cacheTtl())));
    }

    private void cacheType(String shortCode, LookupTypeResponse type) {
        evictIfFull(typeCache);
        typeCache.put(shortCode, new CachedType(type, Instant.now().plus(properties.cacheTtl())));
    }

    private void evictIfFull(Map<String, ?> cache) {
        // The catalog has a dozen rows; the cap is a safety net, not an LRU policy.
        if (cache.size() >= properties.cacheMaxSize()) {
            cache.clear();
        }
    }
}
