package com.crm.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * OIDC RP-initiated logout against Keycloak (ADR-007): after the local session is
 * invalidated, the response tells the browser where to go to finish RP-initiated
 * logout at Keycloak's end_session endpoint with id_token_hint, so the SSO session
 * dies too, before landing straight back on Keycloak's sign-in page.
 *
 * <p>The post-logout target is this gateway's own authorization endpoint, NOT an
 * application page: signing out is a decision the user has already confirmed in
 * the UI, so an intermediate "you have been signed out" screen would only add a
 * click. Keycloak clears its SSO cookie, redirects here, and this endpoint starts
 * a fresh authorization request — which, with the SSO session now gone, lands on
 * the login form. There is no loop risk for the same reason: the session that
 * could have auto-completed that request is exactly the one just destroyed.
 *
 * <p>Built manually (Keycloak's endpoint layout is deterministic) instead of
 * OidcClientInitiatedLogoutSuccessHandler because the provider is configured with
 * explicit endpoints, not OIDC discovery — without discovery metadata that handler
 * silently skips the Keycloak logout, leaving the SSO session alive.
 *
 * <p>The target is returned as JSON, not a 302: {@code POST /logout} is issued by
 * Angular's {@code HttpClient} (an XHR — the only way to attach the CSRF header),
 * and an XHR cannot reliably drive a cross-origin redirect to Keycloak (it can
 * stall, neither completing nor erroring), so Keycloak's own SSO cookie never got
 * cleared. Returning the URL lets the frontend do a real top-level
 * {@code window.location.replace(logoutUrl)} instead.
 */
@Component
public class KeycloakLogoutSuccessHandler implements LogoutSuccessHandler {

    /** Response DTO: the URL the browser must navigate to (top-level) to finish RP-initiated logout. */
    public record LogoutResponse(String logoutUrl) {
    }

    /**
     * Where Keycloak sends the browser once the SSO session is gone. Spring
     * Security's own authorization-request endpoint for the keycloak registration
     * — hitting it starts the login flow, so the user lands on the sign-in form.
     */
    private static final String AUTHORIZATION_ENDPOINT = "/oauth2/authorization/keycloak";

    private final String endSessionEndpoint;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public KeycloakLogoutSuccessHandler(
            @Value("${crm.security.keycloak.base-url}") String keycloakBaseUrl,
            @Value("${crm.security.keycloak.realm}") String realm) {
        this.endSessionEndpoint = keycloakBaseUrl + "/realms/" + realm + "/protocol/openid-connect/logout";
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        // Derived from the request (forward-header-corrected, server.forward-headers-strategy:
        // framework), the same way {baseUrl} resolves the OAuth2 login redirect-uri: :4200
        // through the nginx-forwarded frontend origin, :8080 on a direct hit.
        String postLogoutRedirectUri = ServletUriComponentsBuilder.fromContextPath(request)
                .path(AUTHORIZATION_ENDPOINT)
                .build()
                .toUriString();

        String target = postLogoutRedirectUri;
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            target = UriComponentsBuilder.fromUriString(endSessionEndpoint)
                    .queryParam("id_token_hint", oidcUser.getIdToken().getTokenValue())
                    .queryParam("post_logout_redirect_uri",
                            URLEncoder.encode(postLogoutRedirectUri, StandardCharsets.UTF_8))
                    .build(true)
                    .toUriString();
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(new LogoutResponse(target)));
    }
}
