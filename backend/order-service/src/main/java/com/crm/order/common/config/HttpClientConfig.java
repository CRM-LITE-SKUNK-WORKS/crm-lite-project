package com.crm.order.common.config;

import com.crm.observability.starter.CorrelationIdPropagationInterceptor;
import com.crm.order.account.AccountServiceProperties;
import com.crm.order.lookup.LookupCatalogProperties;
import com.crm.order.product.ProductServiceProperties;
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
 * Outbound HTTP clients for the sale orchestration (ADR-016 §5): account-service
 * (precondition + involvement command), product-service (create/confirm/cancel) and
 * lookup-service (status/type resolution). Service names in the base URLs are
 * resolved through Eureka by the load-balancer interceptor.
 *
 * <p>ADR-010: all three are internal zero-trust resource servers and every call is
 * strictly request-bound, so the end-user's Keycloak token is propagated — the
 * subject and audience are preserved end-to-end. Direct via Eureka, never through
 * the gateway, which is the browser edge (ADR-007).
 *
 * <p>Deliberately NOT exposed as a {@code @LoadBalanced RestClient.Builder} bean:
 * Spring Cloud's Eureka transport picks up any {@code RestClient.Builder} bean in the
 * context for its OWN registry calls, and a load-balanced builder makes Eureka try to
 * "load-balance" the literal host {@code localhost} — registration then fails with
 * "No instances available for localhost" (observed live in customer-service).
 * Building the concrete RestClients here keeps load balancing scoped to exactly
 * these integrations.
 */
@Configuration
@EnableConfigurationProperties({AccountServiceProperties.class, ProductServiceProperties.class,
        LookupCatalogProperties.class})
public class HttpClientConfig {

    @Bean
    public RestClient accountRestClient(LoadBalancerClient loadBalancerClient, AccountServiceProperties properties,
                                        BearerTokenPropagationInterceptor bearerTokenPropagation,
                                        CorrelationIdPropagationInterceptor correlationIdPropagation) {
        // No Resilience4j here — requirement 9 (docs/runbooks/resilience.md): this is
        // exactly the order-service -> account-service SALE-write boundary scheduled
        // to move to messaging, so it stays untouched beyond correlation-id
        // propagation (an observability concern, not a resilience policy).
        return build(loadBalancerClient, properties.baseUrl(), bearerTokenPropagation, correlationIdPropagation);
    }

    @Bean
    public RestClient productRestClient(LoadBalancerClient loadBalancerClient, ProductServiceProperties properties,
                                        BearerTokenPropagationInterceptor bearerTokenPropagation,
                                        CorrelationIdPropagationInterceptor correlationIdPropagation) {
        // No Resilience4j here either — same reasoning as accountRestClient above;
        // POST /api/products is not idempotent (§5.3b) and stays exactly as it was.
        return build(loadBalancerClient, properties.baseUrl(), bearerTokenPropagation, correlationIdPropagation);
    }

    @Bean
    public RestClient lookupRestClient(LoadBalancerClient loadBalancerClient, LookupCatalogProperties properties,
                                       BearerTokenPropagationInterceptor bearerTokenPropagation,
                                       CorrelationIdPropagationInterceptor correlationIdPropagation) {
        // UNLIKE account/product above: lookup-service is a durable synchronous read
        // boundary that remains synchronous even after the SALE flow moves to
        // messaging (docs/runbooks/resilience.md) — it gets the full Resilience4j
        // boundary "lookup-service" (HttpLookupCatalogClient) and its own request
        // factory with explicit timeouts, kept deliberately separate from the
        // account/product beans' nonRetryingRequestFactory() so touching this one can
        // never accidentally touch those.
        return RestClient.builder()
                .requestFactory(resilientRequestFactory())
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(correlationIdPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(properties.baseUrl())
                .build();
    }

    private RestClient build(LoadBalancerClient loadBalancerClient, String baseUrl,
                             BearerTokenPropagationInterceptor bearerTokenPropagation,
                             CorrelationIdPropagationInterceptor correlationIdPropagation) {
        return RestClient.builder()
                .requestFactory(nonRetryingRequestFactory())
                .requestInterceptor(bearerTokenPropagation)
                .requestInterceptor(correlationIdPropagation)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .baseUrl(baseUrl)
                .build();
    }

    /** Explicit connect + read timeout for the lookup-service boundary only
     *  (docs/runbooks/resilience.md) — Resilience4j owns retry from here, so
     *  transport-level auto-retry is disabled exactly like {@link #nonRetryingRequestFactory()}. */
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

    /**
     * <b>Automatic retries are disabled deliberately, and this is load-bearing.</b>
     *
     * <p>httpclient5 is on the runtime classpath (transitively, via the Eureka
     * client), so Spring picks {@code HttpComponentsClientHttpRequestFactory} — whose
     * {@code DefaultHttpRequestRetryStrategy} silently <b>re-executes a request that
     * answered 503</b>. For a GET that is harmless. For
     * {@code POST /api/products} it is not: a retry would create a SECOND set of PNDG
     * products, and the orchestration would only ever learn about the second set's
     * ids — leaving the first set orphaned in product_db with no order referencing it
     * and no compensation able to find it.
     *
     * <p>This was observed, not theorised: an integration test proved one POST
     * produced two orders and two product-creation calls.
     *
     * <p>Retrying is the orchestration's decision to make, not the transport's.
     * ADR-016 §5.3 retries exactly one step — the idempotent confirm — and every
     * other failure is compensated instead. A transport that quietly retried
     * everything would make that design a fiction.
     */
    private ClientHttpRequestFactory nonRetryingRequestFactory() {
        CloseableHttpClient httpClient = HttpClients.custom()
                .disableAutomaticRetries()
                .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }
}
