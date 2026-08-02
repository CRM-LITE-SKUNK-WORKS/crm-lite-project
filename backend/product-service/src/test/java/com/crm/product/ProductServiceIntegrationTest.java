package com.crm.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.product.account.AccountServiceClient;
import com.crm.product.account.AccountServiceUnavailableException;
import com.crm.product.customer.CustomerAddress;
import com.crm.product.customer.CustomerServiceClient;
import com.crm.product.customer.CustomerServiceUnavailableException;
import com.crm.product.lookup.LookupCatalogClient;
import com.crm.product.lookup.LookupCatalogUnavailableException;
import com.crm.product.lookup.LookupStatusResponse;
import com.crm.product.testsecurity.TestSecurity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
 * The two external boundaries — the account-service client (FR-PROD-01
 * composition, ADR-013 §5) and the customer-service client (FR-PROD-02
 * service-address resolution) — are mocked at their interface, never bypassed:
 * the real service/mapper/rules logic still runs against the Flyway-seeded schema.
 *
 * Security (ADR-009): the REAL resource-server filter chain from
 * crm-security-starter is active; only JWT decoding is stubbed (TestSecurity).
 *
 * Requires a running Docker daemon. Rerun with:
 *   mvn -pl backend/product-service test
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurity.TestJwtDecoderConfiguration.class)
class ProductServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** The seeded billing account with products (KR-11 number from account-service's seed). */
    static final String ACCOUNT_WITH_PRODUCTS = "1261000010";
    /** The seeded MSG-PROD-NONE fixture account. */
    static final String ACCOUNT_WITHOUT_PRODUCTS = "1261000028";
    /** V4 fixture: customer 1007's billing account with two independent product families. */
    static final String ACCOUNT_MULTI_FAMILY = "1261000127";
    /** V4 fixture: customer 1007's third billing account, deliberately product-less. */
    static final String ACCOUNT_NO_PRODUCTS_1007 = "1261000135";

    @LocalServerPort
    int port;

    @MockitoBean
    AccountServiceClient accountServiceClient;

    @MockitoBean
    CustomerServiceClient customerServiceClient;

    // ADR-015 §4.1: only the TRANSPORT is mocked — the real LookupCatalogService
    // (domain validation, contract-ID assertion, TTL cache) still runs.
    @MockitoBean
    LookupCatalogClient lookupCatalogClient;

    @Autowired
    JdbcTemplate jdbcTemplate;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurity.OPERATOR_TOKEN)
                .defaultStatusHandler(status -> true, (req, res) -> { /* assert on status manually */ })
                .build();
        stubAccountService();
        stubCustomerService();
        stubHealthyCatalog();
    }

    private void stubHealthyCatalog() {
        Mockito.when(lookupCatalogClient.fetchStatus("ACTV"))
                .thenReturn(Optional.of(new LookupStatusResponse(1L, "ACTV", "Active", "GENERAL")));
        Mockito.when(lookupCatalogClient.fetchStatus("PASV"))
                .thenReturn(Optional.of(new LookupStatusResponse(2L, "PASV", "Passive", "GENERAL")));
        // PNDG lives in the PROD domain, not GENERAL (workbook GNL_ST id 6).
        Mockito.when(lookupCatalogClient.fetchStatus("PNDG"))
                .thenReturn(Optional.of(new LookupStatusResponse(6L, "PNDG", "Pending", "PROD")));
    }

    private void stubAccountService() {
        // Mirrors account-service's V2+V3 involvement seed: products 1..4 on the
        // first billing account (including the PASSIVE product 4 — the involvement
        // projection filters only on deleted_date, ADR-013 §5 read side).
        Mockito.when(accountServiceClient.fetchProductIds(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(accountServiceClient.fetchProductIds(ACCOUNT_WITH_PRODUCTS))
                .thenReturn(Optional.of(List.of(1L, 2L, 3L, 4L)));
        Mockito.when(accountServiceClient.fetchProductIds(ACCOUNT_WITHOUT_PRODUCTS))
                .thenReturn(Optional.of(List.of()));
        // V4 fixture expansion: customer 1007's billing account with two
        // independent product families (standalone Fiber 1000MB + an ADSL 16MB
        // parent/child family) sharing one billing account.
        Mockito.when(accountServiceClient.fetchProductIds(ACCOUNT_MULTI_FAMILY))
                .thenReturn(Optional.of(List.of(13L, 14L, 15L, 16L)));
        // V4 fixture expansion: customer 1007's third billing account, a deliberate
        // MSG-PROD-NONE fixture with zero product involvement.
        Mockito.when(accountServiceClient.fetchProductIds(ACCOUNT_NO_PRODUCTS_1007))
                .thenReturn(Optional.of(List.of()));
    }

    private void stubCustomerService() {
        Mockito.when(customerServiceClient.fetchAddress(Mockito.anyLong())).thenReturn(Optional.empty());
        // Seed address 1 = Ali Yildiz's primary (customer-service seed), the
        // external service_address_id reference of the main product.
        Mockito.when(customerServiceClient.fetchAddress(1L))
                .thenReturn(Optional.of(new CustomerAddress(1L, "Istanbul", "Kadikoy", "Bagdat Cad.", "12/4",
                        "Kadikoy ev", true)));
        // V3 fixture expansion: customer 1007's primary address (addr 8, used by
        // standalone product 13) and secondary address (addr 9, used by the
        // ADSL 16MB main product 14 and resolved by its children 15/16).
        Mockito.when(customerServiceClient.fetchAddress(8L))
                .thenReturn(Optional.of(new CustomerAddress(8L, "Istanbul", "Kadikoy", "Moda Cad.", "45",
                        "Kadikoy ev", true)));
        Mockito.when(customerServiceClient.fetchAddress(9L))
                .thenReturn(Optional.of(new CustomerAddress(9L, "Istanbul", "Besiktas", "Levent Cad.", "8",
                        "Besiktas ofis", false)));
    }

    // ---------------------------------------------------------------- http helpers

    private ResponseEntity<Map> get(String path) {
        return http.get().uri(path).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<List> getList(String path) {
        return http.get().uri(path).retrieve().toEntity(List.class);
    }

    private ResponseEntity<Map> post(String path, String json) {
        return http.post().uri(path)
                .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
                .body(json).retrieve().toEntity(Map.class);
    }

    private Long statusOf(long productId) {
        return jdbcTemplate.queryForObject("SELECT status_id FROM prod WHERE id = ?", Long.class, productId);
    }

    /** A complete, valid ADSL basket: offers 1 (INTERNET) + 2 (RESOURCE) + 3 (ACTIVATION). */
    private static final String VALID_BASKET = """
            {"serviceAddressId": 1, "campaignId": "CMP-ADSL-01", "items": [
              {"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"},
                                                 {"characteristicId": 2, "value": "true"}]},
              {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]},
              {"offerId": 3, "characteristics": []}
            ]}
            """;

    private static final Set<String> ROW_KEYS = Set.of(
            "productId", "productName", "campaignName", "campaignId", "productStatus");
    private static final Set<String> DETAIL_KEYS = Set.of(
            "productOfferName", "productOfferId", "productSpecId", "campaign", "serviceAddress");

    // ---------------------------------------------------------------- security (ADR-009)

    @Test
    @DisplayName("zero trust: anonymous -> 401, token without crm-user -> 403, health public")
    void securityChecks() {
        RestClient anonymous = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { }).build();
        ResponseEntity<Map> unauthorized = anonymous.get().uri("/api/products?accountNumber=" + ACCOUNT_WITH_PRODUCTS)
                .retrieve().toEntity(Map.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.getBody().get("messageKey")).isEqualTo("MSG-AUTH-UNAUTHORIZED");

        RestClient noRole = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurity.NO_ROLE_TOKEN)
                .defaultStatusHandler(status -> true, (req, res) -> { }).build();
        ResponseEntity<Map> forbidden = noRole.get().uri("/api/offers").retrieve().toEntity(Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().get("messageKey")).isEqualTo("MSG-AUTH-FORBIDDEN");

        assertThat(anonymous.get().uri("/actuator/health").retrieve().toEntity(Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ---------------------------------------------------------------- schema + seed

    @Test
    @DisplayName("schema: exactly the 10 workbook tables — no gnl_*, customer, account or involvement tables (ADR-002/013)")
    void schemaContainsOnlyProductTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
                String.class);
        assertThat(tables).containsExactlyInAnyOrder(
                "prod_spec", "prod_ofr", "cmpg", "cmpg_prod_ofr", "prod",
                "prod_spec_char", "prod_spec_char_use", "prod_char_val",
                "prod_catal", "prod_catal_prod_ofr", "flyway_schema_history");
    }

    @Test
    @DisplayName("schema: prod carries NO customer/account column — the account link lives only in account_db (ADR-013 §5)")
    void prodHasNoAccountColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'prod' ORDER BY 1",
                String.class);
        assertThat(columns).containsExactlyInAnyOrder(
                "id", "parent_prod_id", "product_offer_id", "product_spec_id", "name", "description",
                "transaction_id", "campaign_id", "service_start_date", "service_address_id",
                "created_date", "created_by", "updated_date", "updated_by", "status_id",
                "deleted_date", "deleted_by");
    }

    // ---------------------------------------------------------------- FR-PROD-01 list

    @Test
    @DisplayName("FR-PROD-01: account with products -> 4 rows in id order; campaign branch filled, passive product Passive")
    void listProductsOfAccount() {
        ResponseEntity<List> response = getList("/api/products?accountNumber=" + ACCOUNT_WITH_PRODUCTS);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> rows = response.getBody();
        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(row -> assertThat(row.keySet()).isEqualTo(ROW_KEYS));
        assertThat(rows).extracting(row -> row.get("productId")).containsExactly(1, 2, 3, 4);
        assertThat(rows).extracting(row -> row.get("productName"))
                .containsExactly("ADSL 8MB", "ADSL Data Modem", "ADSL Activation", "ADSL 8MB Legacy");

        // AC-PROD-01-03 campaign branch: products 1 and 2 were bought inside
        // CMP-ADSL-01 (seed deviation 2); campaignId is the PUBLIC campaign code.
        assertThat(rows.get(0).get("campaignName")).isEqualTo("ADSL Hosgeldin Kampanyasi");
        assertThat(rows.get(0).get("campaignId")).isEqualTo("CMP-ADSL-01");
        assertThat(rows.get(1).get("campaignId")).isEqualTo("CMP-ADSL-01");

        // Campaign-less product: null fields ("-" rendering is the frontend's job).
        assertThat(rows.get(2).get("campaignName")).isNull();
        assertThat(rows.get(2).get("campaignId")).isNull();

        // Passive product fixture (seed deviation 3) stays listed with Status "Passive".
        assertThat(rows).extracting(row -> row.get("productStatus"))
                .containsExactly("Active", "Active", "Active", "Passive");

        // Reads stay composed over the account-service API only — the address
        // resolution belongs exclusively to the detail endpoint.
        Mockito.verify(customerServiceClient, Mockito.never()).fetchAddress(Mockito.anyLong());
    }

    @Test
    @DisplayName("FR-PROD-01: account without products -> 200 empty array (MSG-PROD-NONE is rendered by the frontend)")
    void listEmptyAccount() {
        ResponseEntity<List> response = getList("/api/products?accountNumber=" + ACCOUNT_WITHOUT_PRODUCTS);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("FR-PROD-01: unknown (or K-8 223) account -> 404 MSG-ACCT-NOT-FOUND; missing accountNumber -> 400")
    void listValidation() {
        ResponseEntity<Map> unknown = get("/api/products?accountNumber=1999999999");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("messageKey")).isEqualTo("MSG-ACCT-NOT-FOUND");

        ResponseEntity<Map> missing = get("/api/products");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");
    }

    @Test
    @DisplayName("FR-PROD-01 fails closed: account-service unreachable -> 503 MSG-SERVICE-UNAVAILABLE")
    void listFailsClosedWhenAccountServiceDown() {
        Mockito.when(accountServiceClient.fetchProductIds(ACCOUNT_WITH_PRODUCTS))
                .thenThrow(new AccountServiceUnavailableException("account-service down", null));
        ResponseEntity<Map> response = get("/api/products?accountNumber=" + ACCOUNT_WITH_PRODUCTS);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");
    }

    @Test
    @DisplayName("V4 fixture: one billing account with TWO independent product families -> campaign-linked and campaign-less rows")
    void listMultipleIndependentFamiliesOnOneAccount() {
        ResponseEntity<List> response = getList("/api/products?accountNumber=" + ACCOUNT_MULTI_FAMILY);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> rows = response.getBody();
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(row -> row.get("productId")).containsExactly(13, 14, 15, 16);

        // Family A: standalone Fiber 1000MB, campaign-less.
        assertThat(rows.get(0).get("productName")).isEqualTo("Fiber 1000MB");
        assertThat(rows.get(0).get("campaignId")).isNull();

        // Family B: ADSL 16MB main + modem + activation, all linked to CMP-ADSL-02.
        assertThat(rows.get(1).get("productName")).isEqualTo("ADSL 16MB");
        assertThat(rows.get(1).get("campaignId")).isEqualTo("CMP-ADSL-02");
        assertThat(rows.get(2).get("campaignId")).isEqualTo("CMP-ADSL-02");
        assertThat(rows).extracting(row -> row.get("productStatus"))
                .containsExactly("Active", "Active", "Active", "Active");
    }

    @Test
    @DisplayName("V4 fixture: billing account with no product involvement -> 200 empty array")
    void listEmptyForV4NoProductsAccount() {
        ResponseEntity<List> response = getList("/api/products?accountNumber=" + ACCOUNT_NO_PRODUCTS_1007);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // ---------------------------------------------------------------- FR-PROD-02 detail

    @Test
    @DisplayName("FR-PROD-02: child product detail -> all 5 fields; Service Address is the PARENT's, resolved via customer-service")
    void detailOfChildProductShowsParentAddress() {
        ResponseEntity<Map> response = get("/api/products/2");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.keySet()).isEqualTo(DETAIL_KEYS);
        assertThat(body.get("productOfferName")).isEqualTo("ADSL Data Modem Offer");
        assertThat(body.get("productOfferId")).isEqualTo(2);
        assertThat(body.get("productSpecId")).isEqualTo(2);
        assertThat(body.get("campaign")).isEqualTo("ADSL Hosgeldin Kampanyasi");

        // Product 2 has no service_address_id of its own: the parent's (product 1,
        // address 1) is displayed — resolved through customer-service.
        Map<String, Object> address = (Map<String, Object>) body.get("serviceAddress");
        assertThat(address).isNotNull();
        assertThat(address.get("addressId")).isEqualTo(1);
        assertThat(address.get("street")).isEqualTo("Bagdat Cad.");
        assertThat(address.get("houseFlatNumber")).isEqualTo("12/4");
        assertThat(address.get("districtName")).isEqualTo("Kadikoy");
        assertThat(address.get("cityName")).isEqualTo("Istanbul");
        Mockito.verify(customerServiceClient).fetchAddress(1L);
    }

    @Test
    @DisplayName("V4 fixture: child product resolves the parent's SECONDARY (non-primary) service address")
    void detailOfChildProductResolvesNonPrimaryParentAddress() {
        // Product 15 (ADSL Data Modem) is a child of main product 14 (ADSL 16MB),
        // whose service_address_id is 9 (customer 1007's non-primary address).
        ResponseEntity<Map> response = get("/api/products/15");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("campaign")).isEqualTo("ADSL Hiz Yukseltme Kampanyasi");

        Map<String, Object> address = (Map<String, Object>) body.get("serviceAddress");
        assertThat(address).isNotNull();
        assertThat(address.get("addressId")).isEqualTo(9);
        assertThat(address.get("street")).isEqualTo("Levent Cad.");
        assertThat(address.get("districtName")).isEqualTo("Besiktas");
        Mockito.verify(customerServiceClient).fetchAddress(9L);
    }

    @Test
    @DisplayName("FR-PROD-02: campaign-less product -> campaign null; passive product resolves its OWN address")
    void detailCampaignlessAndPassiveProducts() {
        ResponseEntity<Map> campaignless = get("/api/products/3");
        assertThat(campaignless.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(campaignless.getBody().get("campaign")).isNull();
        assertThat(campaignless.getBody().get("productOfferName")).isEqualTo("ADSL Activation Offer");

        // The passive fixture is a standalone product carrying its own address.
        ResponseEntity<Map> passive = get("/api/products/4");
        assertThat(passive.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> address = (Map<String, Object>) passive.getBody().get("serviceAddress");
        assertThat(address.get("addressId")).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-PROD-02: unknown product -> 404 MSG-PROD-NOT-FOUND; non-numeric id -> 400; vanished address -> null block")
    void detailValidation() {
        ResponseEntity<Map> unknown = get("/api/products/999");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("messageKey")).isEqualTo("MSG-PROD-NOT-FOUND");

        ResponseEntity<Map> nonNumeric = get("/api/products/abc");
        assertThat(nonNumeric.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(nonNumeric.getBody().get("messageKey")).isEqualTo("MSG-VALIDATION-ERROR");

        // A soft-deleted address is a 404 from customer-service: the detail still
        // renders, only the Service Address block is empty.
        Mockito.when(customerServiceClient.fetchAddress(1L)).thenReturn(Optional.empty());
        ResponseEntity<Map> blankAddress = get("/api/products/1");
        assertThat(blankAddress.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(blankAddress.getBody().get("serviceAddress")).isNull();
    }

    @Test
    @DisplayName("FR-PROD-02 fails closed: customer-service unreachable -> 503 MSG-SERVICE-UNAVAILABLE")
    void detailFailsClosedWhenCustomerServiceDown() {
        Mockito.when(customerServiceClient.fetchAddress(1L))
                .thenThrow(new CustomerServiceUnavailableException("customer-service down", null));
        ResponseEntity<Map> response = get("/api/products/1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");
    }

    // ---------------------------------------------------------------- catalog reads

    @Test
    @DisplayName("GET /api/offers: V2 ADSL (3) + V3 Fiber/ADSL expansion (7) = 10 active offers, id-ordered")
    void offersCatalog() {
        ResponseEntity<List> response = getList("/api/offers");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> offers = response.getBody();
        assertThat(offers).hasSize(10);
        assertThat(offers).allSatisfy(offer ->
                assertThat(offer.keySet()).isEqualTo(Set.of("offerId", "offerName", "serviceType", "price")));
        // findActiveWithSpec orders by offer id ascending: V2's 3 ADSL offers first,
        // then the V3 fixture expansion (Fiber family + 2 more ADSL offers).
        assertThat(offers).extracting(offer -> offer.get("offerName")).containsExactly(
                "ADSL 8MB Offer", "ADSL Data Modem Offer", "ADSL Activation Offer",
                "Fiber 100MB Offer", "Fiber 500MB Offer", "Fiber 1000MB Offer",
                "Fiber Wi-Fi 6 Modem Offer", "Fiber Activation Offer",
                "ADSL 16MB Offer", "ADSL 24MB Offer");
        assertThat(offers).extracting(offer -> offer.get("serviceType")).containsExactly(
                "INTERNET", "RESOURCE", "ACTIVATION",
                "INTERNET", "INTERNET", "INTERNET", "RESOURCE", "ACTIVATION",
                "INTERNET", "INTERNET");
        // Seed deviation: all prices are project-added dev fixtures (analyst approval pending).
        assertThat(offers).extracting(offer -> ((Number) offer.get("price")).doubleValue()).containsExactly(
                299.00, 149.00, 49.00,
                399.00, 599.00, 799.00, 249.00, 79.00,
                349.00, 379.00);
    }

    @Test
    @DisplayName("GET /api/campaigns: V2 CMP-ADSL-01 (497.00) + V3 Fiber/ADSL campaigns (727/927/547) = 4 active campaigns")
    void campaignsCatalog() {
        ResponseEntity<List> response = getList("/api/campaigns");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> campaigns = response.getBody();
        assertThat(campaigns).hasSize(4);
        assertThat(campaigns).extracting(c -> c.get("campaignId"))
                .containsExactly("CMP-ADSL-01", "CMP-FIBER-01", "CMP-FIBER-02", "CMP-ADSL-02");

        Map<String, Object> campaign = campaigns.get(0);
        assertThat(campaign.keySet()).isEqualTo(Set.of(
                "campaignId", "campaignName", "description", "activationEndDate", "offers", "totalPrice"));
        assertThat(campaign.get("campaignId")).isEqualTo("CMP-ADSL-01");   // never the internal cmpg.id
        assertThat(campaign.get("campaignName")).isEqualTo("ADSL Hosgeldin Kampanyasi");
        assertThat(((Number) campaign.get("totalPrice")).doubleValue()).isEqualTo(497.00);

        List<Map<String, Object>> offers = (List<Map<String, Object>>) campaign.get("offers");
        assertThat(offers).hasSize(3);
        assertThat(offers).extracting(offer -> offer.get("main")).containsExactly(true, false, false);
        assertThat(offers).extracting(offer -> offer.get("serviceType"))
                .containsExactly("INTERNET", "RESOURCE", "ACTIVATION");

        // V3 fixture expansion: derived totals for the new campaigns (all project-added
        // dev fixture prices, none expired, none passive).
        assertThat(((Number) campaigns.get(1).get("totalPrice")).doubleValue()).isEqualTo(727.00); // CMP-FIBER-01
        assertThat(((Number) campaigns.get(2).get("totalPrice")).doubleValue()).isEqualTo(927.00); // CMP-FIBER-02
        assertThat(((Number) campaigns.get(3).get("totalPrice")).doubleValue()).isEqualTo(547.00); // CMP-ADSL-02
    }

    // ================================================== FR-SALE-01 write slice (ADR-015)

    @Test
    @DisplayName("characteristics: schema comes through the offer's SPEC; an offer with none is 200 [] (AC-SALE-01-21)")
    void offerCharacteristicSchema() {
        // Offer 1 -> spec 1 (ADSL Internet): chars 1 (NUMBER, mandatory), 2 (BOOLEAN), 4 (DATE).
        List<Map<String, Object>> internet = getList("/api/offers/1/characteristics").getBody();
        assertThat(internet).hasSize(3);
        assertThat(internet.get(0).keySet()).isEqualTo(Set.of(
                "characteristicId", "name", "description", "dataType", "mandatory"));
        assertThat(internet).extracting(row -> row.get("dataType")).containsExactly("NUMBER", "BOOLEAN", "DATE");
        assertThat(internet).extracting(row -> row.get("mandatory")).containsExactly(true, false, false);

        // Offer 2 -> spec 2 (modem): one mandatory TEXT characteristic.
        List<Map<String, Object>> modem = getList("/api/offers/2/characteristics").getBody();
        assertThat(modem).hasSize(1);
        assertThat(modem.get(0).get("name")).isEqualTo("MAC Address");

        // AC-SALE-01-21: offer 3 (activation) has NO characteristics — an empty list
        // is the valid answer, not a 404. The screen renders the block without fields.
        assertThat(getList("/api/offers/3/characteristics").getBody()).isEmpty();

        ResponseEntity<Map> unknown = get("/api/offers/999/characteristics");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("messageKey")).isEqualTo("MSG-PROD-NOT-FOUND");
    }

    @Test
    @DisplayName("create: main/child derived from service type, PNDG, char values persisted, total derived")
    void createProductsAsPending() {
        ResponseEntity<Map> response = post("/api/products", VALID_BASKET);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body = response.getBody();
        assertThat(body.get("campaignId")).isEqualTo("CMP-ADSL-01");     // public code, never cmpg.id
        assertThat(body.get("campaignName")).isEqualTo("ADSL Hosgeldin Kampanyasi");
        // AC-SALE-01-12 Total Amount, derived: 299 + 149 + 49.
        assertThat(((Number) body.get("totalAmount")).doubleValue()).isEqualTo(497.00);

        List<Map<String, Object>> products = (List<Map<String, Object>>) body.get("products");
        assertThat(products).hasSize(3);
        // AC-SALE-01-09: the INTERNET offer is the main product — DERIVED from the
        // service type, never declared by the caller.
        assertThat(products.get(0).get("offerId")).isEqualTo(1);
        assertThat(products.get(0).get("main")).isEqualTo(true);
        assertThat(products).filteredOn(p -> Boolean.TRUE.equals(p.get("main"))).hasSize(1);

        long mainProductId = ((Number) body.get("mainProductId")).longValue();
        assertThat(((Number) products.get(0).get("productId")).longValue()).isEqualTo(mainProductId);

        // Every row PNDG (6) — reserved, not yet committed.
        for (Map<String, Object> product : products) {
            assertThat(statusOf(((Number) product.get("productId")).longValue())).isEqualTo(6L);
        }

        // Only the MAIN product stores the service address; children resolve it by
        // walking up the parent chain (FR-PROD-02), so a stored copy would be a
        // second truth that could drift.
        Map<String, Object> mainRow = jdbcTemplate.queryForMap(
                "SELECT parent_prod_id, service_address_id, campaign_id, transaction_id, created_by "
                        + "FROM prod WHERE id = ?", mainProductId);
        assertThat(mainRow.get("parent_prod_id")).isNull();
        assertThat(((Number) mainRow.get("service_address_id")).longValue()).isEqualTo(1L);
        assertThat(((Number) mainRow.get("campaign_id")).longValue()).isEqualTo(1L);
        assertThat(mainRow.get("transaction_id")).isNull();               // ADR-015 §8.2
        assertThat(mainRow.get("created_by")).isEqualTo(TestSecurity.OPERATOR_SUBJECT);

        long childId = ((Number) products.get(1).get("productId")).longValue();
        Map<String, Object> childRow = jdbcTemplate.queryForMap(
                "SELECT parent_prod_id, service_address_id FROM prod WHERE id = ?", childId);
        assertThat(((Number) childRow.get("parent_prod_id")).longValue()).isEqualTo(mainProductId);
        assertThat(childRow.get("service_address_id")).isNull();

        // Characteristic values persisted, PNDG like their product. The optional DATE
        // (char 4) was not submitted and must NOT be written as an empty row.
        List<Map<String, Object>> values = jdbcTemplate.queryForList(
                "SELECT product_spec_char_id, val, status_id FROM prod_char_val WHERE product_id = ? "
                        + "ORDER BY product_spec_char_id", mainProductId);
        assertThat(values).hasSize(2);
        assertThat(values.get(0).get("val")).isEqualTo("16");
        assertThat(values.get(1).get("val")).isEqualTo("true");
        assertThat(values).allSatisfy(v -> assertThat(((Number) v.get("status_id")).longValue()).isEqualTo(6L));

        // ADR-015 §5.5: a PNDG product is not yet a product the customer owns.
        ResponseEntity<Map> detail = get("/api/products/" + mainProductId);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detail.getBody().get("messageKey")).isEqualTo("MSG-PROD-NOT-FOUND");
    }

    @Test
    @DisplayName("confirm: PNDG -> ACTV incl. characteristic values; idempotent on retry; product becomes visible")
    void confirmPromotesPendingProducts() {
        Map<String, Object> created = post("/api/products", VALID_BASKET).getBody();
        List<Map<String, Object>> products = (List<Map<String, Object>>) created.get("products");
        List<Long> ids = products.stream().map(p -> ((Number) p.get("productId")).longValue()).toList();
        long mainProductId = ((Number) created.get("mainProductId")).longValue();

        ResponseEntity<Map> confirm = post("/api/products/confirm", "{\"productIds\": " + ids + "}");
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ids.forEach(id -> assertThat(statusOf(id)).isEqualTo(1L));

        // Values follow their product — leaving them PNDG would make the
        // "find incomplete sales" query lie (ADR-015 §8.4).
        List<Long> valueStatuses = jdbcTemplate.queryForList(
                "SELECT status_id FROM prod_char_val WHERE product_id = ?", Long.class, mainProductId);
        assertThat(valueStatuses).isNotEmpty().allMatch(status -> status == 1L);

        // Idempotent: ADR-016 §5.3 retries confirm, so a second call must not fail.
        assertThat(post("/api/products/confirm", "{\"productIds\": " + ids + "}").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        ids.forEach(id -> assertThat(statusOf(id)).isEqualTo(1L));

        // Now a real product: the detail modal answers.
        ResponseEntity<Map> detail = get("/api/products/" + mainProductId);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().keySet()).isEqualTo(DETAIL_KEYS);
    }

    @Test
    @DisplayName("cancel: PNDG-only compensation, full soft-delete invariant; a committed product -> 409")
    void cancelPassivatesOnlyPendingProducts() {
        Map<String, Object> created = post("/api/products", VALID_BASKET).getBody();
        List<Map<String, Object>> products = (List<Map<String, Object>>) created.get("products");
        List<Long> ids = products.stream().map(p -> ((Number) p.get("productId")).longValue()).toList();
        long mainProductId = ((Number) created.get("mainProductId")).longValue();

        assertThat(post("/api/products/cancel", "{\"productIds\": " + ids + "}").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // Full invariant, never a physical delete: PASV + deleted/updated metadata.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status_id, deleted_date, deleted_by, updated_date FROM prod WHERE id = ?", mainProductId);
        assertThat(((Number) row.get("status_id")).longValue()).isEqualTo(2L);
        assertThat(row.get("deleted_date")).isNotNull();
        assertThat(row.get("deleted_by")).isEqualTo(TestSecurity.OPERATOR_SUBJECT);
        assertThat(row.get("updated_date")).isNotNull();

        List<Long> valueStatuses = jdbcTemplate.queryForList(
                "SELECT status_id FROM prod_char_val WHERE product_id = ?", Long.class, mainProductId);
        assertThat(valueStatuses).isNotEmpty().allMatch(status -> status == 2L);

        // ADR-015 §5.7: the guard is the point. Cancelling a COMMITTED product would
        // be the KR-7 cancellation that is out of phase, arriving by the back door.
        ResponseEntity<Map> committed = post("/api/products/cancel", "{\"productIds\": [1]}");
        assertThat(committed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(committed.getBody().get("messageKey")).isEqualTo("MSG-PROD-NOT-PENDING");
        assertThat(statusOf(1L)).isEqualTo(1L);   // untouched

        ResponseEntity<Map> unknown = post("/api/products/cancel", "{\"productIds\": [999999]}");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("messageKey")).isEqualTo("MSG-PROD-NOT-FOUND");
    }

    @Test
    @DisplayName("AC-SALE-01-08: each basket-composition failure gets its OWN analyst message key")
    void basketCompositionRules() {
        int before = productCount();

        // Missing RESOURCE and ACTIVATION -> the first missing one is reported.
        assertKey(post("/api/products", basket("{\"offerId\": 1, \"characteristics\": "
                + "[{\"characteristicId\": 1, \"value\": \"16\"}]}")), "MSG-SALE-NO-RESOURCE");

        // Two INTERNET offers (1 and 9, both spec 1) -> MULTI, not NO.
        assertKey(post("/api/products", basket(
                "{\"offerId\": 1, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"16\"}]}",
                "{\"offerId\": 9, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"24\"}]}",
                "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}",
                "{\"offerId\": 3, \"characteristics\": []}")), "MSG-SALE-MULTI-INTERNET");

        // Missing ACTIVATION only.
        assertKey(post("/api/products", basket(
                "{\"offerId\": 1, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"16\"}]}",
                "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}")),
                "MSG-SALE-NO-ACTIVATION");

        // AC-SALE-01-05: the same offer twice.
        assertKey(post("/api/products", basket(
                "{\"offerId\": 1, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"16\"}]}",
                "{\"offerId\": 1, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"16\"}]}",
                "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}",
                "{\"offerId\": 3, \"characteristics\": []}")), "MSG-SALE-DUP-OFFER");

        // The V4 passive-offer fixture (11, spec 1) — inactive is reported BEFORE
        // composition, even though it would ALSO be a MULTI-INTERNET basket.
        assertKey(post("/api/products", basket(
                "{\"offerId\": 11, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"4\"}]}",
                "{\"offerId\": 1, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"16\"}]}",
                "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}",
                "{\"offerId\": 3, \"characteristics\": []}")), "MSG-SALE-OFFER-INACTIVE");

        // A nonexistent offer is a client defect, NOT "inactive" — telling the user
        // an offer is inactive when it does not exist would be false.
        assertKey(post("/api/products", basket("{\"offerId\": 987654, \"characteristics\": []}")),
                "MSG-VALIDATION-ERROR");

        // An empty basket never reaches the rules: bean validation rejects it.
        assertKey(post("/api/products",
                "{\"serviceAddressId\": 1, \"items\": []}"), "MSG-VALIDATION-ERROR");

        // Nothing was persisted by ANY of the rejections.
        assertThat(productCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-SALE-01-18/19: mandatory-blank and format failures carry their own keys; nothing persisted")
    void characteristicValidationRules() {
        int before = productCount();

        // Char 1 is mandatory for spec 1 and was left blank.
        assertKey(post("/api/products", basket(
                "{\"offerId\": 1, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"  \"}]}",
                "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}",
                "{\"offerId\": 3, \"characteristics\": []}")), "MSG-VAL-CHAR-REQUIRED");

        // Omitting it entirely is the same failure as sending it blank.
        assertKey(post("/api/products", basket(
                "{\"offerId\": 1, \"characteristics\": []}",
                "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}",
                "{\"offerId\": 3, \"characteristics\": []}")), "MSG-VAL-CHAR-REQUIRED");

        // NUMBER given a word.
        assertKey(post("/api/products", basket(
                "{\"offerId\": 1, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"fast\"}]}",
                "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}",
                "{\"offerId\": 3, \"characteristics\": []}")), "MSG-VAL-CHAR-FORMAT");

        // BOOLEAN given "1" — deliberately rejected: Boolean.parseBoolean would
        // silently turn every typo into `false`.
        assertKey(post("/api/products", basket(
                "{\"offerId\": 1, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"16\"},"
                        + "{\"characteristicId\": 2, \"value\": \"1\"}]}",
                "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}",
                "{\"offerId\": 3, \"characteristics\": []}")), "MSG-VAL-CHAR-FORMAT");

        // DATE given a non-ISO value.
        assertKey(post("/api/products", basket(
                "{\"offerId\": 1, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"16\"},"
                        + "{\"characteristicId\": 4, \"value\": \"31/12/2027\"}]}",
                "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}",
                "{\"offerId\": 3, \"characteristics\": []}")), "MSG-VAL-CHAR-FORMAT");

        // A characteristic the offer does not use is a client defect, not user input —
        // MSG-VAL-CHAR-* would describe a field that was never on the screen.
        assertKey(post("/api/products", basket(
                "{\"offerId\": 1, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"16\"},"
                        + "{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}",
                "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]}",
                "{\"offerId\": 3, \"characteristics\": []}")), "MSG-VALIDATION-ERROR");

        assertThat(productCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("campaign coherence: unknown code and a non-member offer are both rejected, nothing persisted")
    void campaignCoherenceRules() {
        int before = productCount();

        assertKey(post("/api/products", VALID_BASKET.replace("CMP-ADSL-01", "CMP-NOPE-99")),
                "MSG-VALIDATION-ERROR");

        // Offer 9 (ADSL 16MB) is a member of CMP-ADSL-02, not CMP-ADSL-01 — a basket
        // claiming a campaign it does not match is a client defect, not a discount.
        assertKey(post("/api/products", "{\"serviceAddressId\": 1, \"campaignId\": \"CMP-ADSL-01\", \"items\": ["
                + "{\"offerId\": 9, \"characteristics\": [{\"characteristicId\": 1, \"value\": \"16\"}]},"
                + "{\"offerId\": 2, \"characteristics\": [{\"characteristicId\": 3, \"value\": \"AA:BB:CC:DD:EE:FF\"}]},"
                + "{\"offerId\": 3, \"characteristics\": []}]}"), "MSG-VALIDATION-ERROR");

        assertThat(productCount()).isEqualTo(before);

        // No campaign at all is legal: a basket assembled offer by offer.
        ResponseEntity<Map> noCampaign = post("/api/products", VALID_BASKET
                .replace("\"campaignId\": \"CMP-ADSL-01\", ", ""));
        assertThat(noCampaign.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(noCampaign.getBody().get("campaignId")).isNull();
        assertThat(noCampaign.getBody().get("campaignName")).isNull();
    }

    @Test
    @DisplayName("ADR-002 §7 fail closed: catalog outage on a write -> 503, nothing persisted; reads unaffected")
    void writeFailsClosedWhenCatalogUnavailable() {
        int before = productCount();
        Mockito.when(lookupCatalogClient.fetchStatus("PNDG"))
                .thenThrow(new LookupCatalogUnavailableException("catalog down", null));

        ResponseEntity<Map> response = post("/api/products", VALID_BASKET);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-SERVICE-UNAVAILABLE");
        assertThat(productCount()).isEqualTo(before);

        // ADR-002 §8: reads use the local LookupContract constants, so a catalog
        // outage must NOT degrade product viewing.
        ResponseEntity<List> list = getList("/api/products?accountNumber=" + ACCOUNT_WITH_PRODUCTS);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(4);
    }

    // ------------------------------------------------- write-slice test helpers

    private int productCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM prod", Integer.class);
        return count == null ? -1 : count;
    }

    private String basket(String... items) {
        return "{\"serviceAddressId\": 1, \"items\": [" + String.join(",", items) + "]}";
    }

    private void assertKey(ResponseEntity<Map> response, String expectedKey) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("messageKey")).isEqualTo(expectedKey);
    }
}
