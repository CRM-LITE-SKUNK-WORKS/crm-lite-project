package com.crm.security.starter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 401 with the project error body for missing/invalid bearer tokens.
 * MSG-AUTH-UNAUTHORIZED is a documented project-addition message key (ADR-009),
 * same category as MSG-INTERNAL-ERROR.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        SecurityErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                "MSG-AUTH-UNAUTHORIZED", "Full authentication is required to access this resource",
                request.getRequestURI());
    }
}
