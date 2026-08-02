package com.crm.order.product;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl product-service base URL (ADR-015 §5 write slice). Default uses the
 *                Eureka service name and is resolved by the load-balanced RestClient;
 *                override with a concrete http://host:port for environments without
 *                discovery.
 */
@ConfigurationProperties(prefix = "crm.product")
public record ProductServiceProperties(String baseUrl) {

    public ProductServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://product-service";
        }
    }
}
