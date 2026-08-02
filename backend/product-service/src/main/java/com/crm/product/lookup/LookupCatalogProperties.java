package com.crm.product.lookup;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl   lookup-service base URL. Default uses the Eureka service name and is
 *                  resolved by the load-balanced RestClient; override with a concrete
 *                  http://host:port for environments without discovery.
 * @param cacheTtl  how long a resolved catalog entry may be served from cache. Catalog
 *                  entries are contract-immutable, the TTL only bounds staleness for
 *                  newly added codes.
 * @param cacheMaxSize hard cap on cached entries (the catalog is inherently tiny).
 */
@ConfigurationProperties(prefix = "crm.lookup")
public record LookupCatalogProperties(String baseUrl, Duration cacheTtl, Integer cacheMaxSize) {

    public LookupCatalogProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://lookup-service";
        }
        if (cacheTtl == null) {
            cacheTtl = Duration.ofMinutes(15);
        }
        if (cacheMaxSize == null) {
            cacheMaxSize = 256;
        }
    }
}
