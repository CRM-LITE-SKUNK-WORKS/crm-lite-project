package com.crm.order.product;

import com.crm.order.common.exception.MessageKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpProductServiceClient implements ProductServiceClient {

    private final RestClient restClient;

    public HttpProductServiceClient(@Qualifier("productRestClient") RestClient productRestClient) {
        this.restClient = productRestClient;
    }

    @Override
    public ProductCreationResult createProducts(ProductCreationCommand command) {
        try {
            return restClient.post()
                    .uri("/api/products")
                    .body(toWireBody(command))
                    .retrieve()
                    .body(ProductCreationResult.class);
        } catch (HttpClientErrorException e) {
            // A 4xx is product-service's verdict on the basket, not a failure of the
            // call. Relay its own key so the frontend can render the specific §2.7
            // catalog text (ADR-016 §7).
            throw toValidationException(e);
        } catch (RestClientException | IllegalStateException e) {
            throw new ProductServiceUnavailableException("product-service is unavailable", e);
        }
    }

    @Override
    public void confirmProducts(List<Long> productIds) {
        post("/api/products/confirm", Map.of("productIds", productIds));
    }

    @Override
    public void compensateProducts(String saleOperationId, List<Long> productIds) {
        post("/api/products/compensate", Map.of("saleOperationId", saleOperationId, "productIds", productIds));
    }

    private void post(String uri, Map<String, ?> body) {
        try {
            restClient.post().uri(uri).body(body).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw toValidationException(e);
        } catch (RestClientException | IllegalStateException e) {
            throw new ProductServiceUnavailableException("product-service is unavailable", e);
        }
    }

    /**
     * Extracts the upstream's messageKey from the shared error-body shape. If it is
     * absent or unreadable the call still fails — with the generic validation key —
     * rather than being treated as a success: a 4xx we cannot parse is still a 4xx.
     */
    private ProductValidationException toValidationException(HttpClientErrorException e) {
        String messageKey = MessageKeys.VALIDATION_ERROR;
        String message = "product-service rejected the request";
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            if (body != null) {
                if (body.get("messageKey") instanceof String key) {
                    messageKey = key;
                }
                if (body.get("message") instanceof String text) {
                    message = text;
                }
            }
        } catch (RuntimeException ignored) {
            // Unparseable error body — keep the generic key set above.
        }
        HttpStatusCode statusCode = e.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return new ProductValidationException(status == null ? HttpStatus.BAD_REQUEST : status, messageKey, message);
    }

    /**
     * The wire shape product-service expects. Built explicitly rather than by
     * serializing the command record, because {@code characteristics} is a Map here
     * (order-service never interprets the values) but a LIST of
     * {characteristicId, value} objects on the wire.
     */
    private Map<String, Object> toWireBody(ProductCreationCommand command) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ProductCreationCommand.Item item : command.items()) {
            List<Map<String, Object>> characteristics = new ArrayList<>();
            item.characteristics().forEach((characteristicId, value) -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("characteristicId", characteristicId);
                entry.put("value", value);
                characteristics.add(entry);
            });
            Map<String, Object> wireItem = new LinkedHashMap<>();
            wireItem.put("offerId", item.offerId());
            wireItem.put("characteristics", characteristics);
            items.add(wireItem);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerNumber", command.customerNumber());
        body.put("serviceAddressId", command.serviceAddressId());
        body.put("campaignId", command.campaignId());
        body.put("items", items);
        body.put("saleOperationId", command.saleOperationId());
        return body;
    }
}
