package com.crm.order.saga;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.inbox.InboxDispatcher;
import com.crm.order.account.AccountServiceClient;
import com.crm.order.account.AccountSummary;
import com.crm.order.lookup.LookupCatalogClient;
import com.crm.order.lookup.LookupStatusResponse;
import com.crm.order.lookup.LookupTypeResponse;
import com.crm.order.messaging.SaleReplyContracts;
import com.crm.order.messaging.SaleReplyHandler;
import com.crm.order.order.service.impl.SaleDraftPersistence;
import com.crm.order.product.ProductServiceClient;
import com.crm.order.testsecurity.TestSecurity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The asynchronous SALE flow (ADR-018), end to end, against a real PostgreSQL.
 *
 * <p><b>What is real here and what is not.</b> Real: the HTTP layer with the genuine
 * resource-server filter chain, the idempotency filter, the Flyway schema, every local
 * transaction, the KR-12 generator, the saga state machine, the Inbox duplicate guard and
 * the Outbox. Mocked: the three outbound HTTP boundaries (at their INTERFACE, never
 * bypassed) and the broker — replies are fed straight into {@link InboxDispatcher}, which
 * is exactly what the Spring Cloud Stream binding does with the bytes it receives.
 *
 * <p><b>Why no broker.</b> Every behaviour this suite is about — a duplicate reply, a
 * reply that arrives for the wrong state, a compensation that itself fails, a command that
 * is never answered — is a property of the saga and the Inbox, not of Kafka. Feeding the
 * dispatcher directly produces all of them on demand and deterministically; a real broker
 * would add minutes of startup to test someone else's redelivery logic.
 *
 * <p>The clock is pinned to 2026 so KR-12 numbers continue the Flyway seed
 * deterministically, and so a "due" retry time can be created by writing the past into the
 * row rather than by sleeping.
 *
 * <p>Requires a running Docker daemon. Rerun with:
 * {@code mvn -pl backend/order-service test -Dtest=AsyncSaleFlowIntegrationTest}
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // The cutover switch. Without it Submit answers 503 by design (ADR-018 §10),
        // which is itself asserted below.
        "crm.messaging.outbox.enabled=true",
        // No relay: this suite asserts what the BUSINESS transaction wrote. A relay
        // draining rows in the background would turn "not yet published" into a race.
        "crm.messaging.outbox.relay.enabled=false",
        // The recovery job is driven explicitly instead, so its assertions are about what
        // one tick does rather than about when a background thread happened to fire.
        "crm.order.saga.recovery-enabled=false"
})
@Import({TestSecurity.TestJwtDecoderConfiguration.class,
        AsyncSaleFlowIntegrationTest.FixedClockConfiguration.class})
class AsyncSaleFlowIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);
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

    static final String ACTIVE_ACCOUNT = "1261000010";
    static final String PASSIVE_ACCOUNT = "1261000036";
    static final String UNKNOWN_ACCOUNT = "1999999999";

    static final String SUBMIT_BODY = """
            {"serviceAddressId": 1, "campaignId": "CMP-ADSL-01", "items": [
              {"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"}]},
              {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]},
              {"offerId": 3, "characteristics": []}
            ]}
            """;

    @LocalServerPort
    int port;

    @MockitoBean
    LookupCatalogClient lookupCatalogClient;
    @MockitoBean
    AccountServiceClient accountServiceClient;
    @MockitoBean
    ProductServiceClient productServiceClient;

    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EnvelopeCodec codec;
    @Autowired
    InboxDispatcher dispatcher;
    @Autowired
    SaleSagaOrchestrator orchestrator;
    @Autowired
    SaleSagaRepository sagaRepository;
    @Autowired
    SaleSagaRecoveryJob recoveryJob;
    @Autowired
    SaleDraftPersistence draftPersistence;

    RestClient http;
    SaleReplyHandler handler;

    @BeforeEach
    void setUp() {
        http = testClient();
        handler = new SaleReplyHandler(codec, orchestrator);
        stubHealthyCatalog();
        stubAccountService();
    }

    // ------------------------------------------------------------------ DRAFT

    @Test
    @DisplayName("draft: creates a WAIT order with its KR-12 number, no items, no products, no saga")
    void draftCreatesWaitOrderWithNumber() {
        ResponseEntity<Map> response = post("/api/orders/drafts", draftBody(ACTIVE_ACCOUNT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderNumber = (String) response.getBody().get("orderNumber");
        assertThat(orderNumber)
                .as("the Order Number must exist before Submit (analyst decision, ADR-018 §1.1)")
                .isNotNull().hasSize(10);
        assertThat(response.getBody()).containsEntry("orderStatus", "WAIT");
        assertThat(response.getBody()).containsEntry("processingStatus", "DRAFT");

        assertThat(orderStatusIdOf(orderNumber)).isEqualTo(3L);            // GNL_ST WAIT
        assertThat(interactionStatusIdOf(orderNumber)).isEqualTo(3L);      // BSN_INTER exists too
        assertThat(itemCountOf(orderNumber)).isZero();
        assertThat(sagaRepository.existsByOrderNumber(orderNumber))
                .as("a draft starts no saga — that is what makes it incapable of processing itself")
                .isFalse();
        Mockito.verify(productServiceClient, Mockito.never()).createProducts(Mockito.any());
    }

    @Test
    @DisplayName("draft: the same Idempotency-Key returns the first order and creates no second one")
    void duplicateDraftKeyCreatesOneOrder() {
        String key = UUID.randomUUID().toString();
        long before = orderCount();

        ResponseEntity<Map> first = post("/api/orders/drafts", draftBody(ACTIVE_ACCOUNT), key);
        ResponseEntity<Map> replay = post("/api/orders/drafts", draftBody(ACTIVE_ACCOUNT), key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody().get("orderNumber")).isEqualTo(first.getBody().get("orderNumber"));
        assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(orderCount()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("draft: only an Active billing account may start a sale")
    void draftRequiresActiveAccount() {
        assertThat(post("/api/orders/drafts", draftBody(PASSIVE_ACCOUNT)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/api/orders/drafts", draftBody(UNKNOWN_ACCOUNT)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("draft: abandon is idempotent, creates no product, and blocks a later submit")
    void abandonedDraftCannotBeSubmitted() {
        String orderNumber = createDraft();

        assertThat(delete("/api/orders/" + orderNumber + "/draft").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(delete("/api/orders/" + orderNumber + "/draft").getStatusCode())
                .as("a client that lost the response must be able to ask again")
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(orderStatusIdOf(orderNumber)).isEqualTo(5L);            // CANCELLED
        Mockito.verify(productServiceClient, Mockito.never()).createProducts(Mockito.any());

        ResponseEntity<Map> submit = post("/api/orders/" + orderNumber + "/submit", SUBMIT_BODY);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(submit.getBody()).containsEntry("messageKey", "MSG-ORDER-NOT-DRAFT");
        assertThat(sagaRepository.existsByOrderNumber(orderNumber)).isFalse();
    }

    @Test
    @DisplayName("draft: the stale-draft cleanup cancels an expired draft and never submits it")
    void staleDraftCleanupCancelsOnly() {
        String orderNumber = createDraft();
        // Backdated rather than waited for: the job's question is "older than the
        // threshold", and a test that slept for two hours would answer it no better.
        jdbcTemplate.update("UPDATE cust_ord SET created_date = ? WHERE order_number = ?",
                java.sql.Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), orderNumber);

        int cancelled = draftPersistence.cancelStaleDrafts(Instant.parse("2026-01-01T00:00:00Z"), 100);

        assertThat(cancelled).isPositive();
        assertThat(orderStatusIdOf(orderNumber)).isEqualTo(5L);
        assertThat(sagaRepository.existsByOrderNumber(orderNumber))
                .as("the cleanup only ever CANCELS — nothing here may start a sale")
                .isFalse();
    }

    // ----------------------------------------------------------------- SUBMIT

    @Test
    @DisplayName("submit: 202 with WAIT -> MIDLWARE, the saga and its first command in one transaction")
    void submitIsAtomicAndAsynchronous() {
        String orderNumber = createDraft();

        ResponseEntity<Map> response = post("/api/orders/" + orderNumber + "/submit", SUBMIT_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo("/api/orders/" + orderNumber + "/status");
        assertThat(response.getBody()).containsEntry("orderStatus", "MIDLWARE");
        assertThat(response.getBody()).containsEntry("processingStatus", "PROCESSING");

        assertThat(orderStatusIdOf(orderNumber)).isEqualTo(4L);            // MIDLWARE
        assertThat(interactionStatusIdOf(orderNumber)).isEqualTo(4L);
        assertThat(itemCountOf(orderNumber)).isEqualTo(3);

        SaleSaga saga = sagaRepository.findById(orderNumber).orElseThrow();
        assertThat(saga.getSagaId())
                .as("sagaId IS the order number (ADR-018 §3)")
                .isEqualTo(orderNumber);
        assertThat(saga.getCurrentState()).isEqualTo(SaleSagaState.AWAITING_ACCOUNT_CHECK);
        assertThat(outboxDestinations(orderNumber))
                .containsExactly("crm.account.cmd.check-sale-account.v1");

        // The whole point of the cutover: no downstream write happened before the 202.
        Mockito.verify(productServiceClient, Mockito.never()).createProducts(Mockito.any());
        Mockito.verify(accountServiceClient, Mockito.never()).linkProducts(Mockito.anyString(), Mockito.anyList());
    }

    @Test
    @DisplayName("submit: a replayed Idempotency-Key does not start a second saga")
    void duplicateSubmitStartsOneSaga() {
        String orderNumber = createDraft();
        String key = UUID.randomUUID().toString();

        ResponseEntity<Map> first = post("/api/orders/" + orderNumber + "/submit", SUBMIT_BODY, key);
        ResponseEntity<Map> replay = post("/api/orders/" + orderNumber + "/submit", SUBMIT_BODY, key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(outboxDestinations(orderNumber))
                .as("one saga, one first command")
                .containsExactly("crm.account.cmd.check-sale-account.v1");
    }

    @Test
    @DisplayName("submit: only a WAIT draft may be submitted, so one sale cannot be submitted twice")
    void onlyWaitMaySubmit() {
        String orderNumber = createDraft();
        assertThat(post("/api/orders/" + orderNumber + "/submit", SUBMIT_BODY).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        // A NEW key on an order that is no longer WAIT: refused, not replayed.
        ResponseEntity<Map> second = post("/api/orders/" + orderNumber + "/submit", SUBMIT_BODY);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).containsEntry("messageKey", "MSG-ORDER-NOT-DRAFT");
    }

    @Test
    @DisplayName("boundaries: the legacy synchronous route creates no saga, and its order cannot be submitted")
    void legacyAndAsyncCannotBothRunOneSale() {
        stubProductServiceForLegacy();
        String legacyBody = """
                {"accountNumber": "1261000010", "serviceAddressId": 1, "campaignId": "CMP-ADSL-01",
                 "items": [{"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"}]}]}
                """;

        ResponseEntity<Map> legacy = post("/api/orders", legacyBody);
        assertThat(legacy.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderNumber = (String) legacy.getBody().get("orderNumber");

        assertThat(sagaRepository.existsByOrderNumber(orderNumber))
                .as("the legacy route is synchronous and owns no saga")
                .isFalse();
        assertThat(post("/api/orders/" + orderNumber + "/submit", SUBMIT_BODY).getStatusCode())
                .as("a legacy order is created MIDLWARE, so the async submit can never touch it")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------- SAGA

    @Test
    @DisplayName("saga: the happy path completes, attaches products, and emits SaleCompleted last")
    void happyPath() {
        String orderNumber = submittedOrder();

        feed(SaleReplyContracts.ACCOUNT_CHECKED, SaleReplyContracts.ACCOUNT_CHECKED_DESTINATION,
                succeededWithCustomer(orderNumber));
        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.AWAITING_PRODUCT_PREPARATION);
        assertThat(outboxDestinations(orderNumber)).contains("crm.product.cmd.prepare-sale-products.v1");

        feed(SaleReplyContracts.PRODUCTS_PREPARED, SaleReplyContracts.PRODUCTS_PREPARED_DESTINATION,
                prepared(orderNumber));
        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.AWAITING_INVOLVEMENT);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM cust_ord_item i JOIN cust_ord o ON o.id = i.cust_ord_id
                WHERE o.order_number = ? AND i.product_id IS NOT NULL AND i.amount IS NOT NULL
                """, Long.class, orderNumber))
                .as("the authoritative product ids and amount snapshots are written from the reply")
                .isEqualTo(3L);

        feed(SaleReplyContracts.PRODUCTS_LINKED, SaleReplyContracts.PRODUCTS_LINKED_DESTINATION,
                succeeded(orderNumber));
        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.AWAITING_ACTIVATION);
        assertThat(outboxDestinations(orderNumber)).contains("crm.product.cmd.activate-sale-products.v1");
        assertThat(outboxDestinations(orderNumber))
                .as("the terminal fact must not exist before core consistency is reached")
                .doesNotContain("crm.sale.evt.sale-completed.v1");

        feed(SaleReplyContracts.PRODUCTS_ACTIVATED, SaleReplyContracts.PRODUCTS_ACTIVATED_DESTINATION,
                succeeded(orderNumber));

        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.COMPLETED);
        assertThat(outboxDestinations(orderNumber)).contains("crm.sale.evt.sale-completed.v1");
        assertThat(orderStatusIdOf(orderNumber))
                .as("a completed sale stays MIDLWARE — no accepted requirement defines another terminal status")
                .isEqualTo(4L);
        assertThat(statusOf(orderNumber)).containsEntry("processingStatus", "COMPLETED");
    }

    @Test
    @DisplayName("saga: an exact duplicate reply is absorbed by the Inbox and changes nothing")
    void duplicateReplyIsAbsorbed() {
        String orderNumber = submittedOrder();
        String messageId = UUID.randomUUID().toString();

        assertThat(feed(SaleReplyContracts.ACCOUNT_CHECKED, SaleReplyContracts.ACCOUNT_CHECKED_DESTINATION,
                succeededWithCustomer(orderNumber), messageId)).isEqualTo(InboxDispatcher.Outcome.PROCESSED);
        long commandsAfterFirst = outboxDestinations(orderNumber).size();

        assertThat(feed(SaleReplyContracts.ACCOUNT_CHECKED, SaleReplyContracts.ACCOUNT_CHECKED_DESTINATION,
                succeededWithCustomer(orderNumber), messageId)).isEqualTo(InboxDispatcher.Outcome.DUPLICATE);

        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.AWAITING_PRODUCT_PREPARATION);
        assertThat(outboxDestinations(orderNumber)).hasSize((int) commandsAfterFirst);
    }

    @Test
    @DisplayName("saga: a reply that does not fit the current state does not advance it")
    void outOfOrderReplyDoesNotAdvance() {
        String orderNumber = submittedOrder();

        // An activation reply while the saga is still waiting for the account check.
        feed(SaleReplyContracts.PRODUCTS_ACTIVATED, SaleReplyContracts.PRODUCTS_ACTIVATED_DESTINATION,
                succeeded(orderNumber));

        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.AWAITING_ACCOUNT_CHECK);
        assertThat(outboxDestinations(orderNumber)).containsExactly("crm.account.cmd.check-sale-account.v1");
    }

    @Test
    @DisplayName("saga: a rejected account cancels the order with no compensation — nothing exists to undo")
    void accountRejectionFailsWithoutCompensation() {
        String orderNumber = submittedOrder();

        feed(SaleReplyContracts.ACCOUNT_CHECKED, SaleReplyContracts.ACCOUNT_CHECKED_DESTINATION,
                failed(orderNumber, "MSG-ACCT-NOT-ACTIVE"));

        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.FAILED);
        assertThat(orderStatusIdOf(orderNumber)).isEqualTo(5L);            // CANCELLED
        assertThat(outboxDestinations(orderNumber))
                .containsExactly("crm.account.cmd.check-sale-account.v1");
        assertThat(statusOf(orderNumber))
                .containsEntry("processingStatus", "FAILED")
                .containsEntry("failureMessageKey", "MSG-ACCT-NOT-ACTIVE");
    }

    @Test
    @DisplayName("saga: a rejected basket relays product-service's own message key")
    void productPreparationFailureRelaysItsKey() {
        String orderNumber = submittedOrder();
        feed(SaleReplyContracts.ACCOUNT_CHECKED, SaleReplyContracts.ACCOUNT_CHECKED_DESTINATION,
                succeededWithCustomer(orderNumber));

        feed(SaleReplyContracts.PRODUCTS_PREPARED, SaleReplyContracts.PRODUCTS_PREPARED_DESTINATION,
                failed(orderNumber, "MSG-SALE-OFFER-INACTIVE"));

        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.FAILED);
        assertThat(orderStatusIdOf(orderNumber)).isEqualTo(5L);
        assertThat(statusOf(orderNumber)).containsEntry("failureMessageKey", "MSG-SALE-OFFER-INACTIVE");
        assertThat(outboxDestinations(orderNumber))
                .as("product creation is one transaction, so a failure left nothing to compensate")
                .doesNotContain("crm.product.cmd.compensate-sale-products.v1");
    }

    @Test
    @DisplayName("saga: an involvement failure compensates the products only, then cancels the order")
    void involvementFailureCompensatesProducts() {
        String orderNumber = preparedOrder();

        feed(SaleReplyContracts.PRODUCTS_LINKED, SaleReplyContracts.PRODUCTS_LINKED_DESTINATION,
                failed(orderNumber, "MSG-ACCT-NOT-ACTIVE"));
        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.COMPENSATING_PRODUCTS);
        assertThat(outboxDestinations(orderNumber))
                .contains("crm.product.cmd.compensate-sale-products.v1")
                .as("no involvement exists, so none is compensated")
                .doesNotContain("crm.account.cmd.compensate-sale-involvements.v1");

        feed(SaleReplyContracts.PRODUCTS_COMPENSATED, SaleReplyContracts.PRODUCTS_COMPENSATED_DESTINATION,
                succeeded(orderNumber));
        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.FAILED);
        assertThat(orderStatusIdOf(orderNumber)).isEqualTo(5L);
        assertThat(statusOf(orderNumber))
                .as("the ORIGINAL failure is what the client is told about, not the successful undo")
                .containsEntry("failureMessageKey", "MSG-ACCT-NOT-ACTIVE");
    }

    @Test
    @DisplayName("saga: an activation failure compensates the involvement FIRST, then the products")
    void activationFailureCompensatesInvolvementThenProducts() {
        String orderNumber = linkedOrder();

        feed(SaleReplyContracts.PRODUCTS_ACTIVATED, SaleReplyContracts.PRODUCTS_ACTIVATED_DESTINATION,
                failed(orderNumber, "MSG-PROD-NOT-FOUND"));
        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.COMPENSATING_INVOLVEMENT);
        assertThat(outboxDestinations(orderNumber))
                .contains("crm.account.cmd.compensate-sale-involvements.v1")
                .as("products must not be passivated while an account still claims them")
                .doesNotContain("crm.product.cmd.compensate-sale-products.v1");

        feed(SaleReplyContracts.INVOLVEMENTS_COMPENSATED,
                SaleReplyContracts.INVOLVEMENTS_COMPENSATED_DESTINATION, succeeded(orderNumber));
        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.COMPENSATING_PRODUCTS);
        assertThat(outboxDestinations(orderNumber)).contains("crm.product.cmd.compensate-sale-products.v1");

        feed(SaleReplyContracts.PRODUCTS_COMPENSATED, SaleReplyContracts.PRODUCTS_COMPENSATED_DESTINATION,
                succeeded(orderNumber));
        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.FAILED);
        assertThat(orderStatusIdOf(orderNumber)).isEqualTo(5L);
    }

    @Test
    @DisplayName("saga: a failed compensation escalates rather than pretending the sale was undone")
    void failedCompensationEscalates() {
        String orderNumber = preparedOrder();
        feed(SaleReplyContracts.PRODUCTS_LINKED, SaleReplyContracts.PRODUCTS_LINKED_DESTINATION,
                failed(orderNumber, "MSG-ACCT-NOT-ACTIVE"));

        feed(SaleReplyContracts.PRODUCTS_COMPENSATED, SaleReplyContracts.PRODUCTS_COMPENSATED_DESTINATION,
                failed(orderNumber, "MSG-SALE-OPERATION-MISMATCH"));

        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.MANUAL_INTERVENTION);
        assertThat(orderStatusIdOf(orderNumber))
                .as("CANCELLED would assert an undo that did not happen")
                .isEqualTo(4L);                                            // still MIDLWARE
        assertThat(statusOf(orderNumber))
                .as("operations see MANUAL_INTERVENTION; the client sees only FAILED")
                .containsEntry("processingStatus", "FAILED");
    }

    @Test
    @DisplayName("saga: an unsafe failure message is replaced, never relayed to a client")
    void unsafeFailureKeyIsReplaced() {
        String orderNumber = submittedOrder();

        feed(SaleReplyContracts.ACCOUNT_CHECKED, SaleReplyContracts.ACCOUNT_CHECKED_DESTINATION,
                failed(orderNumber, "java.sql.SQLException: connection refused to db:5432"));

        assertThat(statusOf(orderNumber)).containsEntry("failureMessageKey", "MSG-SALE-FAILED");
    }

    @Test
    @DisplayName("saga: a reply for an unknown saga is ignored rather than creating one")
    void replyForUnknownSagaIsIgnored() {
        assertThat(feed(SaleReplyContracts.ACCOUNT_CHECKED, SaleReplyContracts.ACCOUNT_CHECKED_DESTINATION,
                succeededWithCustomer("9999999999")))
                .isEqualTo(InboxDispatcher.Outcome.PROCESSED);
        assertThat(sagaRepository.existsByOrderNumber("9999999999")).isFalse();
    }

    // --------------------------------------------------------------- RECOVERY

    @Test
    @DisplayName("recovery: a saga whose command went unanswered has it reissued, idempotently")
    void stuckSagaCommandIsReissued() {
        String orderNumber = submittedOrder();
        makeDue(orderNumber);

        recoveryJob.recoverOnce();

        SaleSaga saga = sagaRepository.findById(orderNumber).orElseThrow();
        assertThat(saga.getRetryCount()).isEqualTo(1);
        assertThat(saga.getCurrentState())
                .as("a reissue must not advance the saga — the step is still outstanding")
                .isEqualTo(SaleSagaState.AWAITING_ACCOUNT_CHECK);
        assertThat(outboxDestinations(orderNumber))
                .containsExactly("crm.account.cmd.check-sale-account.v1",
                        "crm.account.cmd.check-sale-account.v1");
    }

    @Test
    @DisplayName("recovery: an exhausted retry budget escalates instead of generating load forever")
    void exhaustedRetryBudgetEscalates() {
        String orderNumber = submittedOrder();
        jdbcTemplate.update("UPDATE sale_saga SET retry_count = 99 WHERE saga_id = ?", orderNumber);
        makeDue(orderNumber);

        recoveryJob.recoverOnce();

        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.MANUAL_INTERVENTION);
        assertThat(statusOf(orderNumber))
                .containsEntry("processingStatus", "FAILED")
                .containsEntry("failureMessageKey", "MSG-SALE-FAILED");
    }

    @Test
    @DisplayName("recovery: a terminal saga is never reissued — the same order cannot be fulfilled twice")
    void terminalSagaIsNeverReissued() {
        String orderNumber = submittedOrder();
        feed(SaleReplyContracts.ACCOUNT_CHECKED, SaleReplyContracts.ACCOUNT_CHECKED_DESTINATION,
                failed(orderNumber, "MSG-ACCT-NOT-ACTIVE"));
        int commandsBefore = outboxDestinations(orderNumber).size();
        // A terminal transition already cleared next_retry_at, so the row would not be
        // selected at all. Writing a due time back into it removes that first line of
        // defence, leaving only the state filter — which is the thing worth proving.
        jdbcTemplate.update("UPDATE sale_saga SET next_retry_at = ? WHERE saga_id = ?",
                java.sql.Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), orderNumber);

        recoveryJob.recoverOnce();

        assertThat(stateOf(orderNumber)).isEqualTo(SaleSagaState.FAILED);
        assertThat(outboxDestinations(orderNumber)).hasSize(commandsBefore);
    }

    // ------------------------------------------------------------- test helpers

    private String createDraft() {
        ResponseEntity<Map> response = post("/api/orders/drafts", draftBody(ACTIVE_ACCOUNT));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("orderNumber");
    }

    /** A draft that has been submitted: the saga exists and awaits the account check. */
    private String submittedOrder() {
        String orderNumber = createDraft();
        assertThat(post("/api/orders/" + orderNumber + "/submit", SUBMIT_BODY).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        return orderNumber;
    }

    /** ...and whose products have been prepared: PNDG products exist, no involvement yet. */
    private String preparedOrder() {
        String orderNumber = submittedOrder();
        feed(SaleReplyContracts.ACCOUNT_CHECKED, SaleReplyContracts.ACCOUNT_CHECKED_DESTINATION,
                succeededWithCustomer(orderNumber));
        feed(SaleReplyContracts.PRODUCTS_PREPARED, SaleReplyContracts.PRODUCTS_PREPARED_DESTINATION,
                prepared(orderNumber));
        return orderNumber;
    }

    /** ...and whose products are linked to the account: activation is outstanding. */
    private String linkedOrder() {
        String orderNumber = preparedOrder();
        feed(SaleReplyContracts.PRODUCTS_LINKED, SaleReplyContracts.PRODUCTS_LINKED_DESTINATION,
                succeeded(orderNumber));
        return orderNumber;
    }

    private void makeDue(String orderNumber) {
        jdbcTemplate.update("UPDATE sale_saga SET next_retry_at = ? WHERE saga_id = ?",
                java.sql.Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), orderNumber);
    }

    /**
     * Feeds a reply through the REAL Inbox path — claim, transaction, dedup, dispatch —
     * which is exactly what the stream binding does with the bytes it receives. The only
     * thing missing is the broker.
     */
    private InboxDispatcher.Outcome feed(String messageType, String destination,
                                         SaleReplyContracts.SaleStepOutcome payload) {
        return feed(messageType, destination, payload, UUID.randomUUID().toString());
    }

    private InboxDispatcher.Outcome feed(String messageType, String destination,
                                         SaleReplyContracts.SaleStepOutcome payload, String messageId) {
        EventEnvelope envelope = new EventEnvelope(messageId, messageType, 1, "order",
                payload.orderNumber(), payload.orderNumber(), "corr-test", null,
                Instant.parse("2026-08-01T12:00:00Z"), codec.encodePayload(payload));
        return dispatcher.dispatch(codec.encode(envelope), destination,
                SaleReplyContracts.consumerGroup(destination), handler);
    }

    private static SaleReplyContracts.SaleStepOutcome succeeded(String orderNumber) {
        return new SaleReplyContracts.SaleStepOutcome(orderNumber, SaleReplyContracts.Result.SUCCEEDED,
                null, null, null, null, null, List.of(101L, 102L, 103L));
    }

    private static SaleReplyContracts.SaleStepOutcome succeededWithCustomer(String orderNumber) {
        return new SaleReplyContracts.SaleStepOutcome(orderNumber, SaleReplyContracts.Result.SUCCEEDED,
                null, null, 1001L, null, null, null);
    }

    private static SaleReplyContracts.SaleStepOutcome prepared(String orderNumber) {
        return new SaleReplyContracts.SaleStepOutcome(orderNumber, SaleReplyContracts.Result.SUCCEEDED,
                null, null, null, new BigDecimal("497.00"),
                List.of(new SaleReplyContracts.SaleStepOutcome.PreparedProduct(101L, 1L,
                                new BigDecimal("299.00"), true),
                        new SaleReplyContracts.SaleStepOutcome.PreparedProduct(102L, 2L,
                                new BigDecimal("149.00"), false),
                        new SaleReplyContracts.SaleStepOutcome.PreparedProduct(103L, 3L,
                                new BigDecimal("49.00"), false)),
                List.of(101L, 102L, 103L));
    }

    private static SaleReplyContracts.SaleStepOutcome failed(String orderNumber, String messageKey) {
        return new SaleReplyContracts.SaleStepOutcome(orderNumber, SaleReplyContracts.Result.FAILED,
                "test", messageKey, null, null, null, null);
    }

    private SaleSagaState stateOf(String orderNumber) {
        return sagaRepository.findById(orderNumber).orElseThrow().getCurrentState();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> statusOf(String orderNumber) {
        ResponseEntity<Map> response = http.get().uri("/api/orders/" + orderNumber + "/status")
                .retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<String> outboxDestinations(String orderNumber) {
        return jdbcTemplate.queryForList(
                "SELECT destination FROM outbox_message WHERE saga_id = ? ORDER BY occurred_at, message_id",
                String.class, orderNumber);
    }

    private Long orderStatusIdOf(String orderNumber) {
        return jdbcTemplate.queryForObject("SELECT status_id FROM cust_ord WHERE order_number = ?",
                Long.class, orderNumber);
    }

    private Long interactionStatusIdOf(String orderNumber) {
        return jdbcTemplate.queryForObject("""
                SELECT b.status_id FROM bsn_inter b JOIN cust_ord o ON o.bsn_inter_id = b.id
                WHERE o.order_number = ?
                """, Long.class, orderNumber);
    }

    private Integer itemCountOf(String orderNumber) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM cust_ord_item i JOIN cust_ord o ON o.id = i.cust_ord_id
                WHERE o.order_number = ?
                """, Integer.class, orderNumber);
    }

    private long orderCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cust_ord", Long.class);
    }

    private static String draftBody(String accountNumber) {
        return "{\"accountNumber\": \"" + accountNumber + "\"}";
    }

    // ------------------------------------------------------------------- stubs

    private RestClient testClient() {
        // httpclient5 re-executes a 503'd request by default, which would silently double
        // every assertion about what ONE request did (ADR-016 §5.3b's original incident).
        CloseableHttpClient httpClient = HttpClients.custom().disableAutomaticRetries().build();
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurity.OPERATOR_TOKEN)
                .defaultStatusHandler(status -> true, (req, res) -> { /* asserted manually */ })
                .build();
    }

    private ResponseEntity<Map> post(String path, String json) {
        return post(path, json, UUID.randomUUID().toString());
    }

    private ResponseEntity<Map> post(String path, String json, String idempotencyKey) {
        return http.post().uri(path)
                .headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("Idempotency-Key", idempotencyKey);
                })
                .body(json).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Void> delete(String path) {
        return http.delete().uri(path).retrieve().toBodilessEntity();
    }

    private void stubHealthyCatalog() {
        Mockito.when(lookupCatalogClient.fetchStatus("ACTV"))
                .thenReturn(Optional.of(new LookupStatusResponse(1L, "ACTV", "Active", "GENERAL")));
        Mockito.when(lookupCatalogClient.fetchStatus("PASV"))
                .thenReturn(Optional.of(new LookupStatusResponse(2L, "PASV", "Passive", "GENERAL")));
        // WAIT is written for the first time by ADR-018's draft.
        Mockito.when(lookupCatalogClient.fetchStatus("WAIT"))
                .thenReturn(Optional.of(new LookupStatusResponse(3L, "WAIT", "Waiting", "ORDER")));
        Mockito.when(lookupCatalogClient.fetchStatus("MIDLWARE"))
                .thenReturn(Optional.of(new LookupStatusResponse(4L, "MIDLWARE",
                        "Siparis Alindi Isleniyor...", "ORDER")));
        Mockito.when(lookupCatalogClient.fetchStatus("CANCELLED"))
                .thenReturn(Optional.of(new LookupStatusResponse(5L, "CANCELLED", "Cancelled", "ORDER")));
        Mockito.when(lookupCatalogClient.fetchType("NEWSALE"))
                .thenReturn(Optional.of(new LookupTypeResponse(7L, "NEWSALE", "New Sale", "BSN_INTER_TYPE")));
    }

    private void stubAccountService() {
        Mockito.when(accountServiceClient.fetchAccount(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(accountServiceClient.fetchAccount(ACTIVE_ACCOUNT))
                .thenReturn(Optional.of(new AccountSummary(ACTIVE_ACCOUNT, 1001L, "Active")));
        Mockito.when(accountServiceClient.fetchAccount(PASSIVE_ACCOUNT))
                .thenReturn(Optional.of(new AccountSummary(PASSIVE_ACCOUNT, 1001L, "Passive")));
    }

    /** Only the legacy-route test needs these: the async path never calls product-service. */
    private void stubProductServiceForLegacy() {
        Mockito.when(productServiceClient.createProducts(Mockito.any()))
                .thenReturn(new com.crm.order.product.ProductCreationResult(
                        "CMP-ADSL-01", "ADSL Hosgeldin Kampanyasi", 201L, new BigDecimal("299.00"),
                        List.of(new com.crm.order.product.ProductCreationResult.CreatedProduct(
                                1L, "ADSL 8MB Offer", 201L, true, new BigDecimal("299.00")))));
        Mockito.doNothing().when(productServiceClient).confirmProducts(Mockito.anyList());
        Mockito.doNothing().when(accountServiceClient).linkProducts(Mockito.anyString(), Mockito.anyList());
    }
}
