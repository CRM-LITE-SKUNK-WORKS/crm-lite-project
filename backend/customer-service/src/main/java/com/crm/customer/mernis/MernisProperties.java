package com.crm.customer.mernis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl mernis-stub base URL; the default is the Eureka service name resolved
 *                by the load-balanced RestClient. Override with a concrete
 *                http://host:port for environments without discovery.
 */
@ConfigurationProperties(prefix = "crm.mernis")
public record MernisProperties(String baseUrl) {

    public MernisProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://mernis-stub";
        }
    }
}
