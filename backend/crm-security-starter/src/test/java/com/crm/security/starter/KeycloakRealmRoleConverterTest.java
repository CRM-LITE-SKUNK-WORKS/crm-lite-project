package com.crm.security.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("subject-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void mapsRealmRolesVerbatimWithRolePrefix() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("crm-user", "other-role"))));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_crm-user", "ROLE_other-role");
    }

    @Test
    void missingRealmAccessYieldsNoAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("scope", "openid"));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void malformedRolesEntryYieldsNoAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", "not-a-list")));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void clientRolesAreNotMapped() {
        Jwt jwt = jwtWithClaims(Map.of(
                "resource_access", Map.of("crm-bff", Map.of("roles", List.of("client-role")))));

        assertThat(converter.convert(jwt)).isEmpty();
    }
}
