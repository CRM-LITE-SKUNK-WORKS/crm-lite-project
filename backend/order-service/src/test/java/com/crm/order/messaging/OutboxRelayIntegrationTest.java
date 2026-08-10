package com.crm.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.outbox.OutboxMessage;
import com.crm.messaging.outbox.OutboxMessageRepository;
import com.crm.messaging.outbox.OutboxMetrics;
import com.crm.messaging.outbox.OutboxProperties;
import com.crm.messaging.outbox.OutboxPublisher;
import com.crm.messaging.outbox.OutboxRelay;
import com.crm.messaging.outbox.OutboxRetentionJob;
import com.crm.order.account.AccountServiceClient;
import com.crm.order.account.AccountSummary;
import com.crm.order.lookup.LookupCatalogClient;
import com.crm.order.lookup.LookupStatusResponse;
import com.crm.order.lookup.LookupTypeResponse;
import com.crm.order.order.dto.request.OrderCreateRequest;
import com.crm.order.order.dto.request.OrderItemRequest;
import com.crm.order.order.service.impl.OrderPersistence;
import com.crm.order.product.ProductServiceClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * What happens when the broker is down (ADR-017 §9), and what retention does afterwards
 * (§10).
 *
 * <p>The broker is simulated by an {@link OutboxPublisher} that can be switched between
 * failing and succeeding. That is not a shortcut — it is why the port exists with no
 * broker types in it (§4). "Kafka was unavailable for thirty seconds and then came back"
 * is not a state a real cluster can be asked to produce on demand, and a test that cannot
 * produce it cannot prove the outbox survives it.
 *
 * <p>Requires a running Docker daemon. Rerun with:
 *   {@code mvn -pl backend/order-service test -Dtest=OutboxRelayIntegrationTest}
 */
@Testcontainers
@SpringBootTest(properties = {
        "crm.messaging.outbox.enabled=true",
        // The scheduler stays off; every drain below is invoked explicitly. A background
        // poll would make "how many rows are still unpublished" a question of timing.
        "crm.messaging.outbox.relay.enabled=false",
        "crm.messaging.outbox.retention.enabled=true",
        "crm.messaging.outbox.retention.keep-published-for=0s"
})
@Import(OutboxRelayIntegrationTest.ControllablePublisherConfiguration.class)
class OutboxRelayIntegrationTest {

    /**
     * A publisher whose availability the test controls, standing in for the broker.
     * Records what it accepted so the test can assert on content, not only on counts.
     */
    static class ControllablePublisher implements OutboxPublisher {
        volatile boolean available = true;
        final List<EventEnvelope> accepted = new ArrayList<>();

