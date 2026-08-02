package com.crm.order.lookup;

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
        return fetch("/api/lookups/statuses/{shortCode}", shortCode, LookupStatusResponse.class);
    }

    @Override
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
