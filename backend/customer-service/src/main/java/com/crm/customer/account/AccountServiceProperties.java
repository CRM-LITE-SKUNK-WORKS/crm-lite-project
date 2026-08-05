package com.crm.customer.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl account-service base URL, shared by both flows that call it:
 *                billing-account passivation on customer delete (AC-CUST-05-04) and
 *                the KR-02 Account Number search criterion. The default uses the
 *                Eureka service name and is resolved by the load-balanced RestClient;
 *                override with a concrete http://host:port for environments without
 *                discovery.
 */
@ConfigurationProperties(prefix = "crm.account")
public record AccountServiceProperties(String baseUrl) {

    public AccountServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://account-service";
        }
    }
}
