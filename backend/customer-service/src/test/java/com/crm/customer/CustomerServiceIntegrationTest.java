package com.crm.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.customer.account.AccountHasActiveProductsException;
import com.crm.customer.account.AccountServiceClient;
import com.crm.customer.account.AccountServiceUnavailableException;
import com.crm.customer.account.AccountSummary;
import com.crm.customer.address.repository.AddressRepository;
import com.crm.customer.contact.repository.ContactMediumRepository;
import com.crm.customer.customer.repository.CustomerRepository;
import com.crm.customer.customer.repository.IndividualRepository;
import com.crm.customer.customer.repository.PartyRepository;
import com.crm.customer.lookup.LookupCatalogClient;
import com.crm.customer.lookup.LookupCatalogUnavailableException;
import com.crm.customer.lookup.LookupStatusResponse;
import com.crm.customer.lookup.LookupTypeResponse;
import com.crm.customer.mernis.MernisClient;
import com.crm.customer.order.OrderServiceClient;
import com.crm.customer.order.OrderServiceUnavailableException;
import com.crm.customer.order.OrderSummary;
import com.crm.customer.testsecurity.TestSecurity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end tests against a real PostgreSQL (Testcontainers) through real HTTP.
 * The external boundaries — the shared GNL_ST/GNL_TP catalog client (ADR-002), the
 * MERNIS client (KR-10) and the account-service client (AC-CUST-05-04) — are mocked
 * at their interface, never bypassed: the real LookupCatalogService validation/caching
 * logic still runs.
 *
 * Security (ADR-009): the REAL resource-server filter chain from
 * crm-security-starter is active; only JWT decoding is stubbed (TestSecurity), so
 * every request needs a bearer token with the crm-user role and audit columns
 * carry the token's sub claim.
 *
 * Requires a running Docker daemon. Rerun with:
 *   mvn -pl backend/customer-service test
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurity.TestJwtDecoderConfiguration.class)
class CustomerServiceIntegrationTest {

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
    MernisClient mernisClient;

    // Both cross-service boundaries, mocked at the CLIENT interface only — the real
    // logic on this side (account passivation ordering for AC-CUST-05-04; KR-02
    // active/in-progress filtering, unmatched-vs-absent, OR folding and the whole JPA
    // query) still runs against the real database, exactly like LookupCatalogClient
    // above. Nothing about either flow is stubbed out.
    @MockitoBean
    AccountServiceClient accountServiceClient;

