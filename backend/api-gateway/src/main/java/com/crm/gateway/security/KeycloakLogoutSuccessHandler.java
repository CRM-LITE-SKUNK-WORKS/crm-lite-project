package com.crm.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OIDC RP-initiated logout against Keycloak (ADR-007): after the local session is
 * invalidated, the browser is sent to Keycloak's end_session endpoint with
 * id_token_hint so the SSO session dies too, then back to the application.
 *
 * <p>Built manually (Keycloak's endpoint layout is deterministic) instead of
 * OidcClientInitiatedLogoutSuccessHandler because the provider is configured with
 * explicit endpoints, not OIDC discovery — without discovery metadata that handler
 * silently skips the Keycloak logout, leaving the SSO session alive.
 */
@Component
public class KeycloakLogoutSuccessHandler implements LogoutSuccessHandler {

    private final String endSessionEndpoint;
    private final String postLogoutRedirectUri;

    public KeycloakLogoutSuccessHandler(
            @Value("${crm.security.keycloak.base-url}") String keycloakBaseUrl,
            @Value("${crm.security.keycloak.realm}") String realm,
            @Value("${crm.security.post-logout-redirect-uri:http://localhost:8080/}") String postLogoutRedirectUri) {
        this.endSessionEndpoint = keycloakBaseUrl + "/realms/" + realm + "/protocol/openid-connect/logout";
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        String target = postLogoutRedirectUri;
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            target = UriComponentsBuilder.fromUriString(endSessionEndpoint)
                    .queryParam("id_token_hint", oidcUser.getIdToken().getTokenValue())
                    .queryParam("post_logout_redirect_uri",
                            URLEncoder.encode(postLogoutRedirectUri, StandardCharsets.UTF_8))
                    .build(true)
                    .toUriString();
        }
        response.sendRedirect(target);
    }
}
