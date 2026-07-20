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

    public record SessionResponse(boolean authenticated, String username, String subject, List<String> roles) {
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
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            subject = oidcUser.getSubject();
            if (oidcUser.getPreferredUsername() != null) {
                username = oidcUser.getPreferredUsername();
            }
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        return new SessionResponse(true, username, subject, roles);
    }
}
