package com.crm.security.starter;

import java.time.Instant;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * A client-credentials access token for calls that have no live user request to
 * borrow a token from (ADR-010 addendum, 2026-08-13) — concretely, the async SALE
 * saga's command handlers, which run on a Kafka listener thread rather than inside
 * an HTTP request. Fetched once, cached, and refreshed shortly before expiry: a
 * Keycloak round trip on every outbound call would make the saga's own commit-time
 * budget depend on the token endpoint's latency for no reason.
 *
 * <p>Registered only when {@code crm.security.service-account.client-id} is set
 * ({@link CrmResourceServerAutoConfiguration}) — every service that never opts in
 * (and the SAME two services outside the {@code async-sale} profile) never creates
 * this bean, so nothing about the request-bound calls ADR-010 already documents
 * changes.
 */
public class ServiceAccountTokenProvider {

    /** Refresh this long before the token's stated expiry — network latency and
     *  clock drift must never let an in-flight call carry an already-expired token. */
    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 10;

    private final RestClient tokenClient;
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiresAt = Instant.EPOCH;

    public ServiceAccountTokenProvider(String tokenUri, String clientId, String clientSecret) {
        this.tokenClient = RestClient.create(tokenUri);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** The current access token — cached, transparently refreshed once it is close
     *  enough to expiry that a caller should not be handed it. */
    public String getToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiresAt)) {
            return cachedToken;
        }
        return refresh();
    }

    private synchronized String refresh() {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiresAt)) {
            return cachedToken;  // another thread refreshed it while this one waited
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<String, Object> response = tokenClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        if (response == null || !(response.get("access_token") instanceof String token)) {
            throw new IllegalStateException("Keycloak client-credentials response carried no access_token");
        }
        long expiresInSeconds = response.get("expires_in") instanceof Number n ? n.longValue() : 60;

        cachedToken = token;
        cachedTokenExpiresAt = Instant.now().plusSeconds(Math.max(1, expiresInSeconds - EXPIRY_SAFETY_MARGIN_SECONDS));
        return cachedToken;
    }
}