        @Override
        public synchronized void publish(String destination, EventEnvelope envelope) {
            if (!available) {
                throw new IllegalStateException("simulated broker outage");
            }
            accepted.add(envelope);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControllablePublisherConfiguration {
        @Bean
        @Primary
        ControllablePublisher controllablePublisher() {
            return new ControllablePublisher();
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    static final AccountSummary ACCOUNT = new AccountSummary("1261000010", 1001L, "Active");

    @MockitoBean
    LookupCatalogClient lookupCatalogClient;
    @MockitoBean
    AccountServiceClient accountServiceClient;
    @MockitoBean
    ProductServiceClient productServiceClient;

    @Autowired
    ControllablePublisher publisher;
    @Autowired
    OutboxRelay relay;
    @Autowired
    OutboxRetentionJob retentionJob;
    @Autowired
    OutboxMessageRepository repository;
    @Autowired
    OrderPersistence persistence;
    @Autowired
    MeterRegistry meterRegistry;
    @Autowired
    OutboxProperties properties;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM outbox_message");
        publisher.available = true;
        publisher.accepted.clear();
        Mockito.when(lookupCatalogClient.fetchStatus("ACTV"))
                .thenReturn(Optional.of(new LookupStatusResponse(1L, "ACTV", "Active", "GENERAL")));
        Mockito.when(lookupCatalogClient.fetchStatus("MIDLWARE"))
                .thenReturn(Optional.of(new LookupStatusResponse(4L, "MIDLWARE",
                        "Siparis Alindi Isleniyor...", "ORDER")));
        Mockito.when(lookupCatalogClient.fetchType("NEWSALE"))
                .thenReturn(Optional.of(new LookupTypeResponse(7L, "NEWSALE", "New Sale", "BSN_INTER_TYPE")));
    }

    @Test
    @DisplayName("a broker outage loses nothing: the row waits and the next drain sends it")
    void survivesABrokerOutage() {
        // Counters live in the shared MeterRegistry for the whole Spring context and are
        // cumulative across tests (rows are cleared per test, meters are not). Asserting
        // a DELTA keeps this test independent of what ran before it.
        double failuresBefore = counter(OutboxMetrics.PUBLISH_FAILURES);
        persistence.persistOrder(request(), ACCOUNT, "saga-outage");

        publisher.available = false;
        assertThat(relay.drainOnce()).isZero();

        OutboxMessage afterFailure = repository.findAll().getFirst();
        assertThat(afterFailure.getPublishedAt()).isNull();
        assertThat(afterFailure.getPublishAttempts()).isEqualTo(1);
        assertThat(afterFailure.getLastError()).contains("simulated broker outage");
        assertThat(counter(OutboxMetrics.PUBLISH_FAILURES) - failuresBefore).isEqualTo(1.0);

        // The broker comes back. Nothing is re-recorded and nothing is replayed by hand:
        // the row was never marked published, so the next poll simply finds it again.
        publisher.available = true;
        assertThat(relay.drainOnce()).isEqualTo(1);

        OutboxMessage afterRecovery = repository.findAll().getFirst();
        assertThat(afterRecovery.getPublishedAt()).isNotNull();
        assertThat(afterRecovery.getPublishAttempts()).isEqualTo(2);
        assertThat(afterRecovery.getLastError()).isNull();
        assertThat(publisher.accepted).hasSize(1);
    }

    @Test
    @DisplayName("a drained outbox stays drained — a published row is not sent twice")
    void doesNotRepublishPublishedRows() {
        persistence.persistOrder(request(), ACCOUNT, "saga-once");

        assertThat(relay.drainOnce()).isEqualTo(1);
        assertThat(relay.drainOnce()).isZero();
        assertThat(publisher.accepted).hasSize(1);
    }

    @Test
    @DisplayName("the backlog and oldest-age gauges report what is actually owed")
    void reportsBacklogAndOldestAge() {
        persistence.persistOrder(request(), ACCOUNT, "saga-metrics");

        assertThat(gauge(OutboxMetrics.BACKLOG)).isEqualTo(1.0);
        // A backlog gauge alone cannot distinguish "one message just arrived" from "one
        // message has been stuck since Tuesday" — the age gauge is what does, so it must
        // be non-null while anything is owed.
        assertThat(gauge(OutboxMetrics.OLDEST_AGE)).isGreaterThanOrEqualTo(0.0);

        relay.drainOnce();

        assertThat(gauge(OutboxMetrics.BACKLOG)).isZero();
        assertThat(gauge(OutboxMetrics.OLDEST_AGE)).isZero();
    }

    @Test
    @DisplayName("retention deletes published rows and NEVER an unpublished one")
    void retentionOnlyRemovesPublishedRows() {
        double deletedBefore = counter(OutboxMetrics.RETENTION_DELETED);
        persistence.persistOrder(request(), ACCOUNT, "saga-published");
        relay.drainOnce();

        // A second message the broker never accepted. It is OLDER than the retention
        // horizon (0s) in every sense except the one that matters — it was never
        // published — so a time-only cleanup would silently delete the very backlog the
        // metrics exist to surface.
        publisher.available = false;
        persistence.persistOrder(request(), ACCOUNT, "saga-stuck");
        relay.drainOnce();
        publisher.available = true;

        assertThat(repository.count()).isEqualTo(2);

        int deleted = retentionJob.purge();

        assertThat(deleted).isEqualTo(1);
        List<OutboxMessage> remaining = repository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().getSagaId()).isEqualTo("saga-stuck");
        assertThat(remaining.getFirst().getPublishedAt()).isNull();
        assertThat(counter(OutboxMetrics.RETENTION_DELETED) - deletedBefore).isEqualTo(1.0);
    }

    @Test
    @DisplayName("the published envelope carries the per-sale ordering key")
    void publishedEnvelopeCarriesThePartitionKey() {
        persistence.persistOrder(request(), ACCOUNT, "saga-ordering");
        relay.drainOnce();

        EventEnvelope sent = publisher.accepted.getFirst();
        assertThat(sent.sagaId()).isEqualTo("saga-ordering");
        assertThat(sent.partitionKey()).isEqualTo("saga-ordering");
        assertThat(sent.occurredAt()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    @DisplayName("retention is a no-op while it is disabled")
    void retentionRespectsItsSwitch() {
        persistence.persistOrder(request(), ACCOUNT, "saga-retention-off");
        relay.drainOnce();

        properties.getRetention().setEnabled(false);
        try {
            assertThat(retentionJob.purge()).isZero();
            assertThat(repository.count()).isEqualTo(1);
        } finally {
            properties.getRetention().setEnabled(true);
        }
    }

    private double counter(String name) {
        return meterRegistry.find(name).counters().stream().mapToDouble(c -> c.count()).sum();
    }

    private double gauge(String name) {
        return meterRegistry.get(name).gauge().value();
    }

    private OrderCreateRequest request() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setAccountNumber("1261000010");
        request.setServiceAddressId(1L);
        OrderItemRequest item = new OrderItemRequest();
        item.setOfferId(1L);
        item.setCharacteristics(List.of());
        request.setItems(List.of(item));
        return request;
    }
}
