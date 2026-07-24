package com.crm.account.common.config;

import com.crm.account.customer.CustomerServiceProperties;
import com.crm.account.lookup.LookupCatalogProperties;
import com.crm.security.starter.BearerTokenPropagationInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Outbound HTTP clients for lookup-service (ADR-002) and customer-service (ADR-010
 * addendum / ADR-013). Service names in the base URLs are resolved through Eureka by
 * the load-balancer interceptor.
 *
 * Deliberately NOT exposed as a {@code @LoadBalanced RestClient.Builder} bean:
 * Spring Cloud's Eureka transport picks up any {@code RestClient.Builder} bean in the
 * context for its OWN registry calls, and a load-balanced builder makes Eureka try to
 * "load-balance" the literal host {@code localhost} — registration then fails with
 * "No instances available for localhost" (observed live in customer-service; see its
 * HttpClientConfig). Building the concrete RestClients here keeps the load balancing
 * scoped to exactly these integrations.
 */
@Configuration
@EnableConfigurationProperties({LookupCatalogProperties.class, CustomerServiceProperties.class})
public class HttpClientConfig {

    @Bean
    public RestClient lookupRestClient(LoadBalancerClient loadBalancerClient, LookupCatalogProperties properties,
                                       BearerTokenPropagationInterceptor bearerTokenPropagation) {
        // ADR-010: lookup-service is an INTERNAL zero-trust resource server — the
        // end-user's Keycloak token is propagated so the subject and audience reach it.
        return RestClient.builder()
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    public RestClient customerRestClient(LoadBalancerClient loadBalancerClient, CustomerServiceProperties properties,
                                         BearerTokenPropagationInterceptor bearerTokenPropagation) {
        // ADR-010 addendum: customer-service is likewise an internal zero-trust
        // resource server; the call is strictly request-bound, so the end-user's token
        // is propagated (sub and audience preserved end-to-end) — direct via Eureka,
        // never through the gateway (the gateway is the browser edge, ADR-007).
        return RestClient.builder()
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }
}
