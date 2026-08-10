package com.crm.product.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.contract.MessageTypes;
import com.crm.messaging.inbox.InboxDispatcher;
import com.crm.messaging.inbox.InboxGuard;
import com.crm.messaging.inbox.InboxMessageRepository;
import com.crm.messaging.inbox.InboxMetrics;
import com.crm.messaging.inbox.MessageHandler;
import com.crm.messaging.outbox.OutboxPublisher;
import com.crm.product.account.AccountServiceClient;
import com.crm.product.customer.CustomerServiceClient;
import com.crm.product.lookup.LookupCatalogClient;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The consumer-side guarantee (ADR-017 §8.2): an at-least-once transport plus this Inbox
 * is exactly-once <i>in effect</i>.
 *
 * <p>Against a real PostgreSQL, because the guarantee IS a database uniqueness constraint.
 * An in-memory test would be checking a {@code HashSet}, which is precisely the weaker
 * thing this design refuses to rely on.
 *
 * <p>Every scenario here is a real operational event, not a hypothetical: the same message
 * delivered twice, a consumer restarted mid-stream, a handler that fails, and a producer
 * that shipped a version this build cannot read.
 *
 * <p>Requires a running Docker daemon. Rerun with:
 *   {@code mvn -pl backend/product-service test -Dtest=InboxDeduplicationIntegrationTest}
 */
