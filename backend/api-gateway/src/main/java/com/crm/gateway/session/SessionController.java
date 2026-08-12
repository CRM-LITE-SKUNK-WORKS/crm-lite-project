package com.crm.gateway.session;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session probe for the Angular shell (ADR-007). Returns WHO is logged in — never
 * any token. Requesting it also (re)issues the XSRF-TOKEN cookie via the
 * CsrfCookieFilter, so the SPA calls it once after every login/logout transition.
 * Anonymous callers receive 401 JSON from ApiAuthenticationEntryPoint.
 */
@RestController
public class SessionController {

    /**
     * {@code fullName} and {@code titleCode} are the header's display identity (the
     * mock's name + role line).
     *
     * <p>{@code titleCode} is a CODE, not display text — {@code "SALES_REP"}, localized
     * by the frontend catalogue exactly the way the wire value {@code "Male"} is
     * (scope §2.20 / §2.7). The name says so on purpose: a future consumer must not
     * print it at a user. The gateway itself neither translates nor validates it; it
     * relays whatever the ID token carries, which is what keeps UI text out of here.
     *
     * <p>BOTH ARE NULLABLE and that is a normal state, not a fault: a non-OIDC
     * principal has neither, and {@code titleCode} additionally depends on a Keycloak
     * user attribute + ID-token mapper that an un-reconciled realm may not carry yet
     * (see infra/docker-compose.yml keycloak-init). Callers fall back to
     * {@code username}.
     */
    public record SessionResponse(boolean authenticated, String username, String subject, List<String> roles,
            String fullName, String titleCode) {
    }

    /**
     * Post-login landing target (no UI is served by the gateway; Angular owns the
     * shell). Keeps the default oauth2Login success redirect from ending on a 404.
     */
    @GetMapping("/")
    public SessionResponse home(Authentication authentication) {
        return me(authentication);
    }

    @GetMapping("/api/session/me")
    public SessionResponse me(Authentication authentication) {
        String username = authentication.getName();
        String subject = null;
        String fullName = null;
        String titleCode = null;
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            subject = oidcUser.getSubject();
            if (oidcUser.getPreferredUsername() != null) {
                username = oidcUser.getPreferredUsername();
            }
            // `name` comes from the standard profile scope (already requested);
            // `titleCode` from the crm-bff user-title-code-in-id-token mapper. Either
            // can be absent — both getters answer null rather than throwing, which is
            // the contract.
            fullName = oidcUser.getFullName();
            titleCode = oidcUser.getClaimAsString("titleCode");
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        return new SessionResponse(true, username, subject, roles, fullName, titleCode);
    }
}
