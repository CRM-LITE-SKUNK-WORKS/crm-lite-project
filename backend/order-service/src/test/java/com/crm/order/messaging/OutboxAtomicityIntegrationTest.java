package com.crm.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.contract.MessageTypes;
import com.crm.messaging.outbox.OutboxMessage;
import com.crm.messaging.outbox.OutboxMessageRepository;
import com.crm.messaging.outbox.OutboxRecorder;
import com.crm.order.account.AccountSummary;
import com.crm.order.account.AccountServiceClient;
import com.crm.order.lookup.LookupCatalogClient;
import com.crm.order.lookup.LookupStatusResponse;
import com.crm.order.lookup.LookupTypeResponse;
import com.crm.order.order.dto.request.OrderCharacteristicRequest;
import com.crm.order.order.dto.request.OrderCreateRequest;
import com.crm.order.order.dto.request.OrderItemRequest;
import com.crm.order.order.entity.CustomerOrder;
import com.crm.order.order.repository.CustomerOrderRepository;
import com.crm.order.order.service.impl.OrderPersistence;
import com.crm.order.product.ProductServiceClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-017 §8.1's central claim, tested rather than asserted: <b>the business state change
 * and its Outbox record commit together or not at all.</b>
 *
 * <p>Against a real PostgreSQL (Testcontainers) with the real Flyway schema, the real
 * {@code OrderPersistence} transaction and the real recorder — a mock at any of those
 * boundaries would be testing the mock's transaction semantics, which is exactly the thing
 * in question.
 *
 * <p>{@code crm.messaging.outbox.enabled=true} here and false in shipped configuration
 * (ADR-017 §11): the mechanism is proven, the SALE flow is not cut over.
 *
 * <p>Requires a running Docker daemon. Rerun with:
 *   {@code mvn -pl backend/order-service test -Dtest=OutboxAtomicityIntegrationTest}
 */
