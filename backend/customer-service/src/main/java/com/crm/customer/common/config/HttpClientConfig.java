package com.crm.customer.common.config;

import com.crm.customer.account.AccountServiceProperties;
import com.crm.customer.lookup.LookupCatalogProperties;
import com.crm.customer.mernis.MernisProperties;
import com.crm.customer.order.OrderServiceProperties;
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
                                       BearerTokenPropagationInterceptor bearerTokenPropagation,
                                       CorrelationIdPropagationInterceptor correlationIdPropagation) {
        // ADR-010: lookup-service is an INTERNAL zero-trust resource server — the
        // end-user's Keycloak token is propagated so the subject and audience reach it.
        // Resilience4j boundary (docs/runbooks/resilience.md): HttpLookupCatalogClient's
        // reads carry @CircuitBreaker/@Bulkhead/@Retry(name="lookup-service").
        return RestClient.builder()
                .requestFactory(resilientRequestFactory())
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(correlationIdPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    public RestClient accountRestClient(LoadBalancerClient loadBalancerClient, AccountServiceProperties properties,
                                        BearerTokenPropagationInterceptor bearerTokenPropagation,
                                        CorrelationIdPropagationInterceptor correlationIdPropagation) {
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
        // decision, never the transport's (and now, for the two READ methods sharing
        // this bean, Resilience4j's decision — @Retry(name="account-service-read") is
        // deliberately NOT applied to passivateAccount, see HttpAccountServiceClient).
        return RestClient.builder()
                .requestFactory(resilientRequestFactory())
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(correlationIdPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    public RestClient orderRestClient(LoadBalancerClient loadBalancerClient, OrderServiceProperties properties,
                                      BearerTokenPropagationInterceptor bearerTokenPropagation,
                                      CorrelationIdPropagationInterceptor correlationIdPropagation) {
        // ADR-010, same reasoning as accountRestClient above. customer-service only
        // ever GETs a single order (KR-02 resolution) through this bean, which is
        // idempotent — safe for the Resilience4j retry policy
        // (name="order-service-read") HttpOrderServiceClient applies.
        return RestClient.builder()
                .requestFactory(resilientRequestFactory())
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(correlationIdPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    public RestClient mernisRestClient(LoadBalancerClient loadBalancerClient, MernisProperties properties,
                                       CorrelationIdPropagationInterceptor correlationIdPropagation) {
        // ADR-010: mernis-stub simulates an EXTERNAL KPS system. Deliberately NO
        // bearer propagation — a real KPS would never accept a CRM-realm JWT; user
        // attribution for verifications lives in CRM-side audit/log context instead.
        // Resilience4j boundary (docs/runbooks/resilience.md §Mernis): despite the POST
        // verb, HttpMernisClient#verify is a pure verification with no side effect, so
        // it is safe to retry — explicitly named in the requirement this addendum
        // implements ("Mernis/KPS verification").
        return RestClient.builder()
                .requestFactory(resilientRequestFactory())
                .requestInterceptor(correlationIdPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    /**
     * Explicit connect + read timeout (requirement order: timeout before
     * bulkhead/circuit-breaker/retry, docs/runbooks/resilience.md) and automatic
     * transport-level retries disabled — same reasoning as the pre-existing
     * {@code accountRestClient} note above, now applied uniformly: retrying is
     * Resilience4j's decision (selective, read-only, see each Http*Client), never
     * httpclient5's default strategy silently re-executing a call underneath it.
     */
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
