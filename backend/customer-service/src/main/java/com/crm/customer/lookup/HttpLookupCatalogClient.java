package com.crm.customer.lookup;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpLookupCatalogClient implements LookupCatalogClient {

    private final RestClient restClient;

    public HttpLookupCatalogClient(@Qualifier("lookupRestClient") RestClient lookupRestClient) {
        this.restClient = lookupRestClient;
    }

    @Override
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
