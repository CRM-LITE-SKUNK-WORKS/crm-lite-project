package com.crm.product.common.config;

import com.crm.product.account.AccountServiceProperties;
import com.crm.product.customer.CustomerServiceProperties;
import com.crm.product.lookup.LookupCatalogProperties;
import com.crm.security.starter.BearerTokenPropagationInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Outbound HTTP clients for account-service (FR-PROD-01 composition, ADR-013 §5)
 * and customer-service (service-address resolution). Service names in the base
 * URLs are resolved through Eureka by the load-balancer interceptor.
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
@EnableConfigurationProperties({AccountServiceProperties.class, CustomerServiceProperties.class,
        LookupCatalogProperties.class})
public class HttpClientConfig {

    @Bean
    public RestClient lookupRestClient(LoadBalancerClient loadBalancerClient, LookupCatalogProperties properties,
                                       BearerTokenPropagationInterceptor bearerTokenPropagation) {
        // ADR-010: the catalog call is strictly request-bound and the user's crm-user
        // role legitimately covers catalog reads, so the end-user token is propagated
        // (same reasoning as customer-service → lookup-service). Added with the
        // FR-SALE write slice — Phase A resolved no status, so it needed no client.
        return RestClient.builder()
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    public RestClient accountRestClient(LoadBalancerClient loadBalancerClient, AccountServiceProperties properties,
                                        BearerTokenPropagationInterceptor bearerTokenPropagation) {
        // ADR-010: account-service is an INTERNAL zero-trust resource server — the
        // end-user's Keycloak token is propagated so the subject and audience reach
        // it. Direct via Eureka, never through the gateway (the gateway is the
        // browser edge, ADR-007).
        return RestClient.builder()
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    public RestClient customerRestClient(LoadBalancerClient loadBalancerClient, CustomerServiceProperties properties,
                                         BearerTokenPropagationInterceptor bearerTokenPropagation) {
        // ADR-010: customer-service is likewise an internal zero-trust resource
        // server; the call is strictly request-bound, so the end-user's token is
        // propagated (sub and audience preserved end-to-end).
        return RestClient.builder()
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }
}
