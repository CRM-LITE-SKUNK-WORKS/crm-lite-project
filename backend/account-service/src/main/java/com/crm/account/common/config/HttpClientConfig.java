package com.crm.account.common.config;

import com.crm.account.customer.CustomerServiceProperties;
import com.crm.account.lookup.LookupCatalogProperties;
import com.crm.observability.starter.CorrelationIdPropagationInterceptor;
import com.crm.security.starter.BearerTokenPropagationInterceptor;
import java.time.Duration;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
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
                                       BearerTokenPropagationInterceptor bearerTokenPropagation,
                                       CorrelationIdPropagationInterceptor correlationIdPropagation) {
        // ADR-010: lookup-service is an INTERNAL zero-trust resource server — the
        // end-user's Keycloak token is propagated so the subject and audience reach it.
        // Resilience4j boundary "lookup-service" (docs/runbooks/resilience.md).
        return RestClient.builder()
                .requestFactory(resilientRequestFactory())
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(correlationIdPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    public RestClient customerRestClient(LoadBalancerClient loadBalancerClient, CustomerServiceProperties properties,
                                         BearerTokenPropagationInterceptor bearerTokenPropagation,
                                         CorrelationIdPropagationInterceptor correlationIdPropagation) {
        // ADR-010 addendum: customer-service is likewise an internal zero-trust
        // resource server; the call is strictly request-bound, so the end-user's token
        // is propagated (sub and audience preserved end-to-end) — direct via Eureka,
        // never through the gateway (the gateway is the browser edge, ADR-007).
        // Resilience4j boundary "customer-service-read" (docs/runbooks/resilience.md):
        // both methods on HttpCustomerServiceClient are GETs.
        return RestClient.builder()
                .requestFactory(resilientRequestFactory())
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(correlationIdPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    /** Explicit connect + read timeout, transport-level retries disabled — Resilience4j
     *  owns the retry decision (docs/runbooks/resilience.md), same as every other
     *  domain service's outbound clients. */
    private ClientHttpRequestFactory resilientRequestFactory() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(Duration.ofSeconds(2)))
                .setResponseTimeout(Timeout.of(Duration.ofSeconds(5)))
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }
}
