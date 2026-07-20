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
 */
public class BearerTokenPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                request.getHeaders().setBearerAuth(jwtAuthentication.getToken().getTokenValue());
            }
        }
        return execution.execute(request, body);
    }
}
