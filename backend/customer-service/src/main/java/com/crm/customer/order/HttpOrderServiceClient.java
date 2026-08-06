package com.crm.customer.order;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Resilience4j boundary "order-service-read" (docs/runbooks/resilience.md) —
 *  the KR-02 orderNumber search is a single GET, safe to retry. */
@Component
public class HttpOrderServiceClient implements OrderServiceClient {

    private final RestClient restClient;

    public HttpOrderServiceClient(@Qualifier("orderRestClient") RestClient orderRestClient) {
        this.restClient = orderRestClient;
    }

    @Override
    @CircuitBreaker(name = "order-service-read")
    @Bulkhead(name = "order-service-read")
    @Retry(name = "order-service-read")
    public Optional<OrderSummary> fetchOrder(String orderNumber) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/orders/{orderNumber}", orderNumber)
                    .retrieve()
                    .body(OrderSummary.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException | IllegalStateException e) {
            throw new OrderServiceUnavailableException("order-service is unavailable", e);
        }
    }
}
