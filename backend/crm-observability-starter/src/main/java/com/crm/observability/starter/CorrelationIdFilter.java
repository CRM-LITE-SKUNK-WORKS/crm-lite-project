package com.crm.observability.starter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code X-Correlation-Id} (or mints a fresh UUID) and puts it in the SLF4J
 * MDC for the lifetime of the request, so every JSON log line the request
 * produces — including a 401/403 rejected before any business code runs — carries
 * the same id. Echoed back on the response so a caller (or the frontend) can log
 * the same value.
 *
 * <p>Registered at {@code Ordered.HIGHEST_PRECEDENCE} ({@link CrmObservabilityAutoConfiguration})
 * — deliberately BEFORE Spring Security's filter chain, unlike a typical
 * request-scoped filter, so the correlation id exists even for requests security
 * rejects before reaching a controller.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // MDC is thread-local and Tomcat's worker threads are pooled and reused —
            // never leave a stale value for the next, unrelated request on this thread.
            MDC.remove(MdcKeys.CORRELATION_ID);
        }
    }
}
