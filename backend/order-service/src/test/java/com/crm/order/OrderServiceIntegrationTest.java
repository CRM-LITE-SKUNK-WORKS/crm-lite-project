package com.crm.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.order.account.AccountNotActiveException;
import com.crm.order.account.AccountServiceClient;
import com.crm.order.account.AccountServiceUnavailableException;
import com.crm.order.account.AccountSummary;
import com.crm.order.lookup.LookupCatalogClient;
import com.crm.order.lookup.LookupCatalogUnavailableException;
import com.crm.order.lookup.LookupStatusResponse;
import com.crm.order.lookup.LookupTypeResponse;
import com.crm.order.product.ProductCreationCommand;
import com.crm.order.product.ProductCreationResult;
import com.crm.order.product.ProductServiceClient;
import com.crm.order.product.ProductServiceUnavailableException;
import com.crm.order.product.ProductValidationException;
import com.crm.order.testsecurity.TestSecurity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
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
 * End-to-end tests against a real PostgreSQL (Testcontainers) through real HTTP.
 * The three external boundaries — lookup, account and product service — are mocked
 * at their INTERFACE, never bypassed: the real orchestration, KR-12 generator,
 * persistence, mapper and the genuine LookupCatalogService validation/caching all
 * run against the Flyway-seeded schema.
 *
 * <p>Mocking at the interface is what makes the compensation paths testable at all:
 * "product-service is unreachable at step 4" is a scenario no live stack could be
 * asked to produce on demand.
 *
 * Security (ADR-009): the REAL resource-server filter chain from
 * crm-security-starter is active; only JWT decoding is stubbed (TestSecurity).
 *
 * The Clock is pinned to 2026 so KR-12 numbers continue the Flyway seed
 * deterministically (V2 consumed 100000; next_value = 100001).
 *
 * Ordered: later tests build on the sequence state of earlier ones.
 * Requires a running Docker daemon. Rerun with:
 *   mvn -pl backend/order-service test
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestSecurity.TestJwtDecoderConfiguration.class, OrderServiceIntegrationTest.FixedClockConfiguration.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderServiceIntegrationTest {

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

    /** The seeded Active billing account (account-service's V2 KR-11 number). */
    static final String ACTIVE_ACCOUNT = "1261000010";
    /** A Passive billing account — AC-SALE-02-01's fixture. */
    static final String PASSIVE_ACCOUNT = "1261000036";
    /** Unknown, or the K-8 223 (indistinguishable by design — ADR-013 §4.5). */
    static final String UNKNOWN_ACCOUNT = "1999999999";

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

    RestClient http;

    @BeforeEach
    void setUp() {
        http = testClient(TestSecurity.OPERATOR_TOKEN);
        stubHealthyCatalog();
        stubAccountService();
        stubProductService();
    }

    /**
     * httpclient5's default retry strategy RE-EXECUTES a request that answered 503,
     * so without {@code disableAutomaticRetries()} a single POST reaches the server
     * twice and every compensation assertion below silently doubles. Disabled here
     * for the same reason production disables it (see {@code HttpClientConfig}) —
     * this suite exists to prove what ONE request does.
     */
    private RestClient testClient(String token) {
        CloseableHttpClient httpClient = HttpClients.custom().disableAutomaticRetries().build();
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { /* assert on status manually */ });
        return token == null ? builder.build()
                : builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }

    private void stubHealthyCatalog() {
        Mockito.when(lookupCatalogClient.fetchStatus("ACTV"))
                .thenReturn(Optional.of(new LookupStatusResponse(1L, "ACTV", "Active", "GENERAL")));
        Mockito.when(lookupCatalogClient.fetchStatus("PASV"))
                .thenReturn(Optional.of(new LookupStatusResponse(2L, "PASV", "Passive", "GENERAL")));
        // The ORDER-domain rows: MIDLWARE is what every order is created with,
        // CANCELLED is compensation-only (ADR-016 §6).
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
        Mockito.doNothing().when(accountServiceClient).linkProducts(Mockito.anyString(), Mockito.anyList());
    }

    private void stubProductService() {
        Mockito.when(productServiceClient.createProducts(Mockito.any())).thenReturn(CREATION);
        Mockito.doNothing().when(productServiceClient).confirmProducts(Mockito.anyList());
        Mockito.doNothing().when(productServiceClient).cancelProducts(Mockito.anyList());
    }

    /** What product-service answers for the standard ADSL basket (offers 1/2/3). */
    static final ProductCreationResult CREATION = new ProductCreationResult(
            "CMP-ADSL-01", "ADSL Hosgeldin Kampanyasi", 21L, new BigDecimal("497.00"),
            List.of(
                    new ProductCreationResult.CreatedProduct(1L, "ADSL 8MB Offer", 21L, true, new BigDecimal("299.00")),
                    new ProductCreationResult.CreatedProduct(2L, "ADSL Data Modem Offer", 22L, false,
                            new BigDecimal("149.00")),
                    new ProductCreationResult.CreatedProduct(3L, "ADSL Activation Offer", 23L, false,
                            new BigDecimal("49.00"))));

    static final String VALID_ORDER = """
            {"accountNumber": "1261000010", "serviceAddressId": 1, "campaignId": "CMP-ADSL-01", "items": [
              {"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"}]},
              {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]},
              {"offerId": 3, "characteristics": []}
            ]}
            """;

    // ---------------------------------------------------------------- http helpers

    private ResponseEntity<Map> get(String path) {
        return http.get().uri(path).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> post(String path, String json) {
        return http.post().uri(path)
                .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
                .body(json).retrieve().toEntity(Map.class);
    }

    private int orderCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cust_ord", Integer.class);
        return count == null ? -1 : count;
    }

    private int nextValue(int segment, int year) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT next_value FROM order_number_seq WHERE segment = ? AND seq_year = ?",
                Integer.class, segment, year);
        return value == null ? -1 : value;
    }

    private Map<String, Object> orderRow(String orderNumber) {
        return jdbcTemplate.queryForMap(
                "SELECT o.id, o.status_id, o.total_amount, o.customer_number, o.updated_by, "
                        + "b.status_id AS bsn_status_id, b.bsn_inter_type_id, b.customer_account_number "
                        + "FROM cust_ord o JOIN bsn_inter b ON b.id = o.bsn_inter_id WHERE o.order_number = ?",
                orderNumber);
    }

    /** The single order created by a submit that was expected to fail; asserts it is CANCELLED. */
    private void assertLastOrderCancelled() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT o.order_number, o.status_id, b.status_id AS bsn_status_id "
                        + "FROM cust_ord o JOIN bsn_inter b ON b.id = o.bsn_inter_id ORDER BY o.id DESC LIMIT 1");
        assertThat(((Number) row.get("status_id")).longValue()).isEqualTo(5L);        // CANCELLED
        assertThat(((Number) row.get("bsn_status_id")).longValue()).isEqualTo(5L);
    }

    // ---------------------------------------------------------------- security (ADR-009)

    @Test
    @Order(1)
    @DisplayName("zero trust: anonymous -> 401, token without crm-user -> 403, health public")
    void securityChecks() {
        RestClient anonymous = testClient(null);
        ResponseEntity<Map> unauthorized = anonymous.get().uri("/api/orders/1261000002")
                .retrieve().toEntity(Map.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.getBody().get("messageKey")).isEqualTo("MSG-AUTH-UNAUTHORIZED");

        RestClient noRole = testClient(TestSecurity.NO_ROLE_TOKEN);
        ResponseEntity<Map> forbidden = noRole.get().uri("/api/orders/1261000002").retrieve().toEntity(Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().get("messageKey")).isEqualTo("MSG-AUTH-FORBIDDEN");

        assertThat(anonymous.get().uri("/actuator/health").retrieve().toEntity(Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ---------------------------------------------------------------- schema + seed

    @Test
    @Order(2)
    @DisplayName("schema: only order tables — no gnl_*, customer, account or product table (ADR-002/016 §2.2)")
    void schemaContainsOnlyOrderTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
                String.class);
        assertThat(tables).contains("bsn_inter", "cust_ord", "cust_ord_item", "order_number_seq");
        assertThat(tables).noneMatch(t -> t.startsWith("gnl_"));
        assertThat(tables).doesNotContain("cust", "cust_acct", "prod", "prod_ofr", "addr", "users");
    }

    @Test
    @Order(3)
    @DisplayName("seed: workbook order regenerated to KR-12 1261000002, MIDLWARE, 3 items, next_value=100001")
    void seedDataLoaded() {
        assertThat(nextValue(1, 2026)).isEqualTo(100001);

        Map<String, Object> row = orderRow("1261000002");
        assertThat(((Number) row.get("status_id")).longValue()).isEqualTo(4L);            // MIDLWARE
        assertThat(((Number) row.get("bsn_status_id")).longValue()).isEqualTo(4L);
        assertThat(((Number) row.get("bsn_inter_type_id")).longValue()).isEqualTo(7L);    // NEWSALE only
        assertThat(((Number) row.get("customer_number")).longValue()).isEqualTo(1001L);   // business number, not id 1
        assertThat(row.get("customer_account_number")).isEqualTo("1261000010");           // KR-11, not id 2
        assertThat(((Number) row.get("total_amount")).doubleValue()).isEqualTo(497.00);

        Integer items = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cust_ord_item WHERE cust_ord_id = ?", Integer.class, row.get("id"));
        assertThat(items).isEqualTo(3);
    }

    // ---------------------------------------------------------------- the happy path

    @Test
    @Order(4)
    @DisplayName("submit: KR-12 number continues the seed, MIDLWARE, product ids + amount snapshots attached")
    void submitCreatesOrder() {
        ResponseEntity<Map> response = post("/api/orders", VALID_ORDER);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body = response.getBody();
        // Seed consumed 100000, so this order gets 100001 -> 1261000010.
        assertThat(body.get("orderNumber")).isEqualTo("1261000010");
        assertThat(body.get("orderStatus")).isEqualTo("MIDLWARE");
        assertThat(body.get("accountNumber")).isEqualTo(ACTIVE_ACCOUNT);
        // S4/ADR-016 §2.3: the PUBLIC customer number, obtained from the account —
        // order-service never sees an internal id.
        assertThat(((Number) body.get("customerNumber")).longValue()).isEqualTo(1001L);
        assertThat(((Number) body.get("totalAmount")).doubleValue()).isEqualTo(497.00);
        // The representation carries only what order_db owns (ADR-016 §3).
        assertThat(body.keySet()).isEqualTo(Set.of(
                "orderNumber", "orderStatus", "accountNumber", "customerNumber", "totalAmount", "items"));

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).hasSize(3);
        assertThat(items).extracting(i -> i.get("offerId")).containsExactly(1, 2, 3);
        // Step 3 filled these in — they could not exist when the header was written.
        assertThat(items).extracting(i -> i.get("productId")).containsExactly(21, 22, 23);
        assertThat(items).extracting(i -> ((Number) i.get("amount")).doubleValue())
                .containsExactly(299.00, 149.00, 49.00);

        Map<String, Object> row = orderRow("1261000010");
        assertThat(((Number) row.get("status_id")).longValue()).isEqualTo(4L);
        assertThat(((Number) row.get("bsn_inter_type_id")).longValue()).isEqualTo(7L);
        assertThat(nextValue(1, 2026)).isEqualTo(100002);

        // The full orchestration ran, in order (ADR-016 §5.1).
        ArgumentCaptor<ProductCreationCommand> creationCommand =
                ArgumentCaptor.forClass(ProductCreationCommand.class);
        Mockito.verify(accountServiceClient).fetchAccount(ACTIVE_ACCOUNT);
        Mockito.verify(productServiceClient).createProducts(creationCommand.capture());
        Mockito.verify(productServiceClient).confirmProducts(List.of(21L, 22L, 23L));
        Mockito.verify(accountServiceClient).linkProducts(ACTIVE_ACCOUNT, List.of(21L, 22L, 23L));
        Mockito.verify(productServiceClient, Mockito.never()).cancelProducts(Mockito.anyList());

        // ADR-015 §5.9: product-service checks the service address against this
        // customer, and the number it checks against comes from the ACCOUNT — never
        // from the request body, or a caller could widen its own reach by claiming a
        // different customer. The request carries no customerNumber at all.
        ProductCreationCommand sent = creationCommand.getValue();
        assertThat(sent.customerNumber()).isEqualTo(1001L);
        assertThat(sent.serviceAddressId()).isEqualTo(1L);
        assertThat(VALID_ORDER).doesNotContain("customerNumber");
    }

    @Test
    @Order(5)
    @DisplayName("detail: the created and the seeded order are both retrievable; unknown -> 404")
    void orderDetail() {
        ResponseEntity<Map> created = get("/api/orders/1261000010");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody().get("orderStatus")).isEqualTo("MIDLWARE");

        ResponseEntity<Map> seeded = get("/api/orders/1261000002");
        assertThat(seeded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) seeded.getBody().get("totalAmount")).doubleValue()).isEqualTo(497.00);

        ResponseEntity<Map> unknown = get("/api/orders/9999999999");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("messageKey")).isEqualTo("MSG-ORDER-NOT-FOUND");
    }

    // ---------------------------------------------------------------- preconditions (step 0)

    @Test
    @Order(6)
    @DisplayName("preconditions: unknown account 404, Passive account 409 — nothing written, no downstream call")
    void preconditionsRejectBeforeWriting() {
        int before = orderCount();

        ResponseEntity<Map> unknown = post("/api/orders", VALID_ORDER.replace(ACTIVE_ACCOUNT, UNKNOWN_ACCOUNT));
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-FOUND");

        // AC-SALE-02-01 enforced server-side: a disabled UI action is not a check.
        ResponseEntity<Map> passive = post("/api/orders", VALID_ORDER.replace(ACTIVE_ACCOUNT, PASSIVE_ACCOUNT));
        assertThat(passive.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(passive.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-ACTIVE");

        assertThat(orderCount()).isEqualTo(before);
        // Step 0 is reads only — no product was created for either attempt.
        Mockito.verify(productServiceClient, Mockito.never()).createProducts(Mockito.any());
    }

    @Test
    @Order(7)
    @DisplayName("body validation: empty basket and a malformed account number are 400, nothing written")
    void bodyValidation() {
        int before = orderCount();

        ResponseEntity<Map> emptyBasket = post("/api/orders",
                "{\"accountNumber\": \"1261000010\", \"serviceAddressId\": 1, \"items\": []}");
        assertThat(emptyBasket.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(emptyBasket.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");

        ResponseEntity<Map> badNumber = post("/api/orders", VALID_ORDER.replace(ACTIVE_ACCOUNT, "12"));
        assertThat(badNumber.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> malformed = post("/api/orders", "{not json");
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(orderCount()).isEqualTo(before);
    }

    // ------------------------------------------- compensation: every failing step (ADR-016 §5)

    @Test
    @Order(8)
    @DisplayName("step 2 rejected: product-service's OWN message key is relayed unchanged, order CANCELLED")
    void basketRejectionIsRelayedVerbatim() {
        // ADR-016 §7: collapsing MSG-SALE-* into a generic 400 would break §2.7's
        // whole point — the user is told WHICH rule they broke.
        Mockito.when(productServiceClient.createProducts(Mockito.any()))
                .thenThrow(new ProductValidationException(HttpStatus.BAD_REQUEST, "MSG-SALE-NO-INTERNET",
                        "The basket must contain exactly one internet offer"));

        ResponseEntity<Map> response = post("/api/orders", VALID_ORDER);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-SALE-NO-INTERNET");

        // The order written in step 1 must not linger as a half-made sale.
        assertLastOrderCancelled();
        // Nothing was created, so there is nothing to discard.
        Mockito.verify(productServiceClient, Mockito.never()).cancelProducts(Mockito.anyList());
        Mockito.verify(accountServiceClient, Mockito.never()).linkProducts(Mockito.anyString(), Mockito.anyList());
    }

    @Test
    @Order(9)
    @DisplayName("step 2 unavailable: 503, order CANCELLED, no product or involvement touched")
    void productServiceUnavailableAtCreate() {
        Mockito.when(productServiceClient.createProducts(Mockito.any()))
                .thenThrow(new ProductServiceUnavailableException("product-service down", null));

        ResponseEntity<Map> response = post("/api/orders", VALID_ORDER);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");

        assertLastOrderCancelled();
        Mockito.verify(accountServiceClient, Mockito.never()).linkProducts(Mockito.anyString(), Mockito.anyList());
    }

    @Test
    @Order(10)
    @DisplayName("step 4 (confirm) fails: products discarded, order CANCELLED, involvement never written")
    void confirmFailureCompensates() {
        Mockito.doThrow(new ProductServiceUnavailableException("product-service down", null))
                .when(productServiceClient).confirmProducts(Mockito.anyList());

        ResponseEntity<Map> response = post("/api/orders", VALID_ORDER);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        assertLastOrderCancelled();
        // The compensation targets exactly what step 2 created — still PNDG, so the
        // PNDG-only guard in product-service is never violated (ADR-015 §5.7).
        Mockito.verify(productServiceClient).cancelProducts(List.of(21L, 22L, 23L));
        // Confirm is BEFORE the commit point, so no involvement exists to undo.
        Mockito.verify(accountServiceClient, Mockito.never()).linkProducts(Mockito.anyString(), Mockito.anyList());
    }

    @Test
    @Order(11)
    @DisplayName("step 5 (commit point) unavailable: products discarded, order CANCELLED, 503")
    void involvementWriteUnavailableCompensates() {
        Mockito.doThrow(new AccountServiceUnavailableException("account-service down", null))
                .when(accountServiceClient).linkProducts(Mockito.anyString(), Mockito.anyList());

        ResponseEntity<Map> response = post("/api/orders", VALID_ORDER);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");

        assertLastOrderCancelled();
        Mockito.verify(productServiceClient).cancelProducts(List.of(21L, 22L, 23L));
    }

    @Test
    @Order(12)
    @DisplayName("step 5 refused (account went Passive): 409 MSG-ACCT-NOT-ACTIVE, not a 503; order CANCELLED")
    void involvementWriteRefusedSurfacesAsConflict() {
        // The account is reachable and known — it simply may not be sold into. "The
        // account is passive" is actionable; "a service is down" is not.
        Mockito.doThrow(new AccountNotActiveException(ACTIVE_ACCOUNT))
                .when(accountServiceClient).linkProducts(Mockito.anyString(), Mockito.anyList());

        ResponseEntity<Map> response = post("/api/orders", VALID_ORDER);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-ACTIVE");

        assertLastOrderCancelled();
        Mockito.verify(productServiceClient).cancelProducts(List.of(21L, 22L, 23L));
    }

    @Test
    @Order(13)
    @DisplayName("a compensated order keeps its number forever and stays retrievable as CANCELLED")
    void cancelledOrderKeepsItsNumberAndStaysVisible() {
        // ADR-016 §4.6: the number is never released. A CANCELLED order is a real
        // record of something that was attempted — hiding it would make a compensated
        // sale indistinguishable from one that never happened.
        Map<String, Object> lastRow = jdbcTemplate.queryForMap(
                "SELECT order_number, status_id FROM cust_ord ORDER BY id DESC LIMIT 1");
        String cancelledNumber = (String) lastRow.get("order_number");
        assertThat(((Number) lastRow.get("status_id")).longValue()).isEqualTo(5L);

        ResponseEntity<Map> detail = get("/api/orders/" + cancelledNumber);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().get("orderStatus")).isEqualTo("CANCELLED");

        // The next successful sale gets a FRESH number — never the cancelled one.
        ResponseEntity<Map> next = post("/api/orders", VALID_ORDER);
        assertThat(next.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(next.getBody().get("orderNumber")).isNotEqualTo(cancelledNumber);
    }

    // ---------------------------------------------------------------- fail closed (ADR-002 §7)

    @Test
    @Order(14)
    @DisplayName("catalog outage: 503, NOTHING persisted — the order is never even written")
    void writeFailsClosedWhenCatalogUnavailable() {
        int before = orderCount();
        Mockito.when(lookupCatalogClient.fetchType("NEWSALE"))
                .thenThrow(new LookupCatalogUnavailableException("catalog down", null));

        ResponseEntity<Map> response = post("/api/orders", VALID_ORDER);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");

        // Statuses are resolved BEFORE anything is written, so step 1 rolls back whole
        // — no CANCELLED husk, and the sequence increment rolls back with it.
        assertThat(orderCount()).isEqualTo(before);
        Mockito.verify(productServiceClient, Mockito.never()).createProducts(Mockito.any());

        // Reads are unaffected (ADR-002 §8): an order lookup needs no catalog call.
        assertThat(get("/api/orders/1261000002").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(15)
    @DisplayName("KR-12 sequence: gapless and monotonic across successes AND compensated sales")
    void sequenceIsGaplessAcrossCompensations() {
        int before = nextValue(1, 2026);

        ResponseEntity<Map> ok = post("/api/orders", VALID_ORDER);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(nextValue(1, 2026)).isEqualTo(before + 1);

        // A compensated sale still consumes its number (ADR-016 §4.6) — the order row
        // survives as CANCELLED, so releasing the number would risk reuse.
        Mockito.doThrow(new AccountServiceUnavailableException("down", null))
                .when(accountServiceClient).linkProducts(Mockito.anyString(), Mockito.anyList());
        assertThat(post("/api/orders", VALID_ORDER).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(nextValue(1, 2026)).isEqualTo(before + 2);

        // Every number ever issued is distinct and Luhn-valid.
        List<String> numbers = jdbcTemplate.queryForList("SELECT order_number FROM cust_ord", String.class);
        assertThat(numbers).doesNotHaveDuplicates();
        assertThat(numbers).allSatisfy(number -> assertThat(
                com.crm.order.order.number.LuhnCheckDigit.isValid(number)).isTrue());
    }
}
