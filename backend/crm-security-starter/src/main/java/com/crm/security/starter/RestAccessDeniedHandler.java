package com.crm.security.starter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 403 with the project error body for authenticated principals lacking the
 * required role (ADR-009). MSG-AUTH-FORBIDDEN is a documented project addition.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        SecurityErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                "MSG-AUTH-FORBIDDEN", "Access to this resource is denied",
                request.getRequestURI());
    }
}
