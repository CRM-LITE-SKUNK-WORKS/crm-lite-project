package com.crm.product.testsecurity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Deterministic JWT decoding for integration tests (same pattern as
 * customer/account-service). The starter's real decoder (signature/issuer/audience
 * against Keycloak) is overridden here — an override path crm-security-starter
 * explicitly supports — so the FULL security filter chain, role mapping and audit
 * attribution still run against known tokens. Real-cryptography validation is
 * covered by the gateway's Keycloak Testcontainers suite and the starter's
 * validator tests.
 */
public final class TestSecurity {

    /** Bearer token accepted as the KR-8 operator; carries realm role crm-user. */
    public static final String OPERATOR_TOKEN = "test-operator-token";
    /** The Keycloak-style subject the operator token resolves to (audit expectations). */
    public static final String OPERATOR_SUBJECT = "7b2d4c1f-8e93-4a56-b0d7-41f8a2c95e33";

    /** Bearer token for an authenticated user WITHOUT the crm-user role. */
    public static final String NO_ROLE_TOKEN = "test-no-role-token";
    public static final String NO_ROLE_SUBJECT = "0c1d2e3f-4a5b-6c7d-8e9f-a0b1c2d3e4f5";

    public static final String ISSUER = "http://localhost:8180/realms/crm-lite";

    private TestSecurity() {
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class TestJwtDecoderConfiguration {

        @Bean
        JwtDecoder testJwtDecoder() {
            return token -> switch (token) {
                case OPERATOR_TOKEN -> jwt(OPERATOR_SUBJECT, List.of("crm-user"));
                case NO_ROLE_TOKEN -> jwt(NO_ROLE_SUBJECT, List.of());
                default -> throw new BadJwtException("Unknown test token");
            };
        }

        private static Jwt jwt(String subject, List<String> realmRoles) {
            return Jwt.withTokenValue("test-token-value")
                    .header("alg", "RS256")
                    .subject(subject)
                    .issuer(ISSUER)
                    .audience(List.of("crm-api"))
                    .claim("preferred_username", "test-user")
                    .claim("realm_access", Map.of("roles", realmRoles))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }
}
