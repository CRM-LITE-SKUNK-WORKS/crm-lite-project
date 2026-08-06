package com.crm.order.lookup;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Resilience4j boundary "lookup-service" (docs/runbooks/resilience.md) — the ONE
 * order-service outbound client that gets it. account-service/product-service
 * (the SALE-write clients, ADR-016 §5) deliberately do NOT, per requirement 9.
 * The annotations sit on the two PUBLIC methods rather than the shared private
 * {@link #fetch} helper: Resilience4j's Spring AOP proxy only intercepts calls
 * that arrive from OUTSIDE this bean, so annotating the private delegate would
 * silently do nothing.
 */
@Component
public class HttpLookupCatalogClient implements LookupCatalogClient {

    private final RestClient restClient;

    public HttpLookupCatalogClient(@Qualifier("lookupRestClient") RestClient lookupRestClient) {
        this.restClient = lookupRestClient;
    }

    @Override
    @CircuitBreaker(name = "lookup-service")
    @Bulkhead(name = "lookup-service")
    @Retry(name = "lookup-service")
    public Optional<LookupStatusResponse> fetchStatus(String shortCode) {
        return fetch("/api/lookups/statuses/{shortCode}", shortCode, LookupStatusResponse.class);
    }

    @Override
    @CircuitBreaker(name = "lookup-service")
    @Bulkhead(name = "lookup-service")
    @Retry(name = "lookup-service")
    public Optional<LookupTypeResponse> fetchType(String shortCode) {
        return fetch("/api/lookups/types/{shortCode}", shortCode, LookupTypeResponse.class);
    }

    private <T> Optional<T> fetch(String uri, String shortCode, Class<T> type) {
        try {
            return Optional.ofNullable(restClient.get().uri(uri, shortCode).retrieve().body(type));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException | IllegalStateException e) {
            // Covers connection failures, 5xx responses and "no instances available"
            // from the load balancer: the catalog is unavailable, never "the code is fine".
            throw new LookupCatalogUnavailableException("Central lookup catalog is unavailable", e);
        }
    }
}
