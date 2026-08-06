package com.crm.product.customer;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Resilience4j boundary "customer-service-read" (docs/runbooks/resilience.md). */
@Component
public class HttpCustomerServiceClient implements CustomerServiceClient {

    private final RestClient restClient;

    public HttpCustomerServiceClient(@Qualifier("customerRestClient") RestClient customerRestClient) {
        this.restClient = customerRestClient;
    }

    @Override
    @CircuitBreaker(name = "customer-service-read")
    @Bulkhead(name = "customer-service-read")
    @Retry(name = "customer-service-read")
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

    @Override
    @CircuitBreaker(name = "customer-service-read")
    @Bulkhead(name = "customer-service-read")
    @Retry(name = "customer-service-read")
    public List<CustomerAddress> fetchAddresses(long customerNumber) {
        try {
            List<CustomerAddress> addresses = restClient.get()
                    .uri("/api/customers/{customerNumber}/addresses", customerNumber)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return addresses == null ? List.of() : addresses;
        } catch (HttpClientErrorException.NotFound e) {
            // Unknown customer: an empty list, so the address check below rejects
            // the sale with a validation error rather than a misleading 404.
            return List.of();
        } catch (RestClientException | IllegalStateException e) {
            throw new CustomerServiceUnavailableException("customer-service is unavailable", e);
        }
    }
}
