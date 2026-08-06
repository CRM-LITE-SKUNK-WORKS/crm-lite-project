package com.crm.customer.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crm.customer.mernis.HttpMernisClient;
import com.crm.customer.mernis.MernisUnavailableException;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Requirement 14 ("timeout", "open circuit", "half-open recovery", "no retry on
 * unsafe writes", "safe read retry policy"): proves the Resilience4j mechanics
 * against the REAL {@link HttpMernisClient} production class and a real embedded
 * HTTP server, using resilience4j's programmatic API to apply the SAME policy
 * shape the {@code @CircuitBreaker}/{@code @Bulkhead}/{@code @Retry} annotations
 * declare on the Spring bean (docs/runbooks/resilience.md) — deliberately at this
 * level rather than through the full Spring context: this project's own
 * integration-test convention mocks downstream clients at the INTERFACE boundary
 * (OrderServiceIntegrationTest, ProductServiceIntegrationTest), which is exactly
 * what would need to be bypassed to observe a REAL timeout or a REAL circuit
 * transition, and Eureka/LoadBalancer resolution has no role in what is being
 * proven here.
 *
 * <p>Short, test-local timeout/circuit-breaker values are used throughout —
 * proving the SAME mechanism the production 2s/5s + 10s-open-state config uses,
 * just fast enough to run in a unit test.
 */
class MernisResilienceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/api/mernis/verify", handler);
        httpServer.start();
        this.server = httpServer;
        return httpServer;
    }

    private HttpMernisClient clientWithTimeout(int port, Duration connectTimeout, Duration readTimeout) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(connectTimeout))
                .setResponseTimeout(Timeout.of(readTimeout))
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();
        RestClient restClient = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .baseUrl("http://127.0.0.1:" + port)
                .build();
        return new HttpMernisClient(restClient);
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    // ---------------------------------------------------------------- timeout

    @Test
    void aSlowDownstreamTripsTheReadTimeoutAndFailsClosed() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        startServer(exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writeJson(exchange, 200, "{\"verified\":true}");
        });
        HttpMernisClient client = clientWithTimeout(server.getAddress().getPort(),
                Duration.ofMillis(200), Duration.ofMillis(300));

        assertThatThrownBy(() -> client.verify("12345678901", "Ali", "Yildiz", LocalDate.of(1990, 1, 1)))
                .isInstanceOf(MernisUnavailableException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    // ---------------------------------------------------------- open circuit / half-open

    @Test
    void repeatedFailuresOpenTheCircuitThenHalfOpenRecoversOnceTheDownstreamHeals() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger healthyFrom = new AtomicInteger(Integer.MAX_VALUE);
        startServer(exchange -> {
            int n = calls.incrementAndGet();
            if (n >= healthyFrom.get()) {
                writeJson(exchange, 200, "{\"verified\":true}");
            } else {
                writeJson(exchange, 503, "{}");
            }
        });
        HttpMernisClient client = clientWithTimeout(server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        CircuitBreaker circuitBreaker = CircuitBreaker.of("mernis-test", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(300))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .recordExceptions(MernisUnavailableException.class)
                .build());

        Supplier<Boolean> call = CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> client.verify("12345678901", "Ali", "Yildiz", LocalDate.of(1990, 1, 1)));

        // 4 failing calls (minimumNumberOfCalls) at 100% failure -> circuit OPENs.
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(call::get).isInstanceOf(MernisUnavailableException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // While OPEN, the call is refused WITHOUT reaching the server — the point of
        // the breaker, and why GlobalExceptionHandler maps CallNotPermittedException
        // to the same 503 contract rather than letting it 500.
        int callsBeforeOpenCheck = calls.get();
        assertThatThrownBy(call::get).isInstanceOf(CallNotPermittedException.class);
        assertThat(calls.get()).isEqualTo(callsBeforeOpenCheck);

        // Downstream heals; move to HALF_OPEN manually (deterministic, no timing race)
        // and prove a real request now reaches the server and succeeds, closing the
        // circuit — the "half-open recovery" requirement.
        healthyFrom.set(0);
        circuitBreaker.transitionToHalfOpenState();
        assertThat(call.get()).isTrue();
        assertThat(call.get()).isTrue();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ---------------------------------------------------------------- retry policy

    @Test
    void safeReadIsRetriedOnFailureAndEventuallySucceeds() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        startServer(exchange -> {
            if (calls.incrementAndGet() < 3) {
                writeJson(exchange, 503, "{}");
            } else {
                writeJson(exchange, 200, "{\"verified\":true}");
            }
        });
        HttpMernisClient client = clientWithTimeout(server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        Retry retry = Retry.of("mernis-test", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(MernisUnavailableException.class)
                .build());

        Supplier<Boolean> call = Retry.decorateSupplier(retry,
                () -> client.verify("12345678901", "Ali", "Yildiz", LocalDate.of(1990, 1, 1)));
        boolean result = call.get();

        assertThat(result).isTrue();
        // Failed twice, succeeded on the 3rd — proves the call actually reached the
        // server more than once, i.e. retry (not just a single lucky call) happened.
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void anUnwrappedWriteStyleCallIsNeverRetriedByTheTransportOrByResilience4j() throws IOException {
        // No @Retry equivalent applied here at all — the point of the test: absent a
        // Retry decorator (exactly HttpAccountServiceClient#passivateAccount's shape,
        // requirement 9/11), a single failure reaches the server exactly ONCE. This is
        // ALSO true because disableAutomaticRetries() is set on the transport
        // (docs/runbooks/resilience.md), so neither layer silently repeats an unsafe write.
        AtomicInteger calls = new AtomicInteger();
        startServer(exchange -> {
            calls.incrementAndGet();
            writeJson(exchange, 503, "{}");
        });
        HttpMernisClient client = clientWithTimeout(server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.verify("12345678901", "Ali", "Yildiz", LocalDate.of(1990, 1, 1)))
                .isInstanceOf(MernisUnavailableException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- bulkhead

    @Test
    void bulkheadRejectsCallsBeyondItsConcurrencyLimitWithoutReachingTheServer() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        startServer(exchange -> {
            calls.incrementAndGet();
            writeJson(exchange, 200, "{\"verified\":true}");
        });
        HttpMernisClient client = clientWithTimeout(server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        Bulkhead bulkhead = Bulkhead.of("mernis-test", BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ZERO)
                .build());

        // Acquire the single permit manually to simulate a call already in flight,
        // then prove a second concurrent call is refused rather than queued/retried.
        assertThat(bulkhead.tryAcquirePermission()).isTrue();
        try {
            Supplier<Boolean> call = Bulkhead.decorateSupplier(bulkhead,
                    () -> client.verify("12345678901", "Ali", "Yildiz", LocalDate.of(1990, 1, 1)));
            assertThatThrownBy(call::get).isInstanceOf(io.github.resilience4j.bulkhead.BulkheadFullException.class);
            assertThat(calls.get()).isZero();
        } finally {
            bulkhead.releasePermission();
        }
    }
}
