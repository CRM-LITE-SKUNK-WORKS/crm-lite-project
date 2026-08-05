package com.crm.customer.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl account-service base URL (AC-CUST-05-04: billing-account passivation
 *                on customer delete). Default uses the Eureka service name and is
 *                resolved by the load-balanced RestClient; override with a concrete
 *                http://host:port for environments without discovery.
 */
@ConfigurationProperties(prefix = "crm.account")
public record AccountServiceProperties(String baseUrl) {

    public AccountServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://account-service";
        }
    }
}