@Testcontainers
@SpringBootTest(properties = {
        "crm.messaging.outbox.enabled=true",
        // The relay stays off: this suite is about what the BUSINESS transaction writes.
        // A relay running in the background would drain rows mid-assertion and turn
        // "not yet published" into a race.
        "crm.messaging.outbox.relay.enabled=false"
})
class OutboxAtomicityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    static final String ACTIVE_ACCOUNT = "1261000010";
    static final AccountSummary ACCOUNT = new AccountSummary(ACTIVE_ACCOUNT, 1001L, "Active");

    @MockitoBean
    LookupCatalogClient lookupCatalogClient;
    @MockitoBean
    AccountServiceClient accountServiceClient;
    @MockitoBean
    ProductServiceClient productServiceClient;

    @Autowired
    OrderPersistence persistence;
    @Autowired
    OutboxRecorder recorder;
    @Autowired
    OutboxMessageRepository outboxRepository;
    @Autowired
    CustomerOrderRepository orderRepository;
    @Autowired
    EnvelopeCodec codec;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM outbox_message");
        Mockito.when(lookupCatalogClient.fetchStatus("ACTV"))
                .thenReturn(Optional.of(new LookupStatusResponse(1L, "ACTV", "Active", "GENERAL")));
        Mockito.when(lookupCatalogClient.fetchStatus("MIDLWARE"))
                .thenReturn(Optional.of(new LookupStatusResponse(4L, "MIDLWARE",
                        "Siparis Alindi Isleniyor...", "ORDER")));
        Mockito.when(lookupCatalogClient.fetchType("NEWSALE"))
                .thenReturn(Optional.of(new LookupTypeResponse(7L, "NEWSALE", "New Sale", "BSN_INTER_TYPE")));
    }

    @Test
    @DisplayName("the order and its outbox row are written by the SAME transaction")
    void writesOrderAndOutboxTogether() {
        CustomerOrder order = persistence.persistOrder(request(), ACCOUNT, "idem-key-1");

        List<OutboxMessage> rows = outboxRepository.findAll();
        assertThat(rows).hasSize(1);
        OutboxMessage row = rows.getFirst();

        assertThat(row.getMessageType()).isEqualTo(MessageTypes.ORDER_SUBMITTED);
        assertThat(row.getSchemaVersion()).isEqualTo(MessageTypes.ORDER_SUBMITTED_V);
        assertThat(row.getAggregateType()).isEqualTo(MessageTypes.AGGREGATE_ORDER);
        // The aggregate id is the KR-12 number the SAME transaction allocated, which is
        // only knowable because the two writes really are one transaction.
        assertThat(row.getAggregateId()).isEqualTo(order.getOrderNumber());
        assertThat(row.getDestination()).isEqualTo("crm.order.evt.order-submitted.v1");
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getPublishAttempts()).isZero();

        // The saga id IS the client's Idempotency-Key (ADR-016 §10 / ADR-017 §7.6) —
        // one sale, one id, not two ids nobody can correlate afterwards.
        assertThat(row.getSagaId()).isEqualTo("idem-key-1");
        assertThat(row.getPartitionKey()).isEqualTo("idem-key-1");

        EventEnvelope envelope = codec.decode(row.getEnvelope());
        OrderEventContracts.OrderSubmittedPayload payload =
                codec.decodePayload(envelope, OrderEventContracts.OrderSubmittedPayload.class);
        assertThat(payload.orderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(payload.accountNumber()).isEqualTo(ACTIVE_ACCOUNT);
        // From the ACCOUNT, never from the request body — the same rule the synchronous
        // path enforces (ADR-015 §5.9).
        assertThat(payload.customerNumber()).isEqualTo(1001L);
        assertThat(payload.items()).hasSize(1);
    }

    @Test
    @DisplayName("a rollback takes the outbox row with the order: neither survives")
    void rollsBackOrderAndOutboxTogether() {
        long ordersBefore = orderRepository.count();

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            persistence.persistOrder(request(), ACCOUNT, "idem-key-2");
            // Anything at all can fail after the write — a downstream 503, a constraint,
            // a bug. What matters is that the message does not escape a transaction that
            // did not commit.
            throw new IllegalStateException("something failed after the local write");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxRepository.count()).isZero();
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    @DisplayName("recording outside a transaction is impossible, not merely discouraged")
    void cannotRecordWithoutATransaction() {
        // Propagation.MANDATORY is the enforcement (ADR-017 §8.1). Without it, "the outbox
        // write must be in the business transaction" would be a code-review rule, and
        // code-review rules are the ones that get broken at 6pm on a Friday.
        assertThatThrownBy(() -> recorder.record(
                OrderEventContracts.ORDER_SUBMITTED_DESTINATION,
                OrderEventContracts.MESSAGE_TYPE,
                OrderEventContracts.AGGREGATE_TYPE,
                "1000000099", "saga-x",
                new OrderEventContracts.OrderSubmittedPayload("1000000099", ACTIVE_ACCOUNT, 1001L,
                        1L, null, List.of())))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("each recorded message gets its own id, so a retry is never a duplicate id")
    void messageIdsAreUnique() {
        CustomerOrder first = persistence.persistOrder(request(), ACCOUNT, "idem-key-3");
        CustomerOrder second = persistence.persistOrder(request(), ACCOUNT, "idem-key-4");

        assertThat(first.getOrderNumber()).isNotEqualTo(second.getOrderNumber());
        assertThat(outboxRepository.findAll())
                .hasSize(2)
                .extracting(OutboxMessage::getMessageId)
                .doesNotHaveDuplicates();
    }

    private OrderCreateRequest request() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setAccountNumber(ACTIVE_ACCOUNT);
        request.setServiceAddressId(1L);
        request.setCampaignId("CMP-ADSL-01");

        OrderCharacteristicRequest characteristic = new OrderCharacteristicRequest();
        characteristic.setCharacteristicId(1L);
        characteristic.setValue("16");

        OrderItemRequest item = new OrderItemRequest();
        item.setOfferId(1L);
        item.setCharacteristics(List.of(characteristic));

        request.setItems(List.of(item));
        return request;
    }
}
