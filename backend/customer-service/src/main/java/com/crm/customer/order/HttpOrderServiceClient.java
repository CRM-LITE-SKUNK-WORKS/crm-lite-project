package com.crm.customer.order;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpOrderServiceClient implements OrderServiceClient {

    private final RestClient restClient;

    public HttpOrderServiceClient(@Qualifier("orderRestClient") RestClient orderRestClient) {
        this.restClient = orderRestClient;
    }

    @Override
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
