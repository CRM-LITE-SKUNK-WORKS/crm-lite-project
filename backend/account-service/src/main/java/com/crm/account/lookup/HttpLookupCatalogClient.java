package com.crm.account.lookup;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Resilience4j boundary "lookup-service" (docs/runbooks/resilience.md). */
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
}
