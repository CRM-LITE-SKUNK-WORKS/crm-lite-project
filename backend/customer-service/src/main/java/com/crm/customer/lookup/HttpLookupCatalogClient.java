package com.crm.customer.lookup;

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
 * Resilience4j boundary "lookup-service" (docs/runbooks/resilience.md): pure GET
 * reads of the shared catalog, unaffected by the SALE-flow messaging migration —
 * every service that reads lookup-service applies the same instance name.
 *
 * <p>No fallback method: an open circuit ({@code CallNotPermittedException}) is
 * caught by {@code GlobalExceptionHandler} alongside {@link LookupCatalogUnavailableException}
 * and answers the SAME 503 {@code MSG-SERVICE-UNAVAILABLE} the ADR-002 fail-closed
 * rule already produces for a genuine connection failure — never a fabricated
 * "code is fine" default.
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
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/lookups/statuses/{shortCode}", shortCode)
                    .retrieve()
                    .body(LookupStatusResponse.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException | IllegalStateException e) {
            // Covers connection failures, 5xx responses and "no instances available"
            // from the load balancer: the catalog is unavailable, never "the code is fine".
            throw new LookupCatalogUnavailableException("Central lookup catalog is unavailable", e);
        }
    }

    @Override
    @CircuitBreaker(name = "lookup-service")
    @Bulkhead(name = "lookup-service")
    @Retry(name = "lookup-service")
    public Optional<LookupTypeResponse> fetchType(String shortCode) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/lookups/types/{shortCode}", shortCode)
                    .retrieve()
                    .body(LookupTypeResponse.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException | IllegalStateException e) {
            throw new LookupCatalogUnavailableException("Central lookup catalog is unavailable", e);
        }
    }
}
