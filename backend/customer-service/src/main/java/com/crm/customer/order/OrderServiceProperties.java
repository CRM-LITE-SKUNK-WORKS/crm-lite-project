package com.crm.customer.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl order-service base URL. The default is the Eureka service name
 *                resolved by the load-balanced RestClient; override with a concrete
 *                http://host:port for environments without discovery.
 */
@ConfigurationProperties(prefix = "crm.order")
public record OrderServiceProperties(String baseUrl) {

    public OrderServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://order-service";
        }
    }
}
