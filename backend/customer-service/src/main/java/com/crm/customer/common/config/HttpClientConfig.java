package com.crm.customer.common.config;

import com.crm.customer.account.AccountServiceProperties;
import com.crm.customer.lookup.LookupCatalogProperties;
import com.crm.customer.mernis.MernisProperties;
import com.crm.customer.order.OrderServiceProperties;
import com.crm.security.starter.BearerTokenPropagationInterceptor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Outbound HTTP clients for lookup-service (ADR-002), mernis-stub (KR-10),
 * account-service (AC-CUST-05-04 billing-account passivation on customer delete, and
 * the KR-02 Account Number search criterion) and order-service (the KR-02 Order Number
 * criterion, ADR-016 §3.2). Service names in the base URLs are resolved through Eureka
 * by the load-balancer interceptor.
 *
 * Deliberately NOT exposed as a {@code @LoadBalanced RestClient.Builder} bean:
 * Spring Cloud's Eureka transport picks up any {@code RestClient.Builder} bean in the
 * context for its OWN registry calls, and a load-balanced builder makes Eureka try to
 * "load-balance" the literal host {@code localhost} — registration then fails with
 * "No instances available for localhost" (observed live). Building the concrete
 * RestClients here keeps the load balancing scoped to exactly these integrations.
 *
 * <p><b>One bean per downstream service, shared by every flow that calls it.</b>
 * {@code accountRestClient} serves both the delete flow and the search: a second bean
 * for the same base URL would be a duplicate-bean failure at startup, and the
 * strictest transport setting has to win anyway (see its retry note below).
 */
@Configuration
@EnableConfigurationProperties({LookupCatalogProperties.class, MernisProperties.class,
        AccountServiceProperties.class, OrderServiceProperties.class})
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
    public RestClient accountRestClient(LoadBalancerClient loadBalancerClient, AccountServiceProperties properties,
                                        BearerTokenPropagationInterceptor bearerTokenPropagation) {
        // ADR-010: account-service is an INTERNAL zero-trust resource server reached
        // directly via Eureka (never through the gateway, ADR-007). The end-user's
        // Keycloak token is propagated, so the subject and audience survive the hop —
        // no client credentials and no service account are introduced for it.
        //
        // Automatic transport-level retries are disabled deliberately: DELETE
        // /api/accounts/{accountNumber} is NOT idempotent (a Passive account answers a
        // repeat call with 409, per ADR-013 §3.5), so a silent retry after a successful-
        // but-unacknowledged passivation would surface as a spurious conflict instead of
        // the success it actually was. Same rationale order-service already applies to
        // its own account/product clients (ADR-016 §5.3b) — retrying is the caller's
        // decision, never the transport's. The KR-02 search shares this bean and only
        // GETs through it, so the stricter setting costs it nothing.
        return RestClient.builder()
                .requestFactory(nonRetryingRequestFactory())
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    public RestClient orderRestClient(LoadBalancerClient loadBalancerClient, OrderServiceProperties properties,
                                      BearerTokenPropagationInterceptor bearerTokenPropagation) {
        // ADR-010, same reasoning as accountRestClient above. No non-retrying factory
        // here: customer-service only ever GETs a single order (KR-02 resolution), which
        // is idempotent — the same reason lookupRestClient above does without one. If a
        // write to order-service is ever added, this bean must gain the factory too.
        return RestClient.builder()
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    public RestClient mernisRestClient(LoadBalancerClient loadBalancerClient, MernisProperties properties) {
        // ADR-010: mernis-stub simulates an EXTERNAL KPS system. Deliberately NO
        // bearer propagation — a real KPS would never accept a CRM-realm JWT; user
        // attribution for verifications lives in CRM-side audit/log context instead.
        return RestClient.builder()
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    private ClientHttpRequestFactory nonRetryingRequestFactory() {
        CloseableHttpClient httpClient = HttpClients.custom()
                .disableAutomaticRetries()
                .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }
}
