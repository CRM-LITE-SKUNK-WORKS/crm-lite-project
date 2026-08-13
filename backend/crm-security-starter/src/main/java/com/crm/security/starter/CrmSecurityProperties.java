package com.crm.security.starter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource-server security settings shared by CRM Lite domain services (ADR-009).
 *
 * <p>{@code issuer} is the CANONICAL issuer — the exact {@code iss} value Keycloak
 * puts into tokens (pinned via KC_HOSTNAME, see infra/docker-compose.yml). JWKS may
 * be fetched from a different (e.g. compose-internal) URL via {@code jwkSetUri}
 * while issuer validation stays canonical (ADR-006 issuer/networking decision).
 */
@ConfigurationProperties(prefix = "crm.security")
public class CrmSecurityProperties {

    /** Canonical issuer, e.g. http://localhost:8180/realms/crm-lite. Enables the starter when set. */
    private String issuer;

    /** Optional JWKS override (e.g. http://keycloak:8180/realms/crm-lite/protocol/openid-connect/certs). */
    private String jwkSetUri;

    /** Audience every access token must carry (Keycloak client-scope audience mapper). */
    private String audience = "crm-api";

    /** Realm role required by the default filter chain (KR-8 single operator role). */
    private String requiredRole = "crm-user";

    /** Paths served without authentication by the default chain. */
    private List<String> permitPaths = new ArrayList<>(List.of("/actuator/health"));

    /** Client-credentials service account for calls with no live user request to
     *  borrow a token from (ADR-010 addendum) — see {@link ServiceAccount}. */
    private final ServiceAccount serviceAccount = new ServiceAccount();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    /** Effective JWKS endpoint: explicit override, else the Keycloak layout under the issuer. */
    public String resolveJwkSetUri() {
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return jwkSetUri;
        }
        return issuer + "/protocol/openid-connect/certs";
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(String requiredRole) {
        this.requiredRole = requiredRole;
    }

    public List<String> getPermitPaths() {
        return permitPaths;
    }

    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths;
    }

    public ServiceAccount getServiceAccount() {
        return serviceAccount;
    }

    /** Effective client-credentials token endpoint: explicit override, else the
     *  Keycloak layout under the issuer (mirrors {@link #resolveJwkSetUri()} — the
     *  canonical issuer is not reachable from inside a compose network either). */
    public String resolveServiceAccountTokenUri() {
        if (serviceAccount.tokenUri != null && !serviceAccount.tokenUri.isBlank()) {
            return serviceAccount.tokenUri;
        }
        return issuer + "/protocol/openid-connect/token";
    }

    /**
     * Unset (blank {@code clientId}) by default, so no service ever activates
     * {@link ServiceAccountTokenProvider} unless it deliberately opts in. Today that
     * is exactly account-service and product-service under the {@code async-sale}
     * profile — their SALE saga command handlers run on a Kafka listener thread,
     * where {@link BearerTokenPropagationInterceptor}'s normal request-bound path
     * has no user token to find (ADR-010 addendum, 2026-08-13).
     */
    public static class ServiceAccount {

        private String clientId;
        private String clientSecret;
        private String tokenUri;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }
    }
}
