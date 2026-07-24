package com.crm.account.customer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl customer-service base URL (ADR-010 addendum / ADR-013). Default uses
 *                the Eureka service name and is resolved by the load-balanced
 *                RestClient; override with a concrete http://host:port for
 *                environments without discovery.
 */
@ConfigurationProperties(prefix = "crm.customer")
public record CustomerServiceProperties(String baseUrl) {

    public CustomerServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://customer-service";
        }
    }
}
