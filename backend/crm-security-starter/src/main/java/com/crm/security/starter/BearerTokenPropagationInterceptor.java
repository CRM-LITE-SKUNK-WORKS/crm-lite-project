package com.crm.security.starter;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Propagates the CURRENT request's user bearer token onto an outbound call
 * (ADR-010: customer-service → lookup-service keeps the end-user identity).
 * Opt-in per RestClient — never registered globally, so external-system
 * integrations (mernis-stub) stay token-free by default.
 *
 * <p>Falls back to a {@link ServiceAccountTokenProvider} — when one is configured —
 * for a call that has no user request to propagate from at all (ADR-010 addendum,
 * 2026-08-13): the async SALE saga's command handlers run on a Kafka listener
 * thread, not inside an HTTP request, so {@code SecurityContextHolder} never holds a
 * {@link JwtAuthenticationToken} there. Every other caller of this interceptor is
 * unaffected — the fallback is {@code null} unless the owning service opted in.
 */
public class BearerTokenPropagationInterceptor implements ClientHttpRequestInterceptor {

    private final ServiceAccountTokenProvider serviceAccountTokenProvider;

    public BearerTokenPropagationInterceptor() {
        this(null);
    }

    public BearerTokenPropagationInterceptor(ServiceAccountTokenProvider serviceAccountTokenProvider) {
        this.serviceAccountTokenProvider = serviceAccountTokenProvider;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                request.getHeaders().setBearerAuth(jwtAuthentication.getToken().getTokenValue());
            } else if (serviceAccountTokenProvider != null) {
                request.getHeaders().setBearerAuth(serviceAccountTokenProvider.getToken());
            }
        }
        return execution.execute(request, body);
    }
}
