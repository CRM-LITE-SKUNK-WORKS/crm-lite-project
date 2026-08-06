package com.crm.account.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.account.customer.CustomerServiceProperties;
import com.crm.account.lookup.LookupCatalogProperties;
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
 * ADR-010 (addendum) wiring proof at the HTTP boundary: BOTH RestClients built by
 * account-service — lookup-service (ADR-002) and customer-service (ADR-013) —
 * PROPAGATE the current user's bearer token, preserving the subject end-to-end for
 * the downstream zero-trust validation. Uses the JDK's built-in HttpServer as the
 * recording sink and a passthrough LoadBalancerClient in place of Eureka (same
 * pattern as customer-service's OutboundBearerPropagationTest).
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
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
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
    @DisplayName("customer RestClient propagates the user's bearer token (ADR-010 addendum)")
    void customerClientPropagatesBearer() {
        authenticate("user-access-token");
        HttpClientConfig config = new HttpClientConfig();
        RestClient customer = config.customerRestClient(passthrough(),
                new CustomerServiceProperties("http://customer-service"),
                new BearerTokenPropagationInterceptor(), new CorrelationIdPropagationInterceptor());

        customer.get().uri("/api/customers/1001/addresses").retrieve().toBodilessEntity();

        assertThat(AUTH_HEADER_BY_PATH).containsEntry("/api/customers/1001/addresses", "Bearer user-access-token");
    }
}
