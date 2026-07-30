package com.crm.gateway.config;

import com.crm.gateway.security.ApiAuthenticationEntryPoint;
import com.crm.gateway.security.ApiAccessDeniedHandler;
import com.crm.gateway.security.CsrfCookieFilter;
import com.crm.gateway.security.KeycloakLogoutSuccessHandler;
import com.crm.gateway.security.SpaCsrfTokenRequestHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * BFF security for the WebMVC gateway (ADR-007).
 *
 * <p>The gateway is the OAuth2 Authorization Code + PKCE client against Keycloak
 * (ADR-006) and the ONLY place browser cookies/sessions exist. Angular receives an
 * HttpOnly session cookie plus a readable XSRF-TOKEN cookie — never a token. The
 * TokenRelay route filter (see config-repo/api-gateway.yml) swaps the session for
 * the user's Keycloak access token before proxying to domain services.
 *
 * <p>Servlet stack: this gateway uses spring-cloud-starter-gateway-server-webmvc, so
 * security is the servlet {@link SecurityFilterChain} + {@link HttpSecurity}, NOT the
 * reactive SecurityWebFilterChain / ServerHttpSecurity used by the WebFlux gateway.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    public static final String REQUIRED_AUTHORITY = "ROLE_crm-user";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   KeycloakLogoutSuccessHandler keycloakLogoutSuccessHandler,
                                                   @Value("${crm.security.cookie-secure:false}") boolean cookieSecure)
            throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // Angular reads XSRF-TOKEN and echoes X-XSRF-TOKEN (ADR-008). Secure flag is
        // environment-driven; SameSite=Lax matches the session cookie policy.
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.secure(cookieSecure).sameSite("Lax").path("/"));

        ApiAuthenticationEntryPoint apiEntryPoint = new ApiAuthenticationEntryPoint();
        ApiAccessDeniedHandler apiAccessDeniedHandler = new ApiAccessDeniedHandler();

        // NO saved-request replay: nothing this gateway serves is a page a human
        // could be sent back to after logging in.
        //
        // ExceptionTranslationFilter saves the current request BEFORE invoking the
        // entry point — for /api/** that happens even though ApiAuthenticationEntryPoint
        // answers 401 JSON rather than redirecting. Since nginx proxies /api, /oauth2,
        // /login and /logout here (FE-ADR-004/010), the SPA on :4200 and a raw API tab
        // on :8080 share ONE session, so anything left in the cache hijacked the SPA's
        // next login: first observed as …/api/products/2?continue, then — once those
        // paths were excluded — as …/favicon.ico?continue, because a browser also
        // fetches sub-resources for the tab it is showing (`continue` is
        // HttpSessionRequestCache's default matching parameter).
        //
        // A path blacklist cannot close that: sub-resources are open-ended. Filtering
        // on "top-level navigation" alone cannot either — a human CAN type an API URL
        // into the address bar, which is a navigation. What actually settles it is that
        // the cache has no legitimate input here at all: /api/** and /actuator/** are
        // JSON, "/" is the JSON session probe (and already the default target), Swagger
        // is permitAll so it is never saved, and every other path is unmapped. SPA deep
        // links never reach this gateway — nginx serves index.html statically and
        // Angular's router owns the URL.
        //
        // REVISIT if either becomes true: (a) this gateway starts serving navigable
        // HTML (SSR, or hosting the SPA itself instead of nginx), (b) Swagger UI is put
        // behind authentication. Both would give the cache a real input again.
        http
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                // Force the deferred CSRF token to be resolved so the cookie is
                // actually written on the response (Spring Security SPA pattern).
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/login/**", "/oauth2/**", "/error").permitAll()
                        // Unified Swagger UI (ADR-012): docs pages and OpenAPI JSON are schema
                        // metadata, not business data — viewable without logging in. Actually
                        // executing "Try it out" still hits /api/** below, which stays gated.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Session probe: any authenticated session may ask "who am I".
                        .requestMatchers("/api/session/me").authenticated()
                        // Every business API needs the explicit KR-8 operator role,
                        // never bare authenticated() (ADR-009).
                        .requestMatchers("/api/**").hasAuthority(REQUIRED_AUTHORITY)
                        .requestMatchers("/actuator/**").hasAuthority(REQUIRED_AUTHORITY)
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        // API calls get 401/403 JSON (Angular reacts by navigating to
                        // login); non-API browser navigation falls through to the
                        // oauth2Login redirect entry point.
                        .defaultAuthenticationEntryPointFor(
                                apiEntryPoint,
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .oauth2Login(login -> login
                        .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(keycloakAuthoritiesMapper()))
                        .successHandler(loginSuccessHandler())
                        .failureHandler(replayedCallbackFailureHandler()))
                .logout(logout -> logout
                        // POST /logout, CSRF-protected. Ends the gateway session AND
                        // the Keycloak SSO session (RP-initiated logout, ADR-007).
                        .logoutSuccessHandler(keycloakLogoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN"));
        return http.build();
    }

    /**
     * Post-login landing (ADR-007/008): ALWAYS this application's root, derived from
     * the request itself — exactly how {@code {baseUrl}} resolves the OAuth2
     * redirect-uri and how {@link com.crm.gateway.security.KeycloakLogoutSuccessHandler}
     * builds its post-logout URI (forward-header-corrected via
     * {@code server.forward-headers-strategy: framework}).
     *
     * <p>That derivation is the point: a login arriving through the nginx-forwarded
     * frontend origin lands on {@code http://localhost:4200/}, where Angular's
     * {@code path: '' → redirectTo: 'customers'} takes over; a direct hit on the
     * gateway lands on {@code http://localhost:8080/} (the session probe). The
     * gateway therefore never hardcodes the frontend origin — and does not rely on a
     * bare relative "/" being resolved by the container's redirect strategy.
     *
     * <p>Deliberately NOT {@code SavedRequestAwareAuthenticationSuccessHandler}: there
     * is no request cache to consult (see the chain above), so the plain
     * target-URL handler states that intent instead of hiding a no-op behind a
     * saved-request-aware name.
     */
    private AuthenticationSuccessHandler loginSuccessHandler() {
        return new SimpleUrlAuthenticationSuccessHandler() {
            @Override
            protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
                return ServletUriComponentsBuilder.fromContextPath(request).path("/").build().toUriString();
            }
        };
    }

    /**
     * A failed authorization-code callback is, in practice, a REPLAYED one: the
     * browser's Back button returns to /login/oauth2/code/keycloak, whose code and
     * state were both consumed by the original login, so the exchange fails
     * (invalid_grant / authorization_request_not_found).
     *
     * <p>Spring Security's default sends that to {@code /login?error} — its GENERATED
     * login page, which is exactly the second login surface this application must not
     * have (FE-ADR-005 P1: credentials are typed ONLY on Keycloak's page). Worse, it
     * reads "Invalid credentials", which is untrue: the user is usually still logged in.
     *
     * <p>Redirecting to "/" is correct for both outcomes — a surviving session lands
     * back in the app (the common Back-button case), and an anonymous one falls through
     * to the normal oauth2Login entry point, which starts a FRESH authorization request,
     * so a stale state cannot loop.
     */
    private AuthenticationFailureHandler replayedCallbackFailureHandler() {
        return (request, response, exception) -> {
            // Not silently swallowed: a genuine misconfiguration (bad client, scope,
            // issuer) also lands here and would otherwise look like a bare redirect.
            LOG.debug("OAuth2 login callback failed ({}), redirecting to /", exception.getMessage());
            response.sendRedirect("/");
        };
    }

    /**
     * Maps Keycloak realm roles from the ID token ({@code realm_access.roles}, put
     * there by the crm-bff client mapper in the realm import) onto the session
     * authorities, so route rules can require ROLE_crm-user.
     */
    private GrantedAuthoritiesMapper keycloakAuthoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            for (GrantedAuthority authority : authorities) {
                if (authority instanceof OidcUserAuthority oidcAuthority) {
                    Map<String, Object> realmAccess = oidcAuthority.getIdToken().getClaimAsMap("realm_access");
                    if (realmAccess != null && realmAccess.get("roles") instanceof Iterable<?> roles) {
                        for (Object role : roles) {
                            if (role instanceof String roleName) {
                                mapped.add(new SimpleGrantedAuthority("ROLE_" + roleName));
                            }
                        }
                    }
                }
            }
            return mapped;
        };
    }
}
