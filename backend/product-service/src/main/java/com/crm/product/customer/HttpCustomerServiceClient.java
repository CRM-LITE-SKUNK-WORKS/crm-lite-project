package com.crm.product.customer;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpCustomerServiceClient implements CustomerServiceClient {

    private final RestClient restClient;

    public HttpCustomerServiceClient(@Qualifier("customerRestClient") RestClient customerRestClient) {
        this.restClient = customerRestClient;
    }

    @Override
    public Optional<CustomerAddress> fetchAddress(long addressId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/addresses/{addressId}", addressId)
                    .retrieve()
                    .body(CustomerAddress.class));
        } catch (HttpClientErrorException.NotFound e) {
            // The address was soft-deleted (or never existed): the detail modal
            // shows no service address rather than failing the whole read.
            return Optional.empty();
        } catch (RestClientException | IllegalStateException e) {
            // Covers connection failures, 5xx responses and "no instances available"
            // from the load balancer: customer-service is unavailable, never "the
            // address does not exist".
            throw new CustomerServiceUnavailableException("customer-service is unavailable", e);
        }
    }
}
