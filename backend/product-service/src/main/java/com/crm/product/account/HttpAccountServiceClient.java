package com.crm.product.account;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpAccountServiceClient implements AccountServiceClient {

    private final RestClient restClient;

    public HttpAccountServiceClient(@Qualifier("accountRestClient") RestClient accountRestClient) {
        this.restClient = accountRestClient;
    }

    @Override
    public Optional<List<Long>> fetchProductIds(String accountNumber) {
        try {
            List<Long> productIds = restClient.get()
                    .uri("/api/accounts/{accountNumber}/product-ids", accountNumber)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return Optional.of(productIds == null ? List.of() : productIds);
        } catch (HttpClientErrorException.NotFound e) {
            // Unknown number, or the K-8 223 Customer Account (indistinguishable by
            // design — ADR-013 §4.5).
            return Optional.empty();
        } catch (RestClientException | IllegalStateException e) {
            // Covers connection failures, 5xx responses and "no instances available"
            // from the load balancer: account-service is unavailable, never "the
            // account does not exist".
            throw new AccountServiceUnavailableException("account-service is unavailable", e);
        }
    }
}
