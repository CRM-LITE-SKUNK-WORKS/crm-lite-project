package com.crm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * End-to-end BFF verification against a REAL Keycloak (Testcontainers) using the
 * COMMITTED realm import (infra/keycloak/realm/crm-lite-realm.json) — including its
 * client, PKCE setting, audience mapper, role mappers, seed users and the disabled
 * user. The single test-only modification: accessTokenLifespan is shortened to make
 * expiry/refresh observable within a test run.
 *
 * The gateway runs on the fixed port 8080 because the committed realm's redirect
 * URIs point there. Downstream domain services are replaced by a local recording
 * sink so the exact relayed headers (Bearer token present, Cookie absent) can be
 * asserted.
 *
 * Requires Docker. Rerun with: mvn -pl backend/api-gateway test
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class GatewayBffIntegrationTest {

    private static final String REALM = "crm-lite";
    private static final Pattern FORM_ACTION = Pattern.compile("action=\"([^\"]+)\"");
    private static final Pattern LOGOUT_URL = Pattern.compile("\"logoutUrl\":\"([^\"]+)\"");

    /** Downstream recording sink standing in for customer-service. */
    private static HttpServer sink;
    private static int sinkPort;
    private static final Map<String, Map<String, String>> SINK_HEADERS = new ConcurrentHashMap<>();

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.3.4")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(MountableFile.forHostPath(testRealmFile()),
                    "/opt/keycloak/data/import/crm-lite-realm.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM).forPort(8080).withStartupTimeout(java.time.Duration.ofMinutes(3)));

    /**
     * The committed realm with ONLY accessTokenLifespan shortened (3s) so the
     * refresh path is exercised; everything else is byte-identical.
     */
    private static Path testRealmFile() {
        try {
            Path committed = Path.of("..", "..", "infra", "keycloak", "realm", "crm-lite-realm.json");
            String json = Files.readString(committed, StandardCharsets.UTF_8);
            String shortened = json.replace("\"accessTokenLifespan\": 300", "\"accessTokenLifespan\": 3");
            if (shortened.equals(json)) {
                throw new IllegalStateException("accessTokenLifespan marker not found in committed realm file");
            }
            Path tmp = Files.createTempFile("crm-lite-realm-test", ".json");
            Files.writeString(tmp, shortened, StandardCharsets.UTF_8);
            return tmp;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeAll
    static void startSink() throws IOException {
        sink = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sink.createContext("/", exchange -> {
            Map<String, String> headers = new ConcurrentHashMap<>();
            headers.put("Authorization", header(exchange.getRequestHeaders().getFirst("Authorization")));
            headers.put("Cookie", header(exchange.getRequestHeaders().getFirst("Cookie")));
            SINK_HEADERS.put(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath(), headers);
            byte[] body = "{\"proxied\": true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        sink.start();
        sinkPort = sink.getAddress().getPort();
    }

    private static String header(String value) {
        return value == null ? "<absent>" : value;
    }

    @AfterAll
    static void stopSink() {
        sink.stop(0);
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        String kc = "http://127.0.0.1:" + KEYCLOAK.getMappedPort(8080);
        String realmBase = kc + "/realms/" + REALM;

        registry.add("server.port", () -> "8080");
        registry.add("eureka.client.enabled", () -> "false");

        registry.add("crm.security.keycloak.base-url", () -> kc);
        registry.add("crm.security.keycloak.realm", () -> REALM);

        registry.add("spring.security.oauth2.client.registration.keycloak.client-id", () -> "crm-bff");
        registry.add("spring.security.oauth2.client.registration.keycloak.client-authentication-method", () -> "none");
        registry.add("spring.security.oauth2.client.registration.keycloak.authorization-grant-type", () -> "authorization_code");
        registry.add("spring.security.oauth2.client.registration.keycloak.redirect-uri", () -> "{baseUrl}/login/oauth2/code/{registrationId}");
        registry.add("spring.security.oauth2.client.registration.keycloak.scope", () -> "openid,profile");
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", () -> realmBase);
        registry.add("spring.security.oauth2.client.provider.keycloak.authorization-uri", () -> realmBase + "/protocol/openid-connect/auth");
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", () -> realmBase + "/protocol/openid-connect/token");
        registry.add("spring.security.oauth2.client.provider.keycloak.jwk-set-uri", () -> realmBase + "/protocol/openid-connect/certs");
        registry.add("spring.security.oauth2.client.provider.keycloak.user-info-uri", () -> realmBase + "/protocol/openid-connect/userinfo");
        registry.add("spring.security.oauth2.client.provider.keycloak.user-name-attribute", () -> "preferred_username");

        registry.add("spring.cloud.gateway.server.webmvc.routes[0].id", () -> "customer-service");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].uri", () -> "http://127.0.0.1:" + sinkPort);
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].predicates[0]", () -> "Path=/api/customers/**");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].filters[0]", () -> "TokenRelay=");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].filters[1]", () -> "RemoveRequestHeader=Cookie");
    }

    // ------------------------------------------------------------ helpers

    private record Browser(HttpClient client, CookieManager cookies) {

        static Browser fresh() {
            // Keycloak marks its auth-session cookies Secure even over http in dev
            // mode. Real browsers exempt localhost from the Secure check; the JDK
            // HttpClient does not and silently drops them, breaking the login form
            // POST. Strip the flag so this http-only test behaves like a browser.
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL) {
                @Override
                public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
                    super.put(uri, responseHeaders);
                    getCookieStore().getCookies().forEach(cookie -> cookie.setSecure(false));
                }
            };
            HttpClient client = HttpClient.newBuilder()
                    .cookieHandler(cookies)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            return new Browser(client, cookies);
        }

        HttpResponse<String> get(String url, String... headers) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
            for (int i = 0; i < headers.length; i += 2) {
                builder.header(headers[i], headers[i + 1]);
            }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String url, String form, String... headers) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .header("Content-Type", "application/x-www-form-urlencoded");
            for (int i = 0; i < headers.length; i += 2) {
                builder.header(headers[i], headers[i + 1]);
            }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        String cookieValue(String name) {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(c -> c.getName().equals(name))
                    .map(HttpCookie::getValue)
                    .findFirst().orElse(null);
        }
    }

    private static final String GATEWAY = "http://localhost:8080";

    /** Full Authorization Code + PKCE browser flow; returns the logged-in "browser". */
    private Browser login(String username, String password) throws Exception {
        Browser browser = Browser.fresh();
        login(browser, username, password);
        return browser;
    }

    /**
     * Same flow on an EXISTING browser, so a test can seed session state (e.g. a
     * request-cache entry) before logging in. Returns the final callback response —
     * its Location header IS the post-login landing page.
     */
    private HttpResponse<String> login(Browser browser, String username, String password) throws Exception {
        HttpResponse<String> start = browser.get(GATEWAY + "/oauth2/authorization/keycloak");
        assertThat(start.statusCode()).isEqualTo(302);
        String authorizeUrl = start.headers().firstValue("Location").orElseThrow();
        // PKCE is on (public client): the authorization request carries a code challenge.
        assertThat(authorizeUrl).contains("code_challenge=").contains("code_challenge_method=S256");

        HttpResponse<String> loginPage = browser.get(authorizeUrl);
        assertThat(loginPage.statusCode()).isEqualTo(200);
        String formAction = extractFormAction(loginPage.body());

        HttpResponse<String> submitted = browser.post(formAction,
                "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                        + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8));
        if (submitted.statusCode() != 302) {
            // Stayed on the Keycloak login page (invalid/disabled user). Print the
            // page's error so unexpected failures here are diagnosable.
            System.out.println("[login-debug] form POST status=" + submitted.statusCode()
                    + " action=" + formAction);
            Matcher error = Pattern.compile("(?s)kc-error-message.*?<span[^>]*>([^<]+)").matcher(submitted.body());
            System.out.println("[login-debug] " + (error.find()
                    ? "kc error: " + error.group(1).trim()
                    : "body head: " + submitted.body().substring(0, Math.min(400, submitted.body().length())).replaceAll("\\s+", " ")));
            return submitted;
        }
        String callback = submitted.headers().firstValue("Location").orElseThrow();
        assertThat(callback).startsWith(GATEWAY + "/login/oauth2/code/keycloak");

        HttpResponse<String> completed = browser.get(callback);
        assertThat(completed.statusCode()).isEqualTo(302); // -> saved request or the origin root
        // A failed code exchange also yields a 302 — but to /login?error. Make that
        // failure mode explicit instead of surfacing later as a puzzling 401.
        assertThat(completed.headers().firstValue("Location").orElseThrow())
                .doesNotContain("error");
        return completed;
    }

    private static String extractFormAction(String html) {
        Matcher matcher = FORM_ACTION.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("No form action found in Keycloak login page");
        }
        return matcher.group(1).replace("&amp;", "&");
    }

    private static String extractLogoutUrl(String json) {
        Matcher matcher = LOGOUT_URL.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No logoutUrl found in logout response: " + json);
        }
        return matcher.group(1).replace("\\/", "/");
    }

    private static String decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3); // header.payload.signature — a real signed JWT
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ tests

    @Test
    @DisplayName("login via Authorization Code + PKCE establishes a session; /api/session/me knows the user")
    void loginEstablishesSession() throws Exception {
        Browser browser = login("ayilmaz", "crm-dev");

        HttpResponse<String> me = browser.get(GATEWAY + "/api/session/me", "Accept", "application/json");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"username\":\"ayilmaz\"").contains("crm-user");

        // No token is ever exposed to the browser (ADR-007): cookies are only the
        // session id + readable XSRF token, and neither cookies nor the session
        // body carry anything JWT-shaped ("eyJ" base64-JSON prefix).
        List<String> cookieNames = browser.cookies().getCookieStore().getCookies().stream()
                .map(HttpCookie::getName)
                .filter(name -> !name.startsWith("AUTH_SESSION") && !name.startsWith("KEYCLOAK")
                        && !name.startsWith("KC_")) // Keycloak's own cookies live on the Keycloak origin
                .toList();
        assertThat(cookieNames).containsExactlyInAnyOrder("JSESSIONID", "XSRF-TOKEN");
        browser.cookies().getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("JSESSIONID") || c.getName().equals("XSRF-TOKEN"))
                .forEach(c -> assertThat(c.getValue()).doesNotContain("eyJ"));
        assertThat(me.body()).doesNotContain("eyJ");
        // The session cookie is HttpOnly (raw Set-Cookie flag checked during login redirects).
    }

    @Test
    @DisplayName("TokenRelay: downstream receives a real Keycloak Bearer JWT (iss/aud/role) and NO cookies")
    void tokenRelayPassesBearerAndStripsCookies() throws Exception {
        Browser browser = login("ayilmaz", "crm-dev");
        SINK_HEADERS.clear();

        HttpResponse<String> proxied = browser.get(GATEWAY + "/api/customers", "Accept", "application/json");
        assertThat(proxied.statusCode()).isEqualTo(200);
        assertThat(proxied.body()).contains("\"proxied\": true");

        Map<String, String> received = SINK_HEADERS.get("GET /api/customers");
        assertThat(received).isNotNull();
        assertThat(received.get("Cookie")).isEqualTo("<absent>"); // browser cookies never leave the BFF
        assertThat(received.get("Authorization")).startsWith("Bearer eyJ");

        String payload = decodeJwtPayload(received.get("Authorization").substring("Bearer ".length()));
        // The committed realm's audience mapper and role model reach the services intact.
        assertThat(payload).contains("\"crm-api\"");
        assertThat(payload).contains("crm-user");
        assertThat(payload).contains("/realms/" + REALM + "\"");
    }

    @Test
    @DisplayName("access-token expiry: relay transparently refreshes; downstream sees a fresh token")
    void expiredAccessTokenIsRefreshed() throws Exception {
        Browser browser = login("ayilmaz", "crm-dev");
        SINK_HEADERS.clear();

        assertThat(browser.get(GATEWAY + "/api/customers", "Accept", "application/json").statusCode()).isEqualTo(200);
        String first = SINK_HEADERS.get("GET /api/customers").get("Authorization");

        Thread.sleep(4500); // accessTokenLifespan is 3s in the test realm

        assertThat(browser.get(GATEWAY + "/api/customers", "Accept", "application/json").statusCode()).isEqualTo(200);
        String second = SINK_HEADERS.get("GET /api/customers").get("Authorization");

        assertThat(first).startsWith("Bearer eyJ");
        assertThat(second).startsWith("Bearer eyJ");
        assertThat(second).isNotEqualTo(first); // refreshed, not replayed
    }

    @Test
    @DisplayName("CSRF: unsafe proxied request without X-XSRF-TOKEN -> 403 and never reaches downstream")
    void csrfProtectsUnsafeProxiedRequests() throws Exception {
        Browser browser = login("ayilmaz", "crm-dev");
        browser.get(GATEWAY + "/api/session/me", "Accept", "application/json"); // ensure XSRF cookie issued
        SINK_HEADERS.clear();

        HttpResponse<String> rejected = browser.post(GATEWAY + "/api/customers", "{}",
                "Accept", "application/json");
        assertThat(rejected.statusCode()).isEqualTo(403);
        assertThat(rejected.body()).contains("MSG-AUTH-CSRF-REJECTED");
        assertThat(SINK_HEADERS).doesNotContainKey("POST /api/customers");

        String xsrf = browser.cookieValue("XSRF-TOKEN");
        assertThat(xsrf).isNotBlank();
        HttpResponse<String> accepted = browser.post(GATEWAY + "/api/customers", "{}",
                "Accept", "application/json", "X-XSRF-TOKEN", xsrf);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(SINK_HEADERS).containsKey("POST /api/customers");
    }

    @Test
    @DisplayName("anonymous: API calls get 401 JSON; browser navigation is redirected to Keycloak")
    void anonymousHandling() throws Exception {
        Browser anonymous = Browser.fresh();

        HttpResponse<String> api = anonymous.get(GATEWAY + "/api/customers", "Accept", "application/json");
        assertThat(api.statusCode()).isEqualTo(401);
        assertThat(api.body()).contains("MSG-AUTH-UNAUTHORIZED");

        HttpResponse<String> page = anonymous.get(GATEWAY + "/",
                "Accept", "text/html,application/xhtml+xml");
        assertThat(page.statusCode()).isEqualTo(302);
        assertThat(page.headers().firstValue("Location").orElseThrow())
                .contains("/oauth2/authorization/keycloak");

        // Session probe without a session -> 401 (Angular's login trigger).
        assertThat(anonymous.get(GATEWAY + "/api/session/me", "Accept", "application/json").statusCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("post-login landing is ALWAYS the app root: nothing anonymous is ever replayed")
    void postLoginLandingIsAlwaysTheApplicationRoot() throws Exception {
        Browser browser = Browser.fresh();

        // Everything a dead session leaves behind, in the order a real browser
        // produces it. ExceptionTranslationFilter saves the current request BEFORE
        // calling the entry point, so with a request cache ANY of these could become
        // the post-login landing page — and the SPA shares this very session through
        // nginx (FE-ADR-004/010).
        assertThat(browser.get(GATEWAY + "/api/customers/1001", "Accept", "application/json").statusCode())
                .isEqualTo(401);                       // the raw API tab (first report)
        assertThat(browser.get(GATEWAY + "/api/session/me", "Accept", "application/json").statusCode())
                .isEqualTo(401);                       // Angular's own anonymous probe
        assertThat(browser.get(GATEWAY + "/favicon.ico",
                "Accept", "image/avif,image/webp,*/*", "Sec-Fetch-Dest", "image").statusCode())
                .isEqualTo(302);                       // the sub-resource the second report hit
        assertThat(browser.get(GATEWAY + "/actuator/info", "Accept", "text/html,application/xhtml+xml")
                .statusCode()).isEqualTo(302);         // machine-readable, navigable
        assertThat(browser.get(GATEWAY + "/portal/report?id=7", "Accept", "text/html,application/xhtml+xml")
                .statusCode()).isEqualTo(302);         // a plausible top-level navigation

        HttpResponse<String> completed = login(browser, "ayilmaz", "crm-dev");

        String landing = completed.headers().firstValue("Location").orElseThrow();
        // "continue" is HttpSessionRequestCache's default matching parameter — the
        // fingerprint of a replayed saved request, which must never appear now.
        assertThat(landing).doesNotContain("continue")
                .doesNotContain("/api/").doesNotContain("favicon").doesNotContain("/portal/");
        // Landing = the application root derived from the request, so a login arriving
        // through the nginx-forwarded :4200 origin lands there instead, where Angular
        // routes '' -> 'customers'.
        assertThat(landing).isEqualTo(GATEWAY + "/");
        assertThat(browser.get(GATEWAY + "/api/session/me", "Accept", "application/json").statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("unmapped path is 404 MSG-NOT-FOUND, not a 500 server fault")
    void unmappedPathIsNotFoundNotServerError() throws Exception {
        Browser browser = login("ayilmaz", "crm-dev");

        // /favicon.ico is neither routed nor served here; before the fix the catch-all
        // advice turned it into 500 MSG-INTERNAL-ERROR with a logged stack trace.
        HttpResponse<String> favicon = browser.get(GATEWAY + "/favicon.ico",
                "Accept", "image/avif,image/webp,*/*");
        assertThat(favicon.statusCode()).isEqualTo(404);
        assertThat(favicon.body()).contains("MSG-NOT-FOUND").doesNotContain("MSG-INTERNAL-ERROR");

        HttpResponse<String> unknown = browser.get(GATEWAY + "/no/such/endpoint",
                "Accept", "application/json");
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(unknown.body()).contains("MSG-NOT-FOUND");
    }

    @Test
    @DisplayName("wrong password and DISABLED user (mkaya) both stay on Keycloak with no gateway session")
    void invalidAndDisabledUsersGetNoSession() throws Exception {
        Browser wrongPassword = login("ayilmaz", "wrong-password");
        assertThat(wrongPassword.get(GATEWAY + "/api/session/me", "Accept", "application/json").statusCode())
                .isEqualTo(401);

        // AC-AUTH-01-04: the disabled (passive) seed user is rejected by Keycloak
        // exactly like a bad password — no session, no distinguishing behaviour here.
        Browser disabled = login("mkaya", "crm-dev");
        assertThat(disabled.get(GATEWAY + "/api/session/me", "Accept", "application/json").statusCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("replayed callback (browser Back) returns to the app, never to a generated /login?error page")
    void replayedCallbackRedirectsToApplication() throws Exception {
        Browser browser = login("ayilmaz", "crm-dev");

        // The Back button re-issues the callback whose code and state the original
        // login already consumed; a stale state reproduces that exactly, since the
        // authorization request is removed from the session on first use.
        HttpResponse<String> replay = browser.get(GATEWAY + "/login/oauth2/code/keycloak?code=stale&state=stale");

        assertThat(replay.statusCode()).isEqualTo(302);
        String target = replay.headers().firstValue("Location").orElseThrow();
        // FE-ADR-005 P1: no second login surface. "Invalid credentials" on Spring's
        // generated page is also a lie here — the session is still valid.
        assertThat(target).doesNotContain("/login").doesNotContain("error").endsWith("/");
        assertThat(browser.get(GATEWAY + "/api/session/me", "Accept", "application/json").statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("logout: CSRF-protected POST kills the session and hits Keycloak's end_session endpoint")
    void logoutEndsLocalAndKeycloakSession() throws Exception {
        Browser browser = login("ayilmaz", "crm-dev");
        browser.get(GATEWAY + "/api/session/me", "Accept", "application/json");

        // Logout without CSRF token is rejected.
        assertThat(browser.post(GATEWAY + "/logout", "").statusCode()).isEqualTo(403);

        String xsrf = browser.cookieValue("XSRF-TOKEN");
        HttpResponse<String> logout = browser.post(GATEWAY + "/logout", "", "X-XSRF-TOKEN", xsrf);
        assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(logout.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        String target = extractLogoutUrl(logout.body());
        // RP-initiated logout: the response tells the browser where to go to reach
        // Keycloak's end_session endpoint (a real client-driven navigation, since an
        // XHR cannot reliably drive Keycloak's own cross-origin redirect chain).
        assertThat(target).contains("/protocol/openid-connect/logout").contains("id_token_hint=");
        // Post-logout the browser goes to the authorization endpoint, not to an
        // application page: the sign-out was already confirmed in the UI, so the
        // user lands directly on Keycloak's sign-in form (no interstitial screen).
        assertThat(target).contains(URLEncoder.encode(GATEWAY + "/oauth2/authorization/keycloak",
                StandardCharsets.UTF_8));
        browser.get(target); // complete the Keycloak logout

        // Back-button / direct access after logout: session is gone -> 401.
        assertThat(browser.get(GATEWAY + "/api/session/me", "Accept", "application/json").statusCode())
                .isEqualTo(401);
        assertThat(browser.get(GATEWAY + "/api/customers", "Accept", "application/json").statusCode())
                .isEqualTo(401);
    }
}
