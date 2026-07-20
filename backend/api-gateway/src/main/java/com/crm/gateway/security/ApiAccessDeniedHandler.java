package com.crm.gateway.security;

import com.crm.gateway.exception.GatewayErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 403 JSON for authenticated sessions lacking ROLE_crm-user, and for CSRF
 * rejections on unsafe methods (ADR-008/009). The messageKey distinguishes the two
 * so the frontend can react differently (re-fetch the XSRF token vs. hard failure).
 */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String messageKey = accessDeniedException instanceof CsrfException
                ? "MSG-AUTH-CSRF-REJECTED"
                : "MSG-AUTH-FORBIDDEN";
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        GatewayErrorResponse body = GatewayErrorResponse.of(HttpStatus.FORBIDDEN,
                messageKey, accessDeniedException.getMessage(), request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