@Testcontainers
@SpringBootTest
class InboxDeduplicationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    AccountServiceClient accountServiceClient;
    @MockitoBean
    CustomerServiceClient customerServiceClient;
    @MockitoBean
    LookupCatalogClient lookupCatalogClient;

    @Autowired
    InboxDispatcher dispatcher;
    @Autowired
    InboxGuard guard;
    @Autowired
    InboxMessageRepository inboxRepository;
    @Autowired
    InboxMessageRepository repository;
    @Autowired
    EnvelopeCodec codec;
    @Autowired
    EntityManager entityManager;
    @Autowired
    MeterRegistry meterRegistry;
    @Autowired
    Clock clock;
    @Autowired
    JdbcTemplate jdbcTemplate;

    static final String GROUP = OrderSubmittedContract.CONSUMER_GROUP;
    static final String SOURCE = OrderSubmittedContract.SOURCE_DESTINATION;

    /** Records what it saw, so "processed once" is a count and not an inference. */
    static class RecordingHandler implements MessageHandler {
        final List<String> handled = new ArrayList<>();

        @Override
        public void handle(EventEnvelope envelope) {
            handled.add(envelope.messageId());
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM inbox_message");
    }

    private EventEnvelope envelope(String messageId) {
        return envelope(messageId, MessageTypes.ORDER_SUBMITTED_V);
    }

    private EventEnvelope envelope(String messageId, int schemaVersion) {
        return new EventEnvelope(messageId, MessageTypes.ORDER_SUBMITTED, schemaVersion,
                MessageTypes.AGGREGATE_ORDER, "1000000018", "saga-" + messageId, "corr-1", null,
                Instant.now(clock),
                codec.encodePayload(new OrderSubmittedContract.OrderSubmitted(
                        "1000000018", 1001L, 1L, "CMP-ADSL-01",
                        List.of(new OrderSubmittedContract.OrderSubmitted.Item(1L, Map.of(1L, "16"))))));
    }

    @Test
    @DisplayName("the same message delivered twice is handled exactly once")
    void deduplicatesRedelivery() {
        // Counters live in the shared MeterRegistry for the whole Spring context and are
        // cumulative across tests (inbox rows are cleared per test, meters are not).
        // Asserting a DELTA keeps this test independent of execution order.
        double duplicatesBefore = counter(InboxMetrics.DUPLICATES);
        double processedBefore = counter(InboxMetrics.PROCESSED);
        RecordingHandler handler = new RecordingHandler();
        byte[] body = codec.encode(envelope(UUID.randomUUID().toString()));

        assertThat(dispatcher.dispatch(body, SOURCE, GROUP, handler))
                .isEqualTo(InboxDispatcher.Outcome.PROCESSED);
        assertThat(dispatcher.dispatch(body, SOURCE, GROUP, handler))
                .isEqualTo(InboxDispatcher.Outcome.DUPLICATE);
        assertThat(dispatcher.dispatch(body, SOURCE, GROUP, handler))
                .isEqualTo(InboxDispatcher.Outcome.DUPLICATE);

        assertThat(handler.handled).hasSize(1);
        assertThat(inboxRepository.countByConsumerGroup(GROUP)).isEqualTo(1);
        assertThat(counter(InboxMetrics.DUPLICATES) - duplicatesBefore).isEqualTo(2.0);
        assertThat(counter(InboxMetrics.PROCESSED) - processedBefore).isEqualTo(1.0);
    }

    @Test
    @DisplayName("the UNIQUE constraint is the real guard, not the exists() pre-check")
    void uniquenessConstraintIsTheFinalGuard() {
        // The pre-check in InboxGuard is an optimization; two concurrent deliveries can
        // both pass it. Bypassing it here proves the constraint underneath actually
        // stops the second write — otherwise this design would be relying on a read
        // that races.
        String messageId = UUID.randomUUID().toString();
        RecordingHandler handler = new RecordingHandler();
        dispatcher.dispatch(codec.encode(envelope(messageId)), SOURCE, GROUP, handler);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO inbox_message
                    (message_id, consumer_group, message_type, schema_version, processed_at)
                VALUES (?, ?, ?, 1, now())
                """, messageId, GROUP, MessageTypes.ORDER_SUBMITTED))
                .hasMessageContaining("uq_inbox_message_id_group");
    }

    @Test
    @DisplayName("a consumer restart does not reprocess what the previous process committed")
    void survivesAConsumerRestart() {
        String messageId = UUID.randomUUID().toString();
        byte[] body = codec.encode(envelope(messageId));

        RecordingHandler beforeRestart = new RecordingHandler();
        assertThat(dispatcher.dispatch(body, SOURCE, GROUP, beforeRestart))
                .isEqualTo(InboxDispatcher.Outcome.PROCESSED);

        // "Restart" = a brand-new dispatcher with brand-new in-memory state, reading the
        // same database. Nothing but the inbox table carries over — which is the point:
        // the deduplication state must be in the database, not in the process. A new
        // handler instance is used too, so a stale in-memory list cannot make this pass.
        InboxDispatcher afterRestart = new InboxDispatcher(
                new InboxGuard(repository, entityManager, new InboxMetrics(meterRegistry), clock),
                new EnvelopeCodec(), new InboxMetrics(meterRegistry), throwingPublisher());
        RecordingHandler afterHandler = new RecordingHandler();

        assertThat(afterRestart.dispatch(body, SOURCE, GROUP, afterHandler))
                .isEqualTo(InboxDispatcher.Outcome.DUPLICATE);
        assertThat(afterHandler.handled).isEmpty();
        assertThat(inboxRepository.countByConsumerGroup(GROUP)).isEqualTo(1);
    }

    @Test
    @DisplayName("a failing handler leaves the message unprocessed — claim and work roll back together")
    void handlerFailureRollsBackTheClaim() {
        String messageId = UUID.randomUUID().toString();
        byte[] body = codec.encode(envelope(messageId));
        double failuresBefore = counter(InboxMetrics.FAILURES);
        AtomicInteger attempts = new AtomicInteger();

        MessageHandler failsOnce = envelope -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("downstream hiccup");
            }
        };

        // Rethrown, not swallowed: returning normally would acknowledge a message that
        // was never applied, and no later redelivery would ever come.
        assertThatThrownBy(() -> dispatcher.dispatch(body, SOURCE, GROUP, failsOnce))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("downstream hiccup");

        // The claim rolled back with the handler, so this is a FIRST delivery, not a
        // duplicate. A claim committed separately "to be safe" would have swallowed this
        // message permanently.
        assertThat(inboxRepository.countByConsumerGroup(GROUP)).isZero();
        assertThat(counter(InboxMetrics.FAILURES) - failuresBefore).isEqualTo(1.0);

        assertThat(dispatcher.dispatch(body, SOURCE, GROUP, failsOnce))
                .isEqualTo(InboxDispatcher.Outcome.PROCESSED);
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(inboxRepository.countByConsumerGroup(GROUP)).isEqualTo(1);
    }

    @Test
    @DisplayName("an unreadable schema version is dead-lettered, never retried forever")
    void unsupportedSchemaVersionIsDeadLettered() {
        RecordingHandler handler = new RecordingHandler();
        int fromTheFuture = MessageTypes.currentVersion(MessageTypes.ORDER_SUBMITTED) + 1;
        byte[] body = codec.encode(envelope(UUID.randomUUID().toString(), fromTheFuture));

        List<String> deadLettered = new ArrayList<>();
        InboxDispatcher withCapturingDlq = new InboxDispatcher(guard, codec,
                new InboxMetrics(meterRegistry), (destination, envelope) -> deadLettered.add(destination));

        assertThat(withCapturingDlq.dispatch(body, SOURCE, GROUP, handler))
                .isEqualTo(InboxDispatcher.Outcome.DEAD_LETTERED);

        assertThat(handler.handled).isEmpty();
        // No claim: this build did not process the message, so it must not record that it
        // did. A future build that CAN read v2 gets a clean first delivery from the DLQ.
        assertThat(inboxRepository.countByConsumerGroup(GROUP)).isZero();
        assertThat(deadLettered).containsExactly(SOURCE + ".dlq");
        assertThat(counter(InboxMetrics.DEAD_LETTER)).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("undecodable bytes are dead-lettered rather than stalling the group")
    void undecodableMessageIsDeadLettered() {
        List<String> deadLettered = new ArrayList<>();
        InboxDispatcher withCapturingDlq = new InboxDispatcher(guard, codec,
                new InboxMetrics(meterRegistry), (destination, envelope) -> deadLettered.add(destination));

        assertThat(withCapturingDlq.dispatch("not json at all".getBytes(), SOURCE, GROUP,
                new RecordingHandler())).isEqualTo(InboxDispatcher.Outcome.DEAD_LETTERED);
        assertThat(inboxRepository.countByConsumerGroup(GROUP)).isZero();
    }

    @Test
    @DisplayName("two consumer groups each process the same message once")
    void groupsAreIndependent() {
        String messageId = UUID.randomUUID().toString();
        byte[] body = codec.encode(envelope(messageId));
        RecordingHandler first = new RecordingHandler();
        RecordingHandler second = new RecordingHandler();
        String otherGroup = "some-other-service." + SOURCE;

        assertThat(dispatcher.dispatch(body, SOURCE, GROUP, first))
                .isEqualTo(InboxDispatcher.Outcome.PROCESSED);
        // A different service reading the same event is not a duplicate — which is why
        // the uniqueness key is (messageId, consumerGroup) and not messageId alone.
        assertThat(dispatcher.dispatch(body, SOURCE, otherGroup, second))
                .isEqualTo(InboxDispatcher.Outcome.PROCESSED);

        assertThat(first.handled).hasSize(1);
        assertThat(second.handled).hasSize(1);
        assertThat(inboxRepository.countByConsumerGroup(GROUP)).isEqualTo(1);
        assertThat(inboxRepository.countByConsumerGroup(otherGroup)).isEqualTo(1);
    }

    @Test
    @DisplayName("the real handler decodes into product-service's OWN dto")
    void realHandlerUsesConsumerOwnedDto() {
        // OrderSubmittedHandler is a plain class: constructed with new, called directly,
        // no Spring and no broker (ADR-017 §4).
        OrderSubmittedHandler handler = new OrderSubmittedHandler(codec);

        handler.handle(envelope(UUID.randomUUID().toString()));
    }

    private OutboxPublisher throwingPublisher() {
        return (destination, envelope) -> {
            throw new IllegalStateException("no broker in this test");
        };
    }

    private double counter(String name) {
        return meterRegistry.find(name).counters().stream().mapToDouble(c -> c.count()).sum();
    }
}
