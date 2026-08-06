package com.crm.customer.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.customer.account.AccountServiceProperties;
import com.crm.customer.lookup.LookupCatalogProperties;
import com.crm.customer.mernis.MernisProperties;
import com.crm.customer.order.OrderServiceProperties;
import com.crm.observability.starter.CorrelationIdPropagationInterceptor;
import com.crm.security.starter.BearerTokenPropagationInterceptor;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * ADR-010 wiring proof at the HTTP boundary: the RestClient built for
 * lookup-service PROPAGATES the current user's bearer token, while the RestClient
 * built for mernis-stub (external-system simulation) sends NO Authorization header.
 * Uses the JDK's built-in HttpServer as the recording sink and a passthrough
 * LoadBalancerClient in place of Eureka.
 */
class OutboundBearerPropagationTest {

    private static HttpServer server;
    private static int port;
    private static final Map<String, String> AUTH_HEADER_BY_PATH = new ConcurrentHashMap<>();

    @BeforeAll
    static void startSink() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            AUTH_HEADER_BY_PATH.put(exchange.getRequestURI().getPath(), auth == null ? "<absent>" : auth);
            byte[] body = "{\"verified\": true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopSink() {
        server.stop(0);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String tokenValue) {
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("subject-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_crm-user"))));
    }

    /** Resolves every Eureka service name to the local recording sink. */
    private static LoadBalancerClient passthrough() {
        return new LoadBalancerClient() {
            @Override
            public <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException {
                return execute(serviceId, choose(serviceId), request);
            }

            @Override
            public <T> T execute(String serviceId, ServiceInstance serviceInstance, LoadBalancerRequest<T> request)
                    throws IOException {
                try {
                    return request.apply(serviceInstance);
                } catch (IOException | RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public URI reconstructURI(ServiceInstance instance, URI original) {
                String query = original.getRawQuery() == null ? "" : "?" + original.getRawQuery();
                return URI.create("http://127.0.0.1:" + port + original.getRawPath() + query);
            }

            @Override
            public ServiceInstance choose(String serviceId) {
                return new DefaultServiceInstance(serviceId + "-1", serviceId, "127.0.0.1", port, false);
            }

            @Override
            public <T> ServiceInstance choose(String serviceId, Request<T> request) {
                return choose(serviceId);
            }
        };
    }

    @Test
    @DisplayName("lookup RestClient propagates the user's bearer token (ADR-010)")
    void lookupClientPropagatesBearer() {
        authenticate("user-access-token");
        HttpClientConfig config = new HttpClientConfig();
        RestClient lookup = config.lookupRestClient(passthrough(),
                new LookupCatalogProperties("http://lookup-service", null, null),
                new BearerTokenPropagationInterceptor(), new CorrelationIdPropagationInterceptor());

        lookup.get().uri("/api/lookups/statuses/ACTV").retrieve().toBodilessEntity();

        assertThat(AUTH_HEADER_BY_PATH).containsEntry("/api/lookups/statuses/ACTV", "Bearer user-access-token");
    }

    @Test
    @DisplayName("account/order RestClients propagate the user's bearer token for the KR-02 search (ADR-010)")
    void childRecordOwnerClientsPropagateBearer() {
        // Both owning services are INTERNAL zero-trust resource servers (ADR-009), so
        // the authorized service-to-service call carries the same operator identity —
        // no client credentials and no service account were introduced.
        authenticate("user-access-token");
        HttpClientConfig config = new HttpClientConfig();

        RestClient account = config.accountRestClient(passthrough(),
                new AccountServiceProperties("http://account-service"), new BearerTokenPropagationInterceptor(),
                new CorrelationIdPropagationInterceptor());
        account.get().uri("/api/accounts/1261000010").retrieve().toBodilessEntity();

        RestClient order = config.orderRestClient(passthrough(),
                new OrderServiceProperties("http://order-service"), new BearerTokenPropagationInterceptor(),
                new CorrelationIdPropagationInterceptor());
        order.get().uri("/api/orders/1262000018").retrieve().toBodilessEntity();

        assertThat(AUTH_HEADER_BY_PATH)
                .containsEntry("/api/accounts/1261000010", "Bearer user-access-token")
                .containsEntry("/api/orders/1262000018", "Bearer user-access-token");
    }

    @Test
    @DisplayName("mernis RestClient sends NO Authorization header even mid-request (ADR-010)")
    void mernisClientSendsNoBearer() {
        authenticate("user-access-token");
        HttpClientConfig config = new HttpClientConfig();
        RestClient mernis = config.mernisRestClient(passthrough(), new MernisProperties("http://mernis-stub"),
                new CorrelationIdPropagationInterceptor());

        mernis.post().uri("/api/mernis/verify").body("{}").retrieve().toBodilessEntity();

        assertThat(AUTH_HEADER_BY_PATH).containsEntry("/api/mernis/verify", "<absent>");
    }
}