    @MockitoBean
    OrderServiceClient orderServiceClient;

    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    IndividualRepository individualRepository;
    @Autowired
    PartyRepository partyRepository;
    @Autowired
    AddressRepository addressRepository;
    @Autowired
    ContactMediumRepository contactMediumRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // Authenticated as the KR-8 operator on every request by default.
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurity.OPERATOR_TOKEN)
                .defaultStatusHandler(status -> true, (req, res) -> { /* assert on status manually */ })
                .build();
        stubHealthyCatalog();
        Mockito.when(mernisClient.verify(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(true);
        // Default: no billing accounts, so existing delete tests that don't care about
        // AC-CUST-05-04 keep passing unchanged. Tests exercising the passivation itself
        // override this per-test.
        Mockito.when(accountServiceClient.listAccounts(Mockito.anyLong())).thenReturn(List.of());
        // Default: no child record anywhere. Every KR-02 test states its own fixture.
        Mockito.when(accountServiceClient.fetchAccount(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(orderServiceClient.fetchOrder(Mockito.anyString())).thenReturn(Optional.empty());
    }

    private void stubHealthyCatalog() {
        Mockito.when(lookupCatalogClient.fetchStatus("ACTV"))
                .thenReturn(Optional.of(new LookupStatusResponse(1L, "ACTV", "Active", "GENERAL")));
        Mockito.when(lookupCatalogClient.fetchStatus("PASV"))
                .thenReturn(Optional.of(new LookupStatusResponse(2L, "PASV", "Passive", "GENERAL")));
        Mockito.when(lookupCatalogClient.fetchType("MALE"))
                .thenReturn(Optional.of(new LookupTypeResponse(1L, "MALE", "Male", "GENDER")));
        Mockito.when(lookupCatalogClient.fetchType("FEMALE"))
                .thenReturn(Optional.of(new LookupTypeResponse(2L, "FEMALE", "Female", "GENDER")));
        Mockito.when(lookupCatalogClient.fetchType("INDV"))
                .thenReturn(Optional.of(new LookupTypeResponse(3L, "INDV", "Individual", "PARTY_TYPE")));
    }

    private String createBody(String firstName, String lastName, String nationalityId) {
        return """
                {
                  "demographic": {
                    "firstName": "%s",
                    "lastName": "%s",
                    "birthDate": "1992-03-15",
                    "gender": "Male",
                    "nationalityId": "%s"
                  },
                  "addresses": [
                    {"cityId": 1, "districtId": 1, "street": "Test Sok.", "houseFlatNumber": "1/1",
                     "addressDescription": "Ev", "primary": true}
                  ],
                  "contactMedium": {"email": "test@example.com", "mobilePhone": "05329998877"}
                }
                """.formatted(firstName, lastName, nationalityId);
    }

    private ResponseEntity<Map> post(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.method(HttpMethod.POST).uri(path).headers(h -> h.addAll(headers))
                .body(json).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> get(String path) {
        return http.get().uri(path).retrieve().toEntity(Map.class);
    }

    // ---------------------------------------------------------------- security (ADR-009)

    @Test
    @DisplayName("direct service access without a token -> 401 MSG-AUTH-UNAUTHORIZED (zero trust)")
    void requestWithoutTokenIsRejected() {
        RestClient anonymous = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();

        ResponseEntity<Map> response = anonymous.get().uri("/api/customers").retrieve().toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-AUTH-UNAUTHORIZED");
    }

    @Test
    @DisplayName("valid token WITHOUT the crm-user role -> 403 MSG-AUTH-FORBIDDEN (explicit role, not authenticated())")
    void tokenWithoutRoleIsForbidden() {
        RestClient noRole = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurity.NO_ROLE_TOKEN)
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();

        ResponseEntity<Map> response = noRole.get().uri("/api/customers").retrieve().toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-AUTH-FORBIDDEN");
    }

    @Test
    @DisplayName("garbage bearer token -> 401; actuator health stays public for anonymous callers")
    void malformedTokenRejectedHealthPublic() {
        RestClient badToken = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();

        assertThat(badToken.get().uri("/api/customers").retrieve().toEntity(Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Health is the single permit-path, aimed at ANONYMOUS orchestration probes
        // (compose healthchecks). A present-but-invalid bearer token still fails
        // authentication first — that is correct resource-server behaviour.
        RestClient anonymous = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();
        assertThat(anonymous.get().uri("/actuator/health").retrieve().toEntity(Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ---------------------------------------------------------------- schema

    @Test
    @DisplayName("customer_db has no local GNL_ST/GNL_TP tables and no cross-database FKs (ADR-002)")
    void schemaContainsNoLocalCatalogTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).map(String::toLowerCase)
                .doesNotContain("gnl_st", "gnl_tp")
                .contains("role", "city", "district", "party", "ind", "party_role", "cust", "addr", "cntc_medium");

        // Every FK must point at a table in this same schema — cross-database FKs are
        // impossible in PostgreSQL anyway, but this proves no FK targets catalog tables.
        List<String> fkTargets = jdbcTemplate.queryForList("""
                SELECT ccu.table_name FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY'
                """, String.class);
        assertThat(fkTargets).map(String::toLowerCase).doesNotContain("gnl_st", "gnl_tp");
    }

    @Test
    @DisplayName("Flyway seed is present: customers 1001/1002 active, 1003 soft-deleted")
    void seedDataLoaded() {
        assertThat(customerRepository.findByCustomerNumber(1001L)).hasValueSatisfying(c ->
                assertThat(c.isActive()).isTrue());
        assertThat(customerRepository.findByCustomerNumber(1003L)).hasValueSatisfying(c -> {
            assertThat(c.isActive()).isFalse();
            assertThat(c.getDeletedDate()).isNotNull();
        });
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("atomic create persists the full aggregate with a sequence-assigned customer number")
    void createPersistsFullAggregate() {
        ResponseEntity<Map> response = post("/api/customers", createBody("Çağla", "Öztürk", "40000000001"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number customerNumber = (Number) response.getBody().get("customerNumber");
        assertThat(customerNumber.longValue()).isGreaterThanOrEqualTo(1004L);
        // Turkish characters round-trip intact (VR-NAME).
        assertThat(response.getBody().get("firstName")).isEqualTo("Çağla");
        assertThat(response.getBody().get("lastName")).isEqualTo("Öztürk");
        assertThat(response.getBody().get("role")).isEqualTo("Customer");
        assertThat(response.getBody().get("status")).isEqualTo("ACTV");

        // Aggregate rows all exist and are active.
        var customer = customerRepository.findByCustomerNumber(customerNumber.longValue()).orElseThrow();
        Long partyId = customer.getPartyRole().getParty().getId();
        assertThat(addressRepository.findByPartyIdAndDeletedDateIsNullOrderById(partyId)).hasSize(1);
        assertThat(contactMediumRepository.findByPartyIdAndDeletedDateIsNull(partyId)).isPresent();
        // ADR-004: created_by carries the Keycloak sub of the authenticated operator.
        assertThat(customer.getCreatedBy()).isEqualTo(TestSecurity.OPERATOR_SUBJECT);
    }

    @Test
    @DisplayName("audit attribution: created/updated/deleted_by carry the token's sub, seeds stay 'system' (ADR-004)")
    void auditColumnsCarryKeycloakSubject() {
        // Seeded rows (Flyway) are attributed to the technical 'system' actor.
        var seeded = customerRepository.findByCustomerNumber(1001L).orElseThrow();
        assertThat(seeded.getCreatedBy()).isEqualTo("system");

        // Create + update + delete through the API as the authenticated operator.
        ResponseEntity<Map> created = post("/api/customers", createBody("Audit", "Izi", "40000000020"));
        long number = ((Number) created.getBody().get("customerNumber")).longValue();
        var customer = customerRepository.findByCustomerNumber(number).orElseThrow();
        assertThat(customer.getCreatedBy()).isEqualTo(TestSecurity.OPERATOR_SUBJECT);

        ResponseEntity<Map> updated = putJson("/api/customers/" + number, """
                {"firstName": "Audit", "lastName": "Izleri", "birthDate": "1992-03-15",
                 "gender": "Male", "nationalityId": "40000000020"}
                """);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        customer = customerRepository.findByCustomerNumber(number).orElseThrow();
        assertThat(customer.getUpdatedBy()).isEqualTo(TestSecurity.OPERATOR_SUBJECT);

        ResponseEntity<Map> deleted = http.method(HttpMethod.DELETE)
                .uri("/api/customers/" + number).retrieve().toEntity(Map.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        customer = customerRepository.findByCustomerNumber(number).orElseThrow();
        assertThat(customer.getDeletedBy()).isEqualTo(TestSecurity.OPERATOR_SUBJECT);
    }

    @Test
    @DisplayName("MERNIS rejection -> 400 MSG-CUST-NATID-VERIFICATION-FAILED and ZERO rows persisted (KR-10)")
    void mernisRejectionLeavesNoPartialData() {
        Mockito.when(mernisClient.verify(Mockito.eq("40000000002"), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(false);
        long partiesBefore = partyRepository.count();
        long individualsBefore = individualRepository.count();

        ResponseEntity<Map> response = post("/api/customers", createBody("Deniz", "Kaya", "40000000002"));

        // AC-CUST-03-06 (v8 Final analyst catalog key).
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-CUST-NATID-VERIFICATION-FAILED");
        assertThat(partyRepository.count()).isEqualTo(partiesBefore);
        assertThat(individualRepository.count()).isEqualTo(individualsBefore);
    }

    @Test
    @DisplayName("MERNIS unavailable -> 503 MSG-MERNIS-UNAVAILABLE fail-closed, ZERO rows persisted (KR-10)")
    void mernisUnavailableFailsClosed() {
        Mockito.when(mernisClient.verify(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenThrow(new com.crm.customer.mernis.MernisUnavailableException("down", new RuntimeException()));
        long partiesBefore = partyRepository.count();

        ResponseEntity<Map> response = post("/api/customers", createBody("Emre", "Şahin", "40000000003"));

        // AC-CUST-03-06 (v8 Final analyst catalog key, MERNIS-specific).
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-MERNIS-UNAVAILABLE");
        assertThat(partyRepository.count()).isEqualTo(partiesBefore);
    }

    @Test
    @DisplayName("shared catalog unavailable -> 503 fail-closed, ZERO rows persisted (ADR-002)")
    void catalogUnavailableFailsClosed() {
        Mockito.when(lookupCatalogClient.fetchStatus(Mockito.anyString()))
                .thenThrow(new LookupCatalogUnavailableException("down", new RuntimeException()));
        Mockito.when(lookupCatalogClient.fetchType(Mockito.anyString()))
                .thenThrow(new LookupCatalogUnavailableException("down", new RuntimeException()));
        long partiesBefore = partyRepository.count();

        ResponseEntity<Map> response = post("/api/customers", createBody("Aylin", "Demir", "40000000004"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");
        assertThat(partyRepository.count()).isEqualTo(partiesBefore);
    }

    @Test
    @DisplayName("unknown catalog code -> field-level 400, ZERO rows persisted (ADR-002)")
    void unknownCatalogCodeRejected() {
        Mockito.when(lookupCatalogClient.fetchType("MALE")).thenReturn(Optional.empty());
        long partiesBefore = partyRepository.count();

        ResponseEntity<Map> response = post("/api/customers", createBody("Kerem", "Aksoy", "40000000005"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> validationErrors = (Map<String, Object>) response.getBody().get("validationErrors");
        assertThat(validationErrors).containsKey("gender");
        assertThat(partyRepository.count()).isEqualTo(partiesBefore);
    }

    @Test
    @DisplayName("Nationality ID held by a SOFT-DELETED customer is still blocked with 409 (ADR-003)")
    void nationalityIdOfSoftDeletedCustomerStaysReserved() {
        // 34567890123 belongs to seeded customer 1003 (Caner Sahin, soft-deleted).
        ResponseEntity<Map> response = post("/api/customers", createBody("Yeni", "Kisi", "34567890123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-CUST-DUP-NATID");
    }

    @Test
    @DisplayName("availability probe reports the ADR-003 rule, soft-deleted holders included")
    void nationalityIdAvailabilityCoversSoftDeletedHolders() {
        // The reason this endpoint exists: 34567890123 belongs to soft-deleted customer
        // 1003, so the list endpoint cannot see it (active-only) while create still
        // rejects it. The probe must agree with create, not with the list.
        assertThat(searchResultNumbers("nationalityId=34567890123")).isEmpty();

        ResponseEntity<Map> softDeleted = get("/api/customers/nationality-id-availability?nationalityId=34567890123");
        assertThat(softDeleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(softDeleted.getBody().get("available")).isEqualTo(false);

        ResponseEntity<Map> activeHolder = get("/api/customers/nationality-id-availability?nationalityId=12345678901");
        assertThat(activeHolder.getBody().get("available")).isEqualTo(false);

        ResponseEntity<Map> free = get("/api/customers/nationality-id-availability?nationalityId=99988877766");
        assertThat(free.getBody().get("available")).isEqualTo(true);

        // Yes/no only: never leaks WHO holds the id (a soft-deleted person included).
        assertThat(softDeleted.getBody().keySet()).containsExactly("available");
    }

    @Test
    @DisplayName("availability probe: numeric-only guard, and it does not shadow /{customerNumber}")
    void nationalityIdAvailabilityDoesNotShadowDetail() {
        ResponseEntity<Map> nonNumeric = get("/api/customers/nationality-id-availability?nationalityId=abc");
        assertThat(nonNumeric.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> missing = get("/api/customers/nationality-id-availability");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // The literal segment wins over the path variable; detail keeps working.
        ResponseEntity<Map> detail = get("/api/customers/1001");
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().get("customerNumber")).isEqualTo(1001);
    }

    @Test
    @DisplayName("multiple primary addresses in one create request -> 400")
    void multiplePrimaryAddressesRejected() {
        String body = """
                {
                  "demographic": {"firstName": "Ada", "lastName": "Toprak", "birthDate": "1990-01-01",
                                  "gender": "Female", "nationalityId": "40000000006"},
                  "addresses": [
                    {"cityId": 1, "districtId": 1, "street": "A", "houseFlatNumber": "1",
                     "addressDescription": "d", "primary": true},
                    {"cityId": 1, "districtId": 2, "street": "B", "houseFlatNumber": "2",
                     "addressDescription": "d", "primary": true}
                  ],
                  "contactMedium": {"email": "ada@example.com", "mobilePhone": "05321110000"}
                }
                """;
        ResponseEntity<Map> response = post("/api/customers", body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("district of another city -> 400 (cascading rule), ZERO rows persisted")
    void districtMustBelongToCity() {
        long partiesBefore = partyRepository.count();
        String body = createBody("Bora", "Aydin", "40000000007")
                .replace("\"districtId\": 1", "\"districtId\": 3"); // Cankaya belongs to Ankara, not Istanbul

        ResponseEntity<Map> response = post("/api/customers", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(partyRepository.count()).isEqualTo(partiesBefore);
    }

    // ---------------------------------------------------------------- search

    @Test
    @DisplayName("KR-01: firstName matches word-start over First+Middle; substrings do not match")
    void searchWordStartSemantics() {
        // Seed: Ali Yildiz (1001), Zeynep Nur Demir (1002), Caner Sahin (1003, deleted).
        // Migrated from the old CustomerSpecificationsTest: prefix yes / contains no,
        // middle-name matching — now with the stronger word-start semantics.
        assertThat(searchResultNumbers("firstName=Ali")).contains(1001L);
        assertThat(searchResultNumbers("firstName=Zey")).contains(1002L);
        assertThat(searchResultNumbers("firstName=Nur")).contains(1002L);   // middle name, word-start
        assertThat(searchResultNumbers("firstName=li")).isEmpty();          // substring of "Ali"
        assertThat(searchResultNumbers("firstName=ur")).isEmpty();          // substring of "Nur"
        assertThat(searchResultNumbers("firstName=zeynep&lastName=De")).contains(1002L); // AND within name criterion
        assertThat(searchResultNumbers("firstName=Zeynep&lastName=Yil")).isEmpty();      // AND: wrong last name
        assertThat(searchResultNumbers("firstName=Caner")).isEmpty();       // soft-deleted stays invisible
    }

    @Test
    @DisplayName("KR-01: GSM prefix search resolves to the owning customer")
    void searchByGsmPrefix() {
        assertThat(searchResultNumbers("gsmNumber=05321")).contains(1001L);
        assertThat(searchResultNumbers("gsmNumber=0533")).contains(1002L).doesNotContain(1001L);
    }

    @Test
    @DisplayName("exact criteria: nationalityId and customerId (business number)")
    void searchByExactCriteria() {
        assertThat(searchResultNumbers("nationalityId=12345678901")).containsExactly(1001L);
        assertThat(searchResultNumbers("customerId=1002")).containsExactly(1002L);
        assertThat(searchResultNumbers("customerId=1003")).isEmpty(); // deleted
        // OR across criterion groups: name matches 1001, gsm matches 1002.
        assertThat(searchResultNumbers("firstName=Ali&gsmNumber=0533")).contains(1001L, 1002L);
    }

    @Test
    @DisplayName("V3 fixture expansion: shared last name, middle-name search, GSM grouping, soft-delete stays invisible")
    void v3FixtureSearchCoverage() {
        // Mehmet Yilmaz (1005) and Elif Yilmaz (1006) share a last name.
        assertThat(searchResultNumbers("lastName=Yilmaz")).contains(1005L, 1006L);

        // Ali Kemal Ozturk (1007): middle-name word-start search.
        assertThat(searchResultNumbers("firstName=Kemal")).contains(1007L);

        // "Ali" matches both a first name (1001) and a middle name (1011, Kerem Ali Toprak).
        assertThat(searchResultNumbers("firstName=Ali")).contains(1001L, 1011L);

        // GSM prefix grouping (docs/testing/seed-fixture-catalog.md): 0532 groups
        // 1001, 1007 and 1011; 0533 groups 1002 and 1006. Not containsExactly: other
        // tests in this class create additional customers via createBody(), whose
        // default fixture mobile (05329998877) also starts with 0532, and this test
        // class shares one live database with no per-test rollback.
        assertThat(searchResultNumbers("gsmNumber=0532")).contains(1001L, 1007L, 1011L);
        assertThat(searchResultNumbers("gsmNumber=0533")).contains(1002L, 1006L);

        // Fatma Nur Sahin (1008) shares a last name with the soft-deleted Caner Sahin
        // (1003) — the deleted row must stay invisible even under a shared surname.
        assertThat(searchResultNumbers("lastName=Sahin")).containsExactly(1008L);
    }

    @Test
    @DisplayName("V3 fixture expansion: every new party has exactly one active primary address")
    void v3FixtureAddressInvariant() {
        // 1004 (1 address), 1006/1008/1009/1010/1011 (1 address each) and the
        // multi-address customers 1005 (2) / 1007 (3) all keep exactly one primary.
        for (long partyId : new long[] {4, 5, 6, 7, 8, 9, 10, 11}) {
            long primaryCount = addressRepository.findByPartyIdAndDeletedDateIsNullOrderById(partyId).stream()
                    .filter(a -> Boolean.TRUE.equals(a.isPrimary()))
                    .count();
            assertThat(primaryCount).as("party %d has exactly one primary address", partyId).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("ADR-005: GET /api/customers with no criteria browses ALL active customers, A-Z")
    @SuppressWarnings("unchecked")
    void browseWithoutCriteriaListsAllActiveCustomers() {
        ResponseEntity<Map> response = get("/api/customers");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        List<Long> numbers = content.stream()
                .map(row -> ((Number) row.get("customerNumber")).longValue()).toList();

        // All active seed customers present; soft-deleted 1003 excluded (AC-CUST-01-08).
        assertThat(numbers).contains(1001L, 1002L).doesNotContain(1003L);
        assertThat(((Number) response.getBody().get("totalElements")).longValue())
                .isEqualTo(numbers.size()); // full active list is browsable, still paginated

        // A-Z ordering (AC-CUST-01-00 / KR-04): firstName ASC, then lastName ASC.
        List<String> sortKeys = content.stream()
                .map(row -> row.get("firstName") + " " + row.get("lastName"))
                .toList();
        assertThat(sortKeys).isSorted();
    }

    @Test
    @DisplayName("ADR-005: every list row carries the FULL CustomerDetailResponse contract")
    @SuppressWarnings("unchecked")
    void listRowsExposeFullDetailContract() {
        ResponseEntity<Map> response = get("/api/customers?customerId=1001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        Map<String, Object> row = content.get(0);

        // Exact contract equivalence with GET /api/customers/{customerNumber}.
        assertThat(row.keySet()).containsExactlyInAnyOrder(
                "customerNumber", "firstName", "middleName", "lastName", "fatherName",
                "motherName", "birthDate", "gender", "nationalityId", "role", "status");
        assertThat(row.get("customerNumber")).isEqualTo(1001);
        assertThat(row.get("firstName")).isEqualTo("Ali");
        assertThat(row.get("gender")).isEqualTo("Male");
        assertThat(row.get("nationalityId")).isEqualTo("12345678901");
        assertThat(row.get("role")).isEqualTo("Customer");
        assertThat(row.get("status")).isEqualTo("ACTV");
        assertThat(row.get("birthDate")).isEqualTo("1990-05-14");

        // Same shape as the singular detail endpoint (which is unchanged).
        ResponseEntity<Map> detail = get("/api/customers/1001");
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) detail.getBody()).keySet()).isEqualTo(row.keySet());
    }

    @Test
    @DisplayName("KR-04: browse stays server-side paginated; default size 15, 15/30/50 accepted")
    @SuppressWarnings("unchecked")
    void browseIsPaginated() {
        // Default page size is 15 (KR-04; ADR-005 §Amendment 2026-07-29 withdrew the old 20).
        ResponseEntity<Map> defaults = get("/api/customers");
        assertThat(defaults.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) defaults.getBody().get("size")).intValue()).isEqualTo(15);
        assertThat(((Number) defaults.getBody().get("number")).intValue()).isEqualTo(0);

        long totalElements = ((Number) defaults.getBody().get("totalElements")).longValue();
        assertThat(totalElements).isGreaterThanOrEqualTo(2); // at least seed 1001 + 1002

        // Every whitelisted size is applied verbatim and echoed back.
        for (int size : new int[] {15, 30, 50}) {
            ResponseEntity<Map> page = get("/api/customers?page=0&size=" + size);
            assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(((Number) page.getBody().get("size")).intValue()).isEqualTo(size);
            List<Map<String, Object>> content = (List<Map<String, Object>>) page.getBody().get("content");
            assertThat(content).hasSize((int) Math.min(totalElements, size));
        }

        // A page past the end stays a normal empty 200 (Spring Data semantics); KR-04
        // whitelists page SIZE only, so `page` deliberately has no upper bound.
        ResponseEntity<Map> beyond = get("/api/customers?page=500&size=15");
        assertThat(beyond.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) beyond.getBody().get("content")).isEmpty();
    }

    @Test
    @DisplayName("KR-04: non-whitelisted, excessive, zero size and negative page -> 400, never 500")
    @SuppressWarnings("unchecked")
    void rejectsInvalidPaginationParameters() {
        // size=17 (not whitelisted), size=999999 (excessive) and size=0 (previously a 500
        // out of PageRequest.of) all fail the same whitelist rule.
        for (String size : new String[] {"17", "999999", "0"}) {
            ResponseEntity<Map> response = get("/api/customers?size=" + size);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");
            Map<String, Object> errors = (Map<String, Object>) response.getBody().get("validationErrors");
            assertThat(errors).containsEntry("size", "must be one of 15, 30, 50");
        }

        // page=-1 was the other PageRequest.of 500.
        ResponseEntity<Map> negativePage = get("/api/customers?page=-1");

        assertThat(negativePage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(negativePage.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");
        Map<String, Object> pageErrors = (Map<String, Object>) negativePage.getBody().get("validationErrors");
        assertThat(pageErrors).containsEntry("page", "must not be negative");
    }

    @Test
    @DisplayName("legacy /search alias is gone (ADR-005) — GET /api/customers is the only list endpoint")
    void removedSearchAlias() {
        ResponseEntity<Map> legacy = get("/api/customers/search?firstName=Ali");
        // "/search" now binds to {customerNumber} and fails Long conversion -> clean 400,
        // proving the alias route no longer exists as an endpoint.
        assertThat(legacy.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------- KR-02 child-record search criteria
    // Account Number is owned by account-service (cust_acct), Order Number by
    // order-service (cust_ord). customer-service resolves each number to its owning
    // customer through that service's public API and folds the result into the same
    // KR-01 OR expression; no cross-database read, join or table copy exists.

    private static final String ACCOUNT_OF_1001 = "1261000010";
    private static final String ORDER_OF_1002 = "1262000018";

    private void stubActiveAccount(String accountNumber, long ownerCustomerNumber) {
        Mockito.when(accountServiceClient.fetchAccount(accountNumber))
                .thenReturn(Optional.of(new AccountSummary(accountNumber, ownerCustomerNumber, "Active", null)));
    }

    private void stubInProgressOrder(String orderNumber, long ownerCustomerNumber) {
        Mockito.when(orderServiceClient.fetchOrder(orderNumber))
                .thenReturn(Optional.of(new OrderSummary(orderNumber, ownerCustomerNumber, "MIDLWARE")));
    }

    @Test
    @DisplayName("KR-02: an existing ACTIVE Account Number returns exactly its owning active customer")
    void searchByAccountNumberReturnsOwningCustomer() {
        stubActiveAccount(ACCOUNT_OF_1001, 1001L);

        assertThat(searchResultNumbers("accountNumber=" + ACCOUNT_OF_1001)).containsExactly(1001L);
    }

    @Test
    @DisplayName("KR-02: an existing in-progress Order Number returns exactly its owning active customer")
    void searchByOrderNumberReturnsOwningCustomer() {
        stubInProgressOrder(ORDER_OF_1002, 1002L);

        assertThat(searchResultNumbers("orderNumber=" + ORDER_OF_1002)).containsExactly(1002L);
    }

    @Test
    @DisplayName("KR-02: an unknown Account/Order Number matches NOTHING — it never falls back to browse")
    @SuppressWarnings("unchecked")
    void unknownChildRecordNumberMatchesNothing() {
        // Both clients answer Optional.empty() by default (see setUp), i.e. no visible
        // record — which for accountNumber also covers the K-8 223 Customer Account,
        // deliberately indistinguishable from unknown (ADR-013 §4.5).
        assertThat(searchResultNumbers("accountNumber=9999999999")).isEmpty();
        assertThat(searchResultNumbers("orderNumber=9999999999")).isEmpty();

        // The regression this guards: a filled-but-unresolved criterion must NOT be
        // dropped. Dropping it would leave zero criteria and silently return the whole
        // active list (ADR-005 browse mode) instead of "not found".
        ResponseEntity<Map> response = get("/api/customers?accountNumber=9999999999");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) response.getBody().get("totalElements")).longValue()).isZero();
        assertThat((List<Map<String, Object>>) response.getBody().get("content")).isEmpty();
    }

    @Test
    @DisplayName("KR-02: a PASSIVE account and a CANCELLED order produce no customer result")
    void inactiveChildRecordsProduceNoResult() {
        // FR-ACCT-04: delete = passivation, so "Passive" IS account-service's soft
        // delete; a passivated billing account must not resolve to its owner.
        Mockito.when(accountServiceClient.fetchAccount("1261000028"))
                .thenReturn(Optional.of(new AccountSummary("1261000028", 1001L, "Passive", null)));
        assertThat(searchResultNumbers("accountNumber=1261000028")).isEmpty();

        // ADR-016 §6: CANCELLED is a compensated (rolled-back) sale, not a live order.
        Mockito.when(orderServiceClient.fetchOrder("1262000026"))
                .thenReturn(Optional.of(new OrderSummary("1262000026", 1002L, "CANCELLED")));
        assertThat(searchResultNumbers("orderNumber=1262000026")).isEmpty();
    }

    @Test
    @DisplayName("KR-02 + AC-CUST-01-08: a child record of a SOFT-DELETED customer produces no result")
    void childRecordOfDeletedCustomerProducesNoResult() {
        // 1003 (Caner Sahin) is soft-deleted in the Flyway seed. The local active-only
        // predicate is applied on top of the resolved owner, so an account/order that
        // legitimately still exists in the other domain cannot resurrect the customer.
        stubActiveAccount("1261000036", 1003L);
        stubInProgressOrder("1262000034", 1003L);

        assertThat(searchResultNumbers("accountNumber=1261000036")).isEmpty();
        assertThat(searchResultNumbers("orderNumber=1262000034")).isEmpty();
    }

    @Test
    @DisplayName("AC-CUST-01-04: several matching child records of one customer yield ONE row; totals count customers")
    @SuppressWarnings("unchecked")
    void duplicateChildRecordMatchesYieldOneCustomerRow() {
        // The same customer reached twice: once through its billing account, once
        // through its order. Exact matches on globally unique numbers resolve to one
        // customer number each, so the OR collapses to a single predicate value and no
        // join fan-out is possible.
        stubActiveAccount(ACCOUNT_OF_1001, 1001L);
        stubInProgressOrder("1262000042", 1001L);

        ResponseEntity<Map> response =
                get("/api/customers?accountNumber=" + ACCOUNT_OF_1001 + "&orderNumber=1262000042");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(((Number) content.get(0).get("customerNumber")).longValue()).isEqualTo(1001L);
        // Page metadata describes DISTINCT CUSTOMERS, not joined child rows.
        assertThat(((Number) response.getBody().get("totalElements")).longValue()).isEqualTo(1L);
        assertThat(((Number) response.getBody().get("totalPages")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("KR-01: the number criteria join the SAME OR expression as name/GSM, and keep the A-Z sort")
    void childRecordCriteriaJoinTheExistingOrExpression() {
        stubActiveAccount(ACCOUNT_OF_1001, 1001L);   // Ali Yildiz
        stubInProgressOrder(ORDER_OF_1002, 1002L);   // Zeynep Nur Demir

        // accountNumber OR orderNumber
        assertThat(searchResultNumbers("accountNumber=" + ACCOUNT_OF_1001 + "&orderNumber=" + ORDER_OF_1002))
                .containsExactly(1001L, 1002L);   // firstName ASC: Ali before Zeynep
        // accountNumber OR name — the number criterion does not narrow the name group.
        assertThat(searchResultNumbers("accountNumber=" + ACCOUNT_OF_1001 + "&firstName=Zeynep"))
                .containsExactly(1001L, 1002L);
        // A name criterion whose owner also matches by GSM still returns one row.
        assertThat(searchResultNumbers("orderNumber=" + ORDER_OF_1002 + "&gsmNumber=0533"))
                .contains(1002L);
    }

    @Test
    @DisplayName("AC-CUST-01-07: non-numeric Account/Order Number -> 400, and the owning service is never called")
    @SuppressWarnings("unchecked")
    void nonNumericChildRecordNumbersAreRejectedByTheBackend() {
        for (String criterion : new String[] {"accountNumber", "orderNumber"}) {
            ResponseEntity<Map> response = get("/api/customers?" + criterion + "=12AB");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");
            Map<String, Object> errors = (Map<String, Object>) response.getBody().get("validationErrors");
            assertThat(errors).containsEntry(criterion, "must contain digits only");
        }

        // The controller rejects before the service layer runs: no outbound call is made
        // for a malformed number (the backend does not trust, or depend on, the client's
        // own digits-only input hygiene).
        Mockito.verify(accountServiceClient, Mockito.never()).fetchAccount(Mockito.anyString());
        Mockito.verify(orderServiceClient, Mockito.never()).fetchOrder(Mockito.anyString());
    }

    @Test
    @DisplayName("KR-02 fail-closed: an owning service outage -> 503 MSG-SERVICE-UNAVAILABLE, never an empty page")
    void childRecordOwnerOutageFailsClosed() {
        Mockito.when(accountServiceClient.fetchAccount("1261000051"))
                .thenThrow(new AccountServiceUnavailableException("account-service is unavailable", null));
        ResponseEntity<Map> account = get("/api/customers?accountNumber=1261000051");
        assertThat(account.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(account.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");

        Mockito.when(orderServiceClient.fetchOrder("1262000059"))
                .thenThrow(new OrderServiceUnavailableException("order-service is unavailable", null));
        ResponseEntity<Map> order = get("/api/customers?orderNumber=1262000059");
        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(order.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");
    }

    @Test
    @DisplayName("KR-02: leading zeros survive — the number is carried as a STRING end to end")
    void childRecordNumbersKeepLeadingZeros() {
        stubActiveAccount("0261000010", 1001L);

        assertThat(searchResultNumbers("accountNumber=0261000010")).containsExactly(1001L);
        // The trimmed-off form is a DIFFERENT number and must not match.
        assertThat(searchResultNumbers("accountNumber=261000010")).isEmpty();
        Mockito.verify(accountServiceClient).fetchAccount("0261000010");
    }

    @Test
    @DisplayName("absent Account/Order Numbers cost nothing: browse and the other criteria make NO outbound call")
    void absentChildRecordCriteriaMakeNoOutboundCall() {
        // ADR-005 browse mode and every pre-existing criterion are untouched: the two
        // owning services are contacted only when their field is actually filled, so
        // the common list request keeps its single local query.
        assertThat(searchResultNumbers("")).contains(1001L, 1002L);
        assertThat(searchResultNumbers("firstName=Ali")).contains(1001L);
        assertThat(searchResultNumbers("customerId=1002")).containsExactly(1002L);

        Mockito.verify(accountServiceClient, Mockito.never()).fetchAccount(Mockito.anyString());
        Mockito.verify(orderServiceClient, Mockito.never()).fetchOrder(Mockito.anyString());
    }

    @SuppressWarnings("unchecked")
    private List<Long> searchResultNumbers(String query) {
        ResponseEntity<Map> response = get("/api/customers?" + query);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        // ADR-005: list rows use the detail contract — the business id field is
        // customerNumber (the old CustomerSearchResponse.customerId field is gone).
        return content.stream().map(row -> ((Number) row.get("customerNumber")).longValue()).toList();
    }

    // ------------------------------------------------------- delete + addresses

    @Test
    @DisplayName("soft delete passivates the whole aggregate with full audit metadata")
    void softDeletePassivatesAggregate() {
        // Create a dedicated customer so other tests are unaffected.
        ResponseEntity<Map> created = post("/api/customers", createBody("Silinecek", "Musteri", "40000000010"));
        long number = ((Number) created.getBody().get("customerNumber")).longValue();

        ResponseEntity<Map> deleted = http.method(HttpMethod.DELETE)
                .uri("/api/customers/" + number).retrieve().toEntity(Map.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var customer = customerRepository.findByCustomerNumber(number).orElseThrow();
        assertThat(customer.getStatusId()).isEqualTo(2L);
        assertThat(customer.getDeletedDate()).isNotNull();
        // ADR-004: deleted_by carries the Keycloak sub of the authenticated operator.
        assertThat(customer.getDeletedBy()).isEqualTo(TestSecurity.OPERATOR_SUBJECT);
        assertThat(customer.getUpdatedDate()).isNotNull();

        Long partyId = customer.getPartyRole().getParty().getId();
        assertThat(addressRepository.findByPartyIdAndDeletedDateIsNullOrderById(partyId)).isEmpty();
        assertThat(contactMediumRepository.findByPartyIdAndDeletedDateIsNull(partyId)).isEmpty();

        // Invisible everywhere afterwards.
        assertThat(get("/api/customers/" + number).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(searchResultNumbers("customerId=" + number)).isEmpty();
    }

    @Test
    @DisplayName("AC-CUST-05-04: soft delete passivates only the customer's Active billing accounts")
    void softDeletePassivatesActiveAccountsOnly() {
        ResponseEntity<Map> created = post("/api/customers", createBody("Hesap", "Sahibi", "40000000030"));
        long number = ((Number) created.getBody().get("customerNumber")).longValue();

        Mockito.when(accountServiceClient.listAccounts(number)).thenReturn(List.of(
                new AccountSummary("1261000010", number, "Active", null),
                new AccountSummary("1261000028", number, "Passive", null)));

        ResponseEntity<Map> deleted = http.method(HttpMethod.DELETE)
                .uri("/api/customers/" + number).retrieve().toEntity(Map.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Only the Active account is passivated; the already-Passive one is left alone
        // (DELETE /api/accounts/{accountNumber} is not idempotent — see HttpAccountServiceClient).
        Mockito.verify(accountServiceClient).passivateAccount("1261000010");
        Mockito.verify(accountServiceClient, Mockito.never()).passivateAccount("1261000028");
    }

    @Test
    @DisplayName("AC-CUST-05-03: account-service reports active products on the account -> 409 MSG-CUST-HAS-PRODUCTS")
    void softDeleteFailsWhenAccountStillHasActiveProducts() {
        ResponseEntity<Map> created = post("/api/customers", createBody("Urunlu", "Hesap", "40000000032"));
        long number = ((Number) created.getBody().get("customerNumber")).longValue();

        Mockito.when(accountServiceClient.listAccounts(number))
                .thenReturn(List.of(new AccountSummary("1261000051", number, "Active", null)));
        Mockito.doThrow(new AccountHasActiveProductsException("1261000051"))
                .when(accountServiceClient).passivateAccount("1261000051");

        ResponseEntity<Map> deleted = http.method(HttpMethod.DELETE)
                .uri("/api/customers/" + number).retrieve().toEntity(Map.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(deleted.getBody().get("messageKey")).isEqualTo("MSG-CUST-HAS-PRODUCTS");

        var customer = customerRepository.findByCustomerNumber(number).orElseThrow();
        assertThat(customer.getStatusId()).isEqualTo(1L);
        assertThat(customer.getDeletedDate()).isNull();
    }

    @Test
    @DisplayName("AC-CUST-05-04: account-service unavailable -> 503, customer left untouched")
    void softDeleteFailsClosedWhenAccountServiceUnavailable() {
        ResponseEntity<Map> created = post("/api/customers", createBody("Erisilemeyen", "Hesap", "40000000031"));
        long number = ((Number) created.getBody().get("customerNumber")).longValue();

        Mockito.when(accountServiceClient.listAccounts(number))
                .thenThrow(new AccountServiceUnavailableException("account-service is unavailable", null));

        ResponseEntity<Map> deleted = http.method(HttpMethod.DELETE)
                .uri("/api/customers/" + number).retrieve().toEntity(Map.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(deleted.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");

        // Nothing local was touched: the account-service call runs before any local
        // passivation, so a failure there must leave the customer fully Active.
        var customer = customerRepository.findByCustomerNumber(number).orElseThrow();
        assertThat(customer.getStatusId()).isEqualTo(1L);
        assertThat(customer.getDeletedDate()).isNull();
    }

    @Test
    @DisplayName("FR-ADDR rules: first address primary, set-primary switches, last/primary delete guards")
    void addressPrimaryInvariants() {
        ResponseEntity<Map> created = post("/api/customers", createBody("Adres", "Testi", "40000000011"));
        long number = ((Number) created.getBody().get("customerNumber")).longValue();
        String base = "/api/customers/" + number + "/addresses";

        // The single (first) address is primary; deleting it is blocked (AC-ADDR-04-01).
        List<Map<String, Object>> addresses = listAddresses(base);
        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0).get("primary")).isEqualTo(true);
        long firstId = ((Number) addresses.get(0).get("addressId")).longValue();
        ResponseEntity<Map> delLast = http.method(HttpMethod.DELETE).uri(base + "/" + firstId)
                .retrieve().toEntity(Map.class);
        assertThat(delLast.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(delLast.getBody().get("messageKey")).isEqualTo("MSG-ADDR-LAST-DELETE");

        // Add a second, non-primary address.
        ResponseEntity<Map> second = post(base, """
                {"cityId": 2, "districtId": 3, "street": "Ikinci", "houseFlatNumber": "2",
                 "addressDescription": "is", "primary": false}
                """);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long secondId = ((Number) second.getBody().get("addressId")).longValue();

        // Primary address still cannot be deleted while another exists (AC-ADDR-04-02).
        ResponseEntity<Map> delPrimary = http.method(HttpMethod.DELETE).uri(base + "/" + firstId)
                .retrieve().toEntity(Map.class);
        assertThat(delPrimary.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(delPrimary.getBody().get("messageKey")).isEqualTo("MSG-ADDR-PRIMARY-DELETE");

        // Switch primary (AC-ADDR-05-02): exactly one primary remains.
        ResponseEntity<Map> patched = http.method(HttpMethod.PATCH)
                .uri(base + "/" + secondId + "/primary").retrieve().toEntity(Map.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> after = listAddresses(base);
        assertThat(after.stream().filter(a -> Boolean.TRUE.equals(a.get("primary")))).hasSize(1);

        // The demoted address is now deletable (AC-ADDR-04-03).
        ResponseEntity<Map> delOld = http.method(HttpMethod.DELETE).uri(base + "/" + firstId)
                .retrieve().toEntity(Map.class);
        assertThat(delOld.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listAddresses(base)).hasSize(1);
    }

    // ------------------------------------ AC-ADDR-04-04 / BUG-API-ADDR-04-03: address in-use

    @Test
    @DisplayName("AC-ADDR-04-04 / BUG-API-ADDR-04-03: an Active Billing Account on this address blocks delete -> 409 MSG-ADDR-IN-USE")
    void deleteBlockedByActiveBillingAccountOnThisAddress() {
        ResponseEntity<Map> created = post("/api/customers", createBody("Faturali", "Musteri", "40000000040"));
        long number = ((Number) created.getBody().get("customerNumber")).longValue();
        String base = "/api/customers/" + number + "/addresses";

        ResponseEntity<Map> second = post(base, """
                {"cityId": 2, "districtId": 3, "street": "Ikinci", "houseFlatNumber": "2",
                 "addressDescription": "is", "primary": false}
                """);
        long secondId = ((Number) second.getBody().get("addressId")).longValue();

        Mockito.when(accountServiceClient.listAccounts(number))
                .thenReturn(List.of(new AccountSummary("1261000060", number, "Active", secondId)));

        ResponseEntity<Map> delete = http.method(HttpMethod.DELETE).uri(base + "/" + secondId)
                .retrieve().toEntity(Map.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(delete.getBody().get("messageKey")).isEqualTo("MSG-ADDR-IN-USE");

        var address = addressRepository.findById(secondId).orElseThrow();
        assertThat(address.isActive()).isTrue();
        assertThat(address.getDeletedDate()).isNull();
        assertThat(listAddresses(base)).extracting(a -> ((Number) a.get("addressId")).longValue())
                .contains(secondId);

        deleteTestCustomer(number);
    }

    @Test
    @DisplayName("AC-ADDR-04-04: a Passive Billing Account on this address does not block delete")
    void deleteAllowedWhenBillingAccountOnThisAddressIsPassive() {
        ResponseEntity<Map> created = post("/api/customers", createBody("Pasif", "Hesapli", "40000000041"));
        long number = ((Number) created.getBody().get("customerNumber")).longValue();
        String base = "/api/customers/" + number + "/addresses";

        ResponseEntity<Map> second = post(base, """
                {"cityId": 2, "districtId": 3, "street": "Ikinci", "houseFlatNumber": "2",
                 "addressDescription": "is", "primary": false}
                """);
        long secondId = ((Number) second.getBody().get("addressId")).longValue();

        Mockito.when(accountServiceClient.listAccounts(number))
                .thenReturn(List.of(new AccountSummary("1261000061", number, "Passive", secondId)));

        ResponseEntity<Map> delete = http.method(HttpMethod.DELETE).uri(base + "/" + secondId)
                .retrieve().toEntity(Map.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        deleteTestCustomer(number);
    }

    @Test
    @DisplayName("AC-ADDR-04-04: an Active Billing Account on a DIFFERENT address does not block delete")
    void deleteAllowedWhenActiveBillingAccountReferencesAnotherAddress() {
        ResponseEntity<Map> created = post("/api/customers", createBody("Baska", "Adresli", "40000000042"));
        long number = ((Number) created.getBody().get("customerNumber")).longValue();
        String base = "/api/customers/" + number + "/addresses";

        ResponseEntity<Map> second = post(base, """
                {"cityId": 2, "districtId": 3, "street": "Ikinci", "houseFlatNumber": "2",
                 "addressDescription": "is", "primary": false}
                """);
        long secondId = ((Number) second.getBody().get("addressId")).longValue();
        long firstId = listAddresses(base).stream()
                .map(a -> ((Number) a.get("addressId")).longValue())
                .filter(id -> id != secondId).findFirst().orElseThrow();

        // The Active account bills to the customer's OTHER (primary) address, not the one being deleted.
        Mockito.when(accountServiceClient.listAccounts(number))
                .thenReturn(List.of(new AccountSummary("1261000062", number, "Active", firstId)));

        ResponseEntity<Map> delete = http.method(HttpMethod.DELETE).uri(base + "/" + secondId)
                .retrieve().toEntity(Map.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        deleteTestCustomer(number);
    }

    @Test
    @DisplayName("AC-ADDR-04-04: account-service unavailable during the in-use check -> 503, address left unchanged")
    void deleteFailsClosedWhenAccountServiceUnavailableDuringInUseCheck() {
        ResponseEntity<Map> created = post("/api/customers", createBody("Erisilemez", "Adres", "40000000043"));
        long number = ((Number) created.getBody().get("customerNumber")).longValue();
        String base = "/api/customers/" + number + "/addresses";

        ResponseEntity<Map> second = post(base, """
                {"cityId": 2, "districtId": 3, "street": "Ikinci", "houseFlatNumber": "2",
                 "addressDescription": "is", "primary": false}
                """);
        long secondId = ((Number) second.getBody().get("addressId")).longValue();

        Mockito.when(accountServiceClient.listAccounts(number))
                .thenThrow(new AccountServiceUnavailableException("account-service is unavailable", null));

        ResponseEntity<Map> delete = http.method(HttpMethod.DELETE).uri(base + "/" + secondId)
                .retrieve().toEntity(Map.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(delete.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");

        var address = addressRepository.findById(secondId).orElseThrow();
        assertThat(address.isActive()).isTrue();
        assertThat(address.getDeletedDate()).isNull();

        // Cleanup: the outage was specific to the in-use check above, not a lasting
        // account-service outage — restore normal behaviour so cleanup can complete.
        // doReturn (not when/thenReturn) because the mock is currently stubbed to
        // THROW on this exact call: when(...) would re-invoke it and throw immediately.
        Mockito.doReturn(List.of()).when(accountServiceClient).listAccounts(number);
        deleteTestCustomer(number);
    }

    @Test
    @DisplayName("FR-CNTC-02: contact medium update validates formats and updates in place")
    void contactMediumUpdate() {
        String base = "/api/customers/1001/contact-medium";

        ResponseEntity<Map> current = get(base);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(current.getBody().get("mobilePhone")).isEqualTo("05321112233");

        // Invalid mobile (VR-MOBILE) -> 400 MSG-VAL-PHONE.
        ResponseEntity<Map> bad = putJson(base, """
                {"email": "ali.yildiz@example.com", "mobilePhone": "02121112233"}
                """);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> ok = putJson(base, """
                {"email": "ali.yeni@example.com", "mobilePhone": "05327778899", "homePhone": "02161112233"}
                """);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("email")).isEqualTo("ali.yeni@example.com");
        assertThat(ok.getBody().get("mobilePhone")).isEqualTo("05327778899");
    }

    @Test
    @DisplayName("FR-CUST-04: update rejects a Nationality ID owned by anyone else, even soft-deleted")
    void updateNationalityIdUniqueness() {
        // 1001 tries to take soft-deleted 1003's Nationality ID -> 409.
        ResponseEntity<Map> conflict = putJson("/api/customers/1001", """
                {"firstName": "Ali", "lastName": "Yildiz", "birthDate": "1990-05-14",
                 "gender": "Male", "nationalityId": "34567890123"}
                """);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().get("messageKey")).isEqualTo("MSG-CUST-DUP-NATID");

        // Keeping its own Nationality ID is fine (uniqueness excludes own record).
        ResponseEntity<Map> ok = putJson("/api/customers/1001", """
                {"firstName": "Ali", "middleName": "Kemal", "lastName": "Yildiz",
                 "birthDate": "1990-05-14", "gender": "Male", "nationalityId": "12345678901"}
                """);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("middleName")).isEqualTo("Kemal");

        // KR-01 regression on the fresh middle name: word-start over First+Middle.
        assertThat(searchResultNumbers("firstName=Kemal")).contains(1001L);
        assertThat(searchResultNumbers("firstName=emal")).doesNotContain(1001L);
    }

    @Test
    @DisplayName("internal /api/addresses/{id}: active address resolves; deleted/unknown -> 404; still zero-trust")
    void internalAddressResolution() {
        // Seed address 1 (Ali Yildiz's primary) resolves without customer context —
        // the service-to-service path product-service uses for FR-PROD-02.
        ResponseEntity<Map> ok = get("/api/addresses/1");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("addressId")).isEqualTo(1);
        assertThat(ok.getBody().get("cityName")).isEqualTo("Istanbul");
        assertThat(ok.getBody().get("districtName")).isEqualTo("Kadikoy");
        assertThat(ok.getBody().get("street")).isEqualTo("Bagdat Cad.");

        // Unknown id -> 404 (same key as the customer-scoped address lookups).
        ResponseEntity<Map> unknown = get("/api/addresses/99999");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("messageKey")).isEqualTo("MSG-CUST-NOT-FOUND");

        // Zero trust holds on the internal endpoint too (ADR-009).
        RestClient anonymous = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();
        ResponseEntity<Map> unauthorized = anonymous.get().uri("/api/addresses/1").retrieve().toEntity(Map.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listAddresses(String base) {
        return http.get().uri(base).retrieve().toEntity(List.class).getBody();
    }

    // Soft-deletes a dedicated test customer so it drops out of the active browse
    // list (KR-04 default page size 15) and doesn't push unrelated pagination-order
    // tests (e.g. absentChildRecordCriteriaMakeNoOutboundCall) off their first page.
    private void deleteTestCustomer(long customerNumber) {
        http.method(HttpMethod.DELETE).uri("/api/customers/" + customerNumber).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> putJson(String path, String json) {
        return http.method(HttpMethod.PUT).uri(path)
                .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
                .body(json).retrieve().toEntity(Map.class);
    }
}
