package com.crm.order.lookup;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Validation + caching layer over {@link LookupCatalogClient} (ADR-002), trimmed to
 * the order domain's needs: the GNL_ST ORDER-domain statuses (MIDLWARE, CANCELLED)
 * plus GENERAL ACTV for order items, and the GNL_TP BSN_INTER_TYPE row NEWSALE.
 *
 * Semantics:
 * - unknown code, soft-deleted code, or wrong domain -> {@link UnknownLookupCodeException} (400)
 * - catalog unreachable and value not cached -> {@link LookupCatalogUnavailableException} (503)
 * - contract codes whose central ID no longer matches {@link LookupContract} ->
 *   IllegalStateException (500, loud) — a renumbered central catalog must never be
 *   silently persisted against.
 *
 * The cache is a tiny bounded TTL map: entries are contract-immutable so cache hits
 * are always safe; the TTL only bounds staleness for newly added codes.
 *
 * <p>Reads are unaffected (ADR-002 §8): looking an order up by number filters on
 * nothing remote, so a catalog outage stops sales without stopping order lookups.
 */
@Service
public class LookupCatalogService {

    private static final Map<String, Long> STATUS_CONTRACT_IDS = Map.of(
            LookupContract.STATUS_ACTIVE, LookupContract.STATUS_ACTIVE_ID,
            LookupContract.STATUS_PASSIVE, LookupContract.STATUS_PASSIVE_ID,
            LookupContract.STATUS_WAITING, LookupContract.STATUS_WAITING_ID,
            LookupContract.STATUS_MIDDLEWARE, LookupContract.STATUS_MIDDLEWARE_ID,
            LookupContract.STATUS_CANCELLED, LookupContract.STATUS_CANCELLED_ID);

    private static final Map<String, Long> TYPE_CONTRACT_IDS = Map.of(
            LookupContract.TYPE_NEW_SALE, LookupContract.TYPE_NEW_SALE_ID);

    private record Cached<T>(T value, Instant expiresAt) {
    }

    private final LookupCatalogClient client;
    private final LookupCatalogProperties properties;
    private final Map<String, Cached<LookupStatusResponse>> statusCache = new ConcurrentHashMap<>();
    private final Map<String, Cached<LookupTypeResponse>> typeCache = new ConcurrentHashMap<>();

    public LookupCatalogService(LookupCatalogClient client, LookupCatalogProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /** Resolves a GNL_ST short code to its central ID, enforcing the expected domain. */
    public long resolveStatusId(String field, String shortCode, String expectedDomain) {
        Cached<LookupStatusResponse> cached = statusCache.get(shortCode);
        LookupStatusResponse status;
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            status = cached.value();
        } else {
            status = client.fetchStatus(shortCode)
                    .orElseThrow(() -> new UnknownLookupCodeException(field, "Unknown status code: " + shortCode));
            assertContract("GNL_ST", STATUS_CONTRACT_IDS, shortCode, status.id());
            put(statusCache, shortCode, status);
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
        Cached<LookupTypeResponse> cached = typeCache.get(shortCode);
        LookupTypeResponse type;
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            type = cached.value();
        } else {
            type = client.fetchType(shortCode)
                    .orElseThrow(() -> new UnknownLookupCodeException(field, "Unknown type code: " + shortCode));
            assertContract("GNL_TP", TYPE_CONTRACT_IDS, shortCode, type.id());
            put(typeCache, shortCode, type);
        }
        if (!expectedDomain.equals(type.typeDomain())) {
            throw new UnknownLookupCodeException(field,
                    "Type code " + shortCode + " belongs to domain " + type.typeDomain()
                            + ", expected " + expectedDomain);
        }
        return type.id();
    }

    private void assertContract(String catalog, Map<String, Long> contract, String shortCode, Long centralId) {
        Long expected = contract.get(shortCode);
        if (expected != null && !expected.equals(centralId)) {
            throw new IllegalStateException("Central catalog contract violation: " + catalog + "."
                    + shortCode + " has id " + centralId + " but the contract fixes it at " + expected
                    + ". Refusing to persist against a renumbered catalog.");
        }
    }

    private <T> void put(Map<String, Cached<T>> cache, String shortCode, T value) {
        // The catalog has a dozen rows; the cap is a safety net, not an LRU policy.
        if (cache.size() >= properties.cacheMaxSize()) {
            cache.clear();
        }
        cache.put(shortCode, new Cached<>(value, Instant.now().plus(properties.cacheTtl())));
    }
}
