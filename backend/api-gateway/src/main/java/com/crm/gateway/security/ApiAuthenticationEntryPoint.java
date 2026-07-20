package com.crm.gateway.security;

import com.crm.gateway.exception.GatewayErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401 JSON for unauthenticated /api/** calls (Angular reacts by starting the login
 * redirect). Browser page navigation is NOT handled here — it falls through to the
 * oauth2Login redirect entry point.
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        GatewayErrorResponse body = GatewayErrorResponse.of(HttpStatus.UNAUTHORIZED,
                "MSG-AUTH-UNAUTHORIZED", "Authentication is required", request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
