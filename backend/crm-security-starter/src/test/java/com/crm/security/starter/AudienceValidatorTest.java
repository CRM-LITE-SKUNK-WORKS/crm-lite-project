package com.crm.security.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("crm-api");

    private static Jwt jwtWithAudience(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("subject-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }

    @Test
    void acceptsTokenContainingExpectedAudience() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience(List.of("crm-api", "account")));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience(List.of("account")));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).first()
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
    }

    @Test
    void rejectsTokenWithoutAudience() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithAudience(null));

        assertThat(result.hasErrors()).isTrue();
    }
}
