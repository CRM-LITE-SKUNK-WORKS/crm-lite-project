package com.crm.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crm.account.account.number.AccountNumberCapacityExceededException;
import com.crm.account.account.number.AccountNumberGenerator;
import com.crm.account.account.number.LuhnCheckDigit;
import com.crm.account.customer.CustomerAddress;
import com.crm.account.customer.CustomerServiceClient;
import com.crm.account.customer.CustomerServiceUnavailableException;
import com.crm.account.customer.CustomerSummary;
import com.crm.account.lookup.LookupCatalogClient;
import com.crm.account.lookup.LookupCatalogUnavailableException;
import com.crm.account.lookup.LookupStatusResponse;
import com.crm.account.testsecurity.TestSecurity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * The two external boundaries — the shared catalog client (ADR-002) and the
 * customer-service client (ADR-013) — are mocked at their interface, never
 * bypassed: the real LookupCatalogService validation/caching logic still runs.
 *
 * Security (ADR-009): the REAL resource-server filter chain from
 * crm-security-starter is active; only JWT decoding is stubbed (TestSecurity).
 *
 * The Clock is pinned to 2026 so KR-11 numbers continue the Flyway seed
 * deterministically (V2 seed consumed 100000..100003, V4 fixture expansion
 * consumed 100004..100017 across customers 1004-1008/1011; next_value = 100018).
 *
 * Ordered: later tests build on the sequence/passivation state of earlier ones.
 * Requires a running Docker daemon. Rerun with:
 *   mvn -pl backend/account-service test
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestSecurity.TestJwtDecoderConfiguration.class, AccountServiceIntegrationTest.FixedClockConfiguration.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountServiceIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneOffset.UTC);
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

    @LocalServerPort
    int port;

    @MockitoBean
    LookupCatalogClient lookupCatalogClient;

    @MockitoBean
    CustomerServiceClient customerServiceClient;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AccountNumberGenerator numberGenerator;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurity.OPERATOR_TOKEN)
                .defaultStatusHandler(status -> true, (req, res) -> { /* assert on status manually */ })
                .build();
        stubHealthyCatalog();
        stubCustomers();
    }

    private void stubHealthyCatalog() {
        Mockito.when(lookupCatalogClient.fetchStatus("ACTV"))
                .thenReturn(Optional.of(new LookupStatusResponse(1L, "ACTV", "Active", "GENERAL")));
        Mockito.when(lookupCatalogClient.fetchStatus("PASV"))
                .thenReturn(Optional.of(new LookupStatusResponse(2L, "PASV", "Passive", "GENERAL")));
    }

    private void stubCustomers() {
        Mockito.when(customerServiceClient.fetchCustomer(Mockito.anyLong())).thenReturn(Optional.empty());
        Mockito.when(customerServiceClient.fetchCustomer(1001L))
                .thenReturn(Optional.of(new CustomerSummary(1001L, "ACTV")));
        Mockito.when(customerServiceClient.fetchCustomer(1002L))
                .thenReturn(Optional.of(new CustomerSummary(1002L, "ACTV")));
        Mockito.when(customerServiceClient.fetchAddresses(1001L))
                .thenReturn(List.of(new CustomerAddress(1L, true), new CustomerAddress(2L, false)));
        Mockito.when(customerServiceClient.fetchAddresses(1002L))
                .thenReturn(List.of(new CustomerAddress(3L, true)));
    }

    // ---------------------------------------------------------------- http helpers

    private ResponseEntity<Map> get(String path) {
        return http.get().uri(path).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<List> getList(String path) {
        return http.get().uri(path).retrieve().toEntity(List.class);
    }

    private ResponseEntity<Map> send(HttpMethod method, String path, String json) {
        return http.method(method).uri(path)
                .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
                .body(json).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> delete(String path) {
        return http.method(HttpMethod.DELETE).uri(path).retrieve().toEntity(Map.class);
    }

    private int nextValue(int segment, int year) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT next_value FROM acct_number_seq WHERE segment = ? AND seq_year = ?",
                Integer.class, segment, year);
        return value == null ? -1 : value;
    }

    private int accountCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cust_acct", Integer.class);
        return count == null ? -1 : count;
    }

    // customerNumber added by the ADR-013 §3.6 amendment: a PUBLIC business number
    // (the same one the list endpoint takes as customerId), never an internal id.
    private static final Set<String> CONTRACT_KEYS = Set.of(
            "accountNumber", "customerNumber", "accountName", "accountTypeCode", "accountTypeName",
            "billingAddressId", "accountStatus");

    private int involvementCount(String accountNumber) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cust_acct_prod_invl i JOIN cust_acct a ON a.id = i.customer_account_id "
                        + "WHERE a.account_number = ? AND i.deleted_date IS NULL",
                Integer.class, accountNumber);
        return count == null ? -1 : count;
    }

    // ---------------------------------------------------------------- security (ADR-009)

    @Test
    @Order(1)
    @DisplayName("zero trust: anonymous -> 401, token without crm-user -> 403, health public")
    void securityChecks() {
        RestClient anonymous = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { }).build();
        ResponseEntity<Map> unauthorized = anonymous.get().uri("/api/accounts?customerId=1001")
                .retrieve().toEntity(Map.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.getBody().get("messageKey")).isEqualTo("MSG-AUTH-UNAUTHORIZED");

        RestClient noRole = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurity.NO_ROLE_TOKEN)
                .defaultStatusHandler(status -> true, (req, res) -> { }).build();
        ResponseEntity<Map> forbidden = noRole.get().uri("/api/accounts?customerId=1001")
                .retrieve().toEntity(Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().get("messageKey")).isEqualTo("MSG-AUTH-FORBIDDEN");

        assertThat(anonymous.get().uri("/actuator/health").retrieve().toEntity(Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ---------------------------------------------------------------- schema + seed (CP3)

    @Test
    @Order(2)
    @DisplayName("schema: only account tables exist — no gnl_*, customer, address or users tables (ADR-002/011/013)")
    void schemaContainsOnlyAccountTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
                String.class);
        assertThat(tables).containsExactlyInAnyOrder(
                "acct_tp", "cust_acct", "cust_acct_prod_invl", "acct_number_seq", "flyway_schema_history",
                // ADR-017 (V5). Service-LOCAL by rule: account_db's own outbox and inbox,
                // never shared and never read by another service — the same ownership
                // property the rest of this test asserts about the workbook tables.
                "outbox_message", "inbox_message");
    }

    @Test
    @Order(3)
    @DisplayName("seed: KR-11 regenerated numbers, next_value=100018, 224-only list in AC-ACCT-01-04 order, 223 hidden")
    void seedDataLoaded() {
        assertThat(nextValue(1, 2026)).isEqualTo(100018);

        ResponseEntity<List> response = getList("/api/accounts?customerId=1001");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> rows = response.getBody();
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(row -> row.get("accountNumber"))
                .containsExactly("1261000010", "1261000028", "1261000036");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.keySet()).isEqualTo(CONTRACT_KEYS);        // no internal ids, no Action field
            assertThat(row.get("accountTypeCode")).isEqualTo("224");  // AC-ACCT-01-02: 224 only
            assertThat(row.get("accountStatus")).isEqualTo("Active");
            assertThat(LuhnCheckDigit.isValid((String) row.get("accountNumber"))).isTrue();
        });

        // K-8: the 223 exists in the database but is invisible through the public API.
        Integer type223 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cust_acct WHERE account_number = '1261000002' AND account_type_id = 1",
                Integer.class);
        assertThat(type223).isEqualTo(1);
        ResponseEntity<Map> detail223 = get("/api/accounts/1261000002");
        assertThat(detail223.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detail223.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-FOUND");
    }

    // ---------------------------------------------------------------- list contract (FR-ACCT-01)

    @Test
    @Order(4)
    @DisplayName("list: customerId is required and numeric; unknown customer -> empty list, no cross-service call")
    void listValidation() {
        ResponseEntity<Map> missing = get("/api/accounts");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");

        ResponseEntity<Map> nonNumeric = get("/api/accounts?customerId=abc");
        assertThat(nonNumeric.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(nonNumeric.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");

        ResponseEntity<List> unknownCustomer = getList("/api/accounts?customerId=9999");
        assertThat(unknownCustomer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unknownCustomer.getBody()).isEmpty();
        // Reads stay local (ADR-013 §3.1): the list never consults customer-service.
        Mockito.verify(customerServiceClient, Mockito.never()).fetchAddresses(Mockito.anyLong());
    }

    // ---------------------------------------------------------------- create + K-8 (FR-ACCT-02)

    @Test
    @Order(5)
    @DisplayName("first 224 for a customer lazily creates the 223 in the SAME transaction (K-8), from the same sequence")
    void firstBillingAccountCreates223() {
        ResponseEntity<Map> response = send(HttpMethod.POST, "/api/accounts",
                """
                {"customerId": 1002, "accountName": "Zeynep Billing", "addressId": 3}
                """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body.keySet()).isEqualTo(CONTRACT_KEYS);
        // Sequence continuation: 223 consumed 100018 -> 1261000184; the returned 224
        // consumed 100019 -> 1261000192 (both Luhn-checked KR-11 numbers, continuing
        // past the V4 fixture expansion's 100004..100017 range).
        assertThat(body.get("accountNumber")).isEqualTo("1261000192");
        assertThat(body.get("accountName")).isEqualTo("Zeynep Billing");
        assertThat(body.get("accountTypeCode")).isEqualTo("224");
        assertThat(body.get("accountTypeName")).isEqualTo("Billing Account");
        assertThat(body.get("billingAddressId")).isEqualTo(3);
        assertThat(body.get("accountStatus")).isEqualTo("Active");

        // The K-8 223: fixed name constant, customer's primary address, same generator,
        // audit attribution = the JWT subject.
        Map<String, Object> row223 = jdbcTemplate.queryForMap(
                "SELECT account_number, account_name, address_id, created_by FROM cust_acct "
                        + "WHERE customer_number = 1002 AND account_type_id = 1");
        assertThat(row223.get("account_number")).isEqualTo("1261000184");
        assertThat(row223.get("account_name")).isEqualTo("Customer Account");
        assertThat(((Number) row223.get("address_id")).longValue()).isEqualTo(3L);
        assertThat(row223.get("created_by")).isEqualTo(TestSecurity.OPERATOR_SUBJECT);

        // The 223 never shows up in the list (AC-ACCT-01-02).
        ResponseEntity<List> list = getList("/api/accounts?customerId=1002");
        assertThat(list.getBody()).hasSize(1);
        assertThat(((Map<String, Object>) list.getBody().get(0)).get("accountNumber")).isEqualTo("1261000192");

        assertThat(nextValue(1, 2026)).isEqualTo(100020);
    }

    @Test
    @Order(6)
    @DisplayName("second 224 for the same customer does NOT create another 223")
    void secondBillingAccountDoesNotDuplicate223() {
        ResponseEntity<Map> response = send(HttpMethod.POST, "/api/accounts",
                """
                {"customerId": 1002, "accountName": "Zeynep Billing 2", "addressId": 3}
                """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("accountNumber")).isEqualTo("1261000200");

        Integer count223 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cust_acct WHERE customer_number = 1002 AND account_type_id = 1", Integer.class);
        assertThat(count223).isEqualTo(1);
        assertThat(nextValue(1, 2026)).isEqualTo(100021);
    }

    @Test
    @Order(7)
    @DisplayName("create validation: unknown customer 404, foreign address 400, forbidden field 400 MSG-ACCT-IMMUTABLE-FIELD")
    void createValidation() {
        int accountsBefore = accountCount();
        int nextBefore = nextValue(1, 2026);

        ResponseEntity<Map> unknownCustomer = send(HttpMethod.POST, "/api/accounts",
                """
                {"customerId": 9999, "accountName": "Ghost", "addressId": 1}
                """);
        assertThat(unknownCustomer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownCustomer.getBody().get("messageKey")).isEqualTo("MSG-CUST-NOT-FOUND");

        ResponseEntity<Map> foreignAddress = send(HttpMethod.POST, "/api/accounts",
                """
                {"customerId": 1002, "accountName": "Wrong addr", "addressId": 99}
                """);
        assertThat(foreignAddress.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(foreignAddress.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");

        // Account type is not client-selectable; unknown properties are never
        // silently ignored (ADR-013 §3.2/3.4).
        ResponseEntity<Map> forbiddenField = send(HttpMethod.POST, "/api/accounts",
                """
                {"customerId": 1002, "accountName": "Sneaky", "addressId": 3, "accountType": "223"}
                """);
        assertThat(forbiddenField.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(forbiddenField.getBody().get("messageKey")).isEqualTo("MSG-ACCT-IMMUTABLE-FIELD");
        assertThat((String) forbiddenField.getBody().get("message")).contains("accountType");

        ResponseEntity<Map> missingFields = send(HttpMethod.POST, "/api/accounts",
                """
                {"customerId": 1002}
                """);
        assertThat(missingFields.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingFields.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");

        assertThat(accountCount()).isEqualTo(accountsBefore);
        assertThat(nextValue(1, 2026)).isEqualTo(nextBefore);   // nothing consumed
    }

    @Test
    @Order(8)
    @DisplayName("fail closed (503 MSG-SERVICE-UNAVAILABLE): lookup or customer-service down -> nothing persisted, no sequence use")
    void createFailsClosed() {
        int accountsBefore = accountCount();
        int nextBefore = nextValue(1, 2026);

        Mockito.when(lookupCatalogClient.fetchStatus("ACTV"))
                .thenThrow(new LookupCatalogUnavailableException("catalog down", null));
        ResponseEntity<Map> catalogDown = send(HttpMethod.POST, "/api/accounts",
                """
                {"customerId": 1002, "accountName": "No catalog", "addressId": 3}
                """);
        assertThat(catalogDown.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(catalogDown.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");

        Mockito.when(customerServiceClient.fetchCustomer(1002L))
                .thenThrow(new CustomerServiceUnavailableException("customer-service down", null));
        ResponseEntity<Map> customerDown = send(HttpMethod.POST, "/api/accounts",
                """
                {"customerId": 1002, "accountName": "No customer svc", "addressId": 3}
                """);
        assertThat(customerDown.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(customerDown.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");

        assertThat(accountCount()).isEqualTo(accountsBefore);
        assertThat(nextValue(1, 2026)).isEqualTo(nextBefore);
    }

    // ---------------------------------------------------------------- update (FR-ACCT-03)

    @Test
    @Order(9)
    @DisplayName("update: accountName/addressId mutable; immutable fields 400; unknown account 404; foreign address 400")
    void updateFlows() {
        ResponseEntity<Map> updated = send(HttpMethod.PUT, "/api/accounts/1261000028",
                """
                {"accountName": "Renamed Billing", "addressId": 2}
                """);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().keySet()).isEqualTo(CONTRACT_KEYS);
        assertThat(updated.getBody().get("accountNumber")).isEqualTo("1261000028");   // immutable
        assertThat(updated.getBody().get("accountName")).isEqualTo("Renamed Billing");
        assertThat(updated.getBody().get("billingAddressId")).isEqualTo(2);

        ResponseEntity<Map> immutableField = send(HttpMethod.PUT, "/api/accounts/1261000028",
                """
                {"accountName": "X", "addressId": 1, "accountNumber": "9999999999"}
                """);
        assertThat(immutableField.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(immutableField.getBody().get("messageKey")).isEqualTo("MSG-ACCT-IMMUTABLE-FIELD");

        ResponseEntity<Map> unknownAccount = send(HttpMethod.PUT, "/api/accounts/1999999999",
                """
                {"accountName": "X", "addressId": 1}
                """);
        assertThat(unknownAccount.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownAccount.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-FOUND");

        ResponseEntity<Map> foreignAddress = send(HttpMethod.PUT, "/api/accounts/1261000028",
                """
                {"accountName": "X", "addressId": 77}
                """);
        assertThat(foreignAddress.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(foreignAddress.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");
    }

    // ---------------------------------------------------------------- delete (FR-ACCT-04)

    @Test
    @Order(10)
    @DisplayName("delete guard: seeded ACTIVE involvements block passivation with 409 MSG-ACCT-HAS-PRODUCTS")
    void deleteBlockedByActiveProducts() {
        ResponseEntity<Map> blocked = delete("/api/accounts/1261000010");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getBody().get("messageKey")).isEqualTo("MSG-ACCT-HAS-PRODUCTS");

        ResponseEntity<Map> detail = get("/api/accounts/1261000010");
        assertThat(detail.getBody().get("accountStatus")).isEqualTo("Active");
    }

    @Test
    @Order(11)
    @DisplayName("delete = soft passivation: 204, row stays list-visible as Passive AFTER the actives, no physical delete")
    void deletePassivates() {
        ResponseEntity<Map> deleted = delete("/api/accounts/1261000036");
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // AC-ACCT-01-03/04: still visible, Passive group after the Active group.
        List<Map<String, Object>> rows = getList("/api/accounts?customerId=1001").getBody();
        assertThat(rows).extracting(row -> row.get("accountNumber"))
                .containsExactly("1261000010", "1261000028", "1261000036");
        assertThat(rows).extracting(row -> row.get("accountStatus"))
                .containsExactly("Active", "Active", "Passive");

        // Passive detail stays readable (sprint decision), with full payload.
        ResponseEntity<Map> detail = get("/api/accounts/1261000036");
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().get("accountStatus")).isEqualTo("Passive");

        // No physical delete: the row exists with the full soft-delete invariant.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status_id, deleted_date, deleted_by, updated_date FROM cust_acct WHERE account_number = '1261000036'");
        assertThat(((Number) row.get("status_id")).longValue()).isEqualTo(2L);
        assertThat(row.get("deleted_date")).isNotNull();
        assertThat(row.get("updated_date")).isNotNull();
        assertThat(row.get("deleted_by")).isEqualTo(TestSecurity.OPERATOR_SUBJECT);
    }

    @Test
    @Order(12)
    @DisplayName("passive account: PUT and re-DELETE -> 409 MSG-ACCT-NOT-ACTIVE (not 204, not 404)")
    void passiveAccountMutationsRejected() {
        ResponseEntity<Map> update = send(HttpMethod.PUT, "/api/accounts/1261000036",
                """
                {"accountName": "Should fail", "addressId": 1}
                """);
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(update.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-ACTIVE");

        ResponseEntity<Map> reDelete = delete("/api/accounts/1261000036");
        assertThat(reDelete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reDelete.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-ACTIVE");
    }

    @Test
    @Order(13)
    @DisplayName("delete fails closed too: catalog down while resolving PASV -> 503, account stays Active")
    void deleteFailsClosedOnCatalogOutage() {
        Mockito.when(lookupCatalogClient.fetchStatus("PASV"))
                .thenThrow(new LookupCatalogUnavailableException("catalog down", null));
        ResponseEntity<Map> response = delete("/api/accounts/1261000028");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");

        assertThat(get("/api/accounts/1261000028").getBody().get("accountStatus")).isEqualTo("Active");
    }

    @Test
    @Order(14)
    @DisplayName("KR-11: a passivated account's number is never reused by later creations")
    void numberNeverReusedAfterPassivation() {
        Set<String> existing = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT account_number FROM cust_acct", String.class));
        assertThat(existing).contains("1261000036");   // passivated, still holding its number

        ResponseEntity<Map> response = send(HttpMethod.POST, "/api/accounts",
                """
                {"customerId": 1001, "accountName": "Post-passivation", "addressId": 1}
                """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String newNumber = (String) response.getBody().get("accountNumber");
        assertThat(newNumber).isNotIn(existing);
        assertThat(LuhnCheckDigit.isValid(newNumber)).isTrue();
    }

    // ---------------------------------------------------------------- generator (ADR-014, CP5)

    @Test
    @Order(15)
    @DisplayName("sequence: first value is EXACTLY 100000, then 100001; next_value invariant holds (no off-by-one)")
    void sequenceFirstValueBehaviour() {
        assertThat(numberGenerator.allocate(1, 2040)).isEqualTo(100000);
        assertThat(numberGenerator.allocate(1, 2040)).isEqualTo(100001);
        assertThat(nextValue(1, 2040)).isEqualTo(100002);
    }

    @Test
    @Order(16)
    @DisplayName("sequence isolation: each (segment, year) pair advances independently from 100000")
    void sequenceSegmentYearIsolation() {
        assertThat(numberGenerator.allocate(2, 2040)).isEqualTo(100000);
        assertThat(numberGenerator.allocate(1, 2041)).isEqualTo(100000);
        assertThat(nextValue(1, 2040)).isEqualTo(100002);   // untouched by the other pairs
    }

    @Test
    @Order(17)
    @DisplayName("overflow above 999999 -> AccountNumberCapacityExceededException (mapped to 409, never a raw 500)")
    void sequenceOverflow() {
        jdbcTemplate.update("UPDATE acct_number_seq SET next_value = 1000000 WHERE segment = 1 AND seq_year = 2041");
        assertThatThrownBy(() -> numberGenerator.allocate(1, 2041))
                .isInstanceOf(AccountNumberCapacityExceededException.class);
    }

    @Test
    @Order(18)
    @DisplayName("endpoint-level overflow: exhausted segment+year -> 409 MSG-ACCT-NUMBER-CAPACITY-EXCEEDED, nothing persisted")
    void createMapsCapacityTo409() {
        int accountsBefore = accountCount();
        int nextBefore = nextValue(1, 2026);
        jdbcTemplate.update("UPDATE acct_number_seq SET next_value = 1000000 WHERE segment = 1 AND seq_year = 2026");
        try {
            ResponseEntity<Map> response = send(HttpMethod.POST, "/api/accounts",
                    """
                    {"customerId": 1002, "accountName": "No capacity", "addressId": 3}
                    """);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NUMBER-CAPACITY-EXCEEDED");
            assertThat(accountCount()).isEqualTo(accountsBefore);
        } finally {
            jdbcTemplate.update("UPDATE acct_number_seq SET next_value = ? WHERE segment = 1 AND seq_year = 2026",
                    nextBefore);
        }
    }

    @Test
    @Order(20)
    @DisplayName("K-8 atomicity: when the 224's number allocation fails AFTER the 223 was created, BOTH roll back")
    void createIsAtomicAcrossThe223And224() {
        // Customer 1009 (customer-service V3 fixture) deliberately has NO accounts
        // yet — needed so this probe genuinely exercises the K-8 223+224 atomic
        // create; 1005 now owns V4 fixture accounts and would skip 223 creation.
        Mockito.when(customerServiceClient.fetchCustomer(1009L))
                .thenReturn(Optional.of(new CustomerSummary(1009L, "ACTV")));
        Mockito.when(customerServiceClient.fetchAddresses(1009L))
                .thenReturn(List.of(new CustomerAddress(12L, true)));

        int nextBefore = nextValue(1, 2026);
        // Leave capacity for exactly ONE number: the 223 gets 999999, the 224's
        // allocation then overflows — the whole transaction (223 included) must vanish.
        jdbcTemplate.update("UPDATE acct_number_seq SET next_value = 999999 WHERE segment = 1 AND seq_year = 2026");
        try {
            ResponseEntity<Map> response = send(HttpMethod.POST, "/api/accounts",
                    """
                    {"customerId": 1009, "accountName": "Atomicity probe", "addressId": 12}
                    """);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NUMBER-CAPACITY-EXCEEDED");

            Integer rowsFor1009 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cust_acct WHERE customer_number = 1009", Integer.class);
            assertThat(rowsFor1009).isZero();                    // no partial 223 survived
            assertThat(nextValue(1, 2026)).isEqualTo(999999);    // both allocations rolled back
        } finally {
            jdbcTemplate.update("UPDATE acct_number_seq SET next_value = ? WHERE segment = 1 AND seq_year = 2026",
                    nextBefore);
        }
    }

    @Test
    @Order(21)
    @DisplayName("duplicate-number race: DB UNIQUE fallback surfaces as clean 409 MSG-ACCT-DUP-NUMBER, never 500 (ADR-014 §7)")
    void duplicateNumberRaceMapsTo409() {
        // Pre-claim the exact number the next allocation will produce, simulating a
        // race that slipped past the generator.
        int next = nextValue(1, 2026);
        String payload = "1" + "26" + "%06d".formatted(next);
        String collidingNumber = payload + LuhnCheckDigit.compute(payload);
        jdbcTemplate.update("""
                INSERT INTO cust_acct (customer_number, account_type_id, account_number, account_name,
                                       description, address_id, status_id, created_date, created_by)
                VALUES (9990, 2, ?, 'Race fixture', 'Race fixture', 1, 1, NOW(), 'system')
                """, collidingNumber);
        try {
            ResponseEntity<Map> response = send(HttpMethod.POST, "/api/accounts",
                    """
                    {"customerId": 1002, "accountName": "Race loser", "addressId": 3}
                    """);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-ACCT-DUP-NUMBER");
        } finally {
            jdbcTemplate.update("DELETE FROM cust_acct WHERE customer_number = 9990");
        }
    }

    // ------------------------------------------ product-ids (ADR-013 §5 read side)

    @Test
    @Order(22)
    @DisplayName("product-ids: V2+V3 involvements in product_id order — non-deleted only, involvement status NOT filtered")
    void productIdsListsNonDeletedInvolvements() {
        // V2 seeded products 1,2 (ACTV) and V3 added 3 (ACTV) + 4 (PASV, deleted_date
        // NULL): the PASV involvement stays listed — AC-PROD-01-03 shows passive
        // products; only the AC-ACCT-04-03 delete guard filters on ACTV.
        ResponseEntity<List> ids = getList("/api/accounts/1261000010/product-ids");
        assertThat(ids.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids.getBody()).containsExactly(1, 2, 3, 4);

        // MSG-PROD-NONE fixtures stay product-less: empty array, not 404.
        ResponseEntity<List> empty = getList("/api/accounts/1261000028/product-ids");
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empty.getBody()).isEmpty();

        // A Passive account stays readable (AC-ACCT-04-02): 1261000036 was
        // passivated in the delete test above.
        ResponseEntity<List> passiveAccount = getList("/api/accounts/1261000036/product-ids");
        assertThat(passiveAccount.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(passiveAccount.getBody()).isEmpty();
    }

    @Test
    @Order(23)
    @DisplayName("product-ids: unknown number and the K-8 223 are indistinguishable 404s (ADR-013 §4.5)")
    void productIdsHidesUnknownAnd223() {
        ResponseEntity<Map> unknown = get("/api/accounts/1999999999/product-ids");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-FOUND");

        ResponseEntity<Map> hidden223 = get("/api/accounts/1261000002/product-ids");
        assertThat(hidden223.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden223.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-FOUND");
    }

    // ------------------------------------------ V4 fixture expansion coverage

    @Test
    @Order(24)
    @DisplayName("V4 fixture: customer 1004 lists 2 Billing Accounts (multiple-BA scenario), 223 stays hidden")
    void customer1004HasMultipleBillingAccounts() {
        ResponseEntity<List> list = getList("/api/accounts?customerId=1004");
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> rows = list.getBody();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("accountNumber"))
                .containsExactly("1261000051", "1261000069");
        assertThat(rows).allSatisfy(row -> assertThat(row.get("accountTypeCode")).isEqualTo("224"));

        ResponseEntity<Map> hidden223 = get("/api/accounts/1261000044");
        assertThat(hidden223.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden223.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-FOUND");
    }

    @Test
    @Order(25)
    @DisplayName("V4 fixture: BA with no product involvement -> empty array; BA with 2 independent families -> 4 ids")
    void v4FixtureProductIdsCoverage() {
        // Customer 1007's third account (1261000135) is a deliberate MSG-PROD-NONE
        // fixture — no cust_acct_prod_invl rows written for it.
        ResponseEntity<List> empty = getList("/api/accounts/1261000135/product-ids");
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empty.getBody()).isEmpty();

        // Customer 1011's Billing Account (1261000176) is likewise product-less.
        ResponseEntity<List> emptyToo = getList("/api/accounts/1261000176/product-ids");
        assertThat(emptyToo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emptyToo.getBody()).isEmpty();

        // Customer 1007's second account (1261000127) holds two independent product
        // families under one billing account: a standalone Fiber 1000MB product (13)
        // plus an ADSL 16MB main (14) with its modem (15) and activation (16) children.
        ResponseEntity<List> multiFamily = getList("/api/accounts/1261000127/product-ids");
        assertThat(multiFamily.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(multiFamily.getBody()).containsExactly(13, 14, 15, 16);
    }

    // -------------------------------- involvement WRITE command (ADR-013 §8, FR-SALE-01)

    @Test
    @Order(26)
    @DisplayName("involvement write: bulk link on an Active 224 -> 201 with the resulting ids; ACCT_PROD + ACTV + JWT-sub audit")
    void involvementWriteLinksProducts() {
        // 1261000176 (customer 1011) is the V4 product-less fixture — it starts empty,
        // which is what makes the before/after assertion meaningful.
        assertThat(involvementCount("1261000176")).isZero();

        ResponseEntity<Map> response = send(HttpMethod.POST, "/api/accounts/1261000176/product-involvements",
                """
                {"productIds": [31, 30, 32]}
                """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("accountNumber")).isEqualTo("1261000176");
        // Resulting state, product_id ascending — same shape/ordering as the §7 read side.
        assertThat((List<Integer>) response.getBody().get("productIds")).containsExactly(30, 31, 32);

        // The read side (§7) and the command agree — one projection, two doors.
        assertThat(getList("/api/accounts/1261000176/product-ids").getBody()).containsExactly(30, 31, 32);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT i.product_id, i.short_code, i.status_id, i.created_by, i.deleted_date "
                        + "FROM cust_acct_prod_invl i JOIN cust_acct a ON a.id = i.customer_account_id "
                        + "WHERE a.account_number = '1261000176' ORDER BY i.product_id");
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> {
            // ADR-013 §8.3: the workbook's per-table constant, NOT a campaign/offer code.
            assertThat(row.get("short_code")).isEqualTo("ACCT_PROD");
            assertThat(((Number) row.get("status_id")).longValue()).isEqualTo(1L);   // ACTV
            assertThat(row.get("created_by")).isEqualTo(TestSecurity.OPERATOR_SUBJECT);
            assertThat(row.get("deleted_date")).isNull();
        });
    }

    @Test
    @Order(27)
    @DisplayName("involvement write is idempotent per (account, product): retries and in-body duplicates never double-link")
    void involvementWriteIsIdempotent() {
        // The caller is a compensating orchestrator (ADR-016 §5) that may retry, and a
        // duplicate row would silently corrupt the AC-ACCT-04-03 guard's meaning.
        ResponseEntity<Map> retry = send(HttpMethod.POST, "/api/accounts/1261000176/product-involvements",
                """
                {"productIds": [30, 31, 32]}
                """);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((List<Integer>) retry.getBody().get("productIds")).containsExactly(30, 31, 32);
        assertThat(involvementCount("1261000176")).isEqualTo(3);

        // A partial overlap adds only what is genuinely new.
        ResponseEntity<Map> overlap = send(HttpMethod.POST, "/api/accounts/1261000176/product-involvements",
                """
                {"productIds": [32, 33]}
                """);
        assertThat(overlap.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((List<Integer>) overlap.getBody().get("productIds")).containsExactly(30, 31, 32, 33);
        assertThat(involvementCount("1261000176")).isEqualTo(4);

        // The same id repeated inside ONE body must not create two rows either.
        ResponseEntity<Map> duped = send(HttpMethod.POST, "/api/accounts/1261000176/product-involvements",
                """
                {"productIds": [34, 34, 34]}
                """);
        assertThat(duped.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(involvementCount("1261000176")).isEqualTo(5);
    }

    @Test
    @Order(28)
    @DisplayName("involvement write rejections: Passive 409, unknown/223 404, empty or malformed body 400")
    void involvementWriteRejections() {
        // AC-SALE-02-01 enforced server-side: 1261000036 was passivated earlier. A
        // Passive account stays READABLE (§7) but must never acquire products (§8.2).
        assertThat(getList("/api/accounts/1261000036/product-ids").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> passive = send(HttpMethod.POST, "/api/accounts/1261000036/product-involvements",
                """
                {"productIds": [40]}
                """);
        assertThat(passive.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(passive.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-ACTIVE");
        assertThat(involvementCount("1261000036")).isZero();

        // Same visibility rule as everywhere else (ADR-013 §4.5): unknown and the K-8
        // 223 are indistinguishable.
        ResponseEntity<Map> unknown = send(HttpMethod.POST, "/api/accounts/1999999999/product-involvements",
                """
                {"productIds": [40]}
                """);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-FOUND");

        ResponseEntity<Map> hidden223 = send(HttpMethod.POST, "/api/accounts/1261000002/product-involvements",
                """
                {"productIds": [40]}
                """);
        assertThat(hidden223.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden223.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-FOUND");

        // An empty command is a client defect, not a no-op success.
        ResponseEntity<Map> empty = send(HttpMethod.POST, "/api/accounts/1261000135/product-involvements",
                """
                {"productIds": []}
                """);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(empty.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");

        ResponseEntity<Map> negative = send(HttpMethod.POST, "/api/accounts/1261000135/product-involvements",
                """
                {"productIds": [-1]}
                """);
        assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(negative.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");

        assertThat(involvementCount("1261000135")).isZero();
    }

    @Test
    @Order(29)
    @DisplayName("involvement write fails closed: catalog down -> 503 MSG-SERVICE-UNAVAILABLE, nothing persisted")
    void involvementWriteFailsClosed() {
        Mockito.when(lookupCatalogClient.fetchStatus("ACTV"))
                .thenThrow(new LookupCatalogUnavailableException("catalog down", null));

        ResponseEntity<Map> response = send(HttpMethod.POST, "/api/accounts/1261000135/product-involvements",
                """
                {"productIds": [50, 51]}
                """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");
        assertThat(involvementCount("1261000135")).isZero();
    }

    @Test
    @Order(30)
    @DisplayName("AC-ACCT-04-03 guard now fires for a REAL sale: a just-sold-into account can no longer be passivated")
    void soldIntoAccountCannotBePassivated() {
        // Until this slice the delete guard was only ever triggered by seed fixtures.
        // 1261000176 acquired live involvements in the tests above.
        ResponseEntity<Map> response = delete("/api/accounts/1261000176");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-ACCT-HAS-PRODUCTS");

        // Still Active — the refused delete changed nothing.
        assertThat(get("/api/accounts/1261000176").getBody().get("accountStatus")).isEqualTo("Active");
    }

    @Test
    @Order(19)
    @DisplayName("concurrency: parallel allocations yield strictly distinct consecutive values, next_value exact")
    void sequenceConcurrency() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = java.util.stream.IntStream.range(0, threads)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return numberGenerator.allocate(1, 2050);
                    }))
                    .toList();
            start.countDown();

            Set<Integer> issued = new HashSet<>();
            for (Future<Integer> future : futures) {
                issued.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(issued).hasSize(threads);
            assertThat(issued).allSatisfy(v -> assertThat(v).isBetween(100000, 100000 + threads - 1));
            assertThat(nextValue(1, 2050)).isEqualTo(100000 + threads);
        } finally {
            executor.shutdownNow();
        }
    }
}
