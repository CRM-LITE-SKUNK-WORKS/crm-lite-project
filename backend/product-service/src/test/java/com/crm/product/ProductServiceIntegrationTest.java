package com.crm.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.product.account.AccountServiceClient;
import com.crm.product.account.AccountServiceUnavailableException;
import com.crm.product.customer.CustomerAddress;
import com.crm.product.customer.CustomerServiceClient;
import com.crm.product.customer.CustomerServiceUnavailableException;
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
}
