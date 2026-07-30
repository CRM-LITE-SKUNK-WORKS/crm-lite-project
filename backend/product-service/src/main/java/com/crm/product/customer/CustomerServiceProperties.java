package com.crm.product.customer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl customer-service base URL (service-address resolution, ADR-010).
 *                Default uses the Eureka service name and is resolved by the
 *                load-balanced RestClient; override with a concrete
 *                http://host:port for environments without discovery.
 */
@ConfigurationProperties(prefix = "crm.customer")
public record CustomerServiceProperties(String baseUrl) {

    public CustomerServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://customer-service";
        }
    }
}
