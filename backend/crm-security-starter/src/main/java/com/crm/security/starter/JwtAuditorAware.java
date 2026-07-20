package com.crm.security.starter;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Audit actor per ADR-004/ADR-009: the Keycloak {@code sub} claim of the current
 * request's JWT; the constant {@code system} for genuinely unauthenticated technical
 * contexts (migrations, seeds, startup work).
 */
public class JwtAuditorAware implements AuditorAware<String> {

    public static final String SYSTEM_ACTOR = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.of(jwtAuthentication.getToken().getSubject());
        }
        return Optional.of(SYSTEM_ACTOR);
    }
}
