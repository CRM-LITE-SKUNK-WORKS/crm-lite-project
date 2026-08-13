package com.crm.security.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class BearerTokenPropagationInterceptorTest {

    private final BearerTokenPropagationInterceptor interceptor = new BearerTokenPropagationInterceptor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithJwt(String tokenValue) {
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("subject-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_crm-user"))));
    }

    private static HttpHeaders intercept(BearerTokenPropagationInterceptor interceptor,
                                         MockClientHttpRequest request) throws IOException {
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));
        interceptor.intercept(request, new byte[0], execution);
        return request.getHeaders();
    }

    private HttpHeaders interceptAndCaptureHeaders(MockClientHttpRequest request) throws IOException {
        return intercept(interceptor, request);
    }

    @Test
    void propagatesBearerTokenFromCurrentJwtAuthentication() throws IOException {
        authenticateWithJwt("the-access-token");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://lookup-service/api/lookups/statuses/ACTV");

        HttpHeaders headers = interceptAndCaptureHeaders(request);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer the-access-token");
    }

    @Test
    void addsNoHeaderWithoutAuthentication() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://lookup-service/api/lookups/statuses/ACTV");

        HttpHeaders headers = interceptAndCaptureHeaders(request);

        assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void neverOverwritesAnExplicitAuthorizationHeader() throws IOException {
        authenticateWithJwt("the-access-token");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://lookup-service/api/lookups/statuses/ACTV");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer pre-set-token");

        HttpHeaders headers = interceptAndCaptureHeaders(request);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer pre-set-token");
    }

    // --- ADR-010 addendum: the async SALE saga's service-account fallback -------

    @Test
    void fallsBackToTheServiceAccountTokenWhenNoUserAuthenticationIsPresent() throws IOException {
        ServiceAccountTokenProvider serviceAccountTokenProvider = mock(ServiceAccountTokenProvider.class);
        when(serviceAccountTokenProvider.getToken()).thenReturn("the-service-account-token");
        BearerTokenPropagationInterceptor interceptor =
                new BearerTokenPropagationInterceptor(serviceAccountTokenProvider);
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://customer-service/api/customers/1001/addresses");

        HttpHeaders headers = intercept(interceptor, request);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer the-service-account-token");
    }

    @Test
    void prefersTheUserTokenOverTheServiceAccountWhenBothAreAvailable() throws IOException {
        ServiceAccountTokenProvider serviceAccountTokenProvider = mock(ServiceAccountTokenProvider.class);
        BearerTokenPropagationInterceptor interceptor =
                new BearerTokenPropagationInterceptor(serviceAccountTokenProvider);
        authenticateWithJwt("the-user-token");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://customer-service/api/customers/1001/addresses");

        HttpHeaders headers = intercept(interceptor, request);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer the-user-token");
        verifyNoInteractions(serviceAccountTokenProvider);
    }
}
