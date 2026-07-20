package com.crm.security.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtAuditorAwareTest {

    private final JwtAuditorAware auditorAware = new JwtAuditorAware();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsJwtSubjectWhenJwtAuthenticationPresent() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("11111111-2222-3333-4444-555555555555")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_crm-user"))));

        assertThat(auditorAware.getCurrentAuditor()).contains("11111111-2222-3333-4444-555555555555");
    }

    @Test
    void fallsBackToSystemWithoutAuthentication() {
        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }

    @Test
    void fallsBackToSystemForNonJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("someone", "credentials"));

        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }
}
