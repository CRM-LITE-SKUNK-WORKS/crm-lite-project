package com.crm.order.order.idempotency;

import com.crm.order.common.exception.ErrorResponse;
import com.crm.order.common.exception.MessageKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * The whole {@code POST /api/orders} idempotency contract lives here, at the servlet
 * layer, rather than in {@code OrderController}/{@code OrderServiceImpl} — deliberately:
 * a REPLAYED response must be byte-for-byte the response {@code GlobalExceptionHandler}
 * (or the controller's own 201 path) actually produced the first time, including for
 * every FAILURE outcome (400/404/409/503/500), and only something that observes the
 * response AFTER the whole MVC + exception-handling pipeline has run can capture that
 * without duplicating GlobalExceptionHandler's own mapping logic here.
 *
 * <p>Runs AFTER Spring Security's filter chain (crm-security-starter's chain registers
 * at {@code SecurityProperties.DEFAULT_FILTER_ORDER = -100}; a plain filter bean
 * defaults to {@code Ordered.LOWEST_PRECEDENCE}) — so an unauthenticated or
 * under-privileged request is rejected with 401/403 before any reservation is made,
 * exactly as it would be for every other endpoint.
 *
 * <p>Only touches {@code POST /api/orders}: {@code GET /api/orders/{orderNumber}} is a
 * read and every other route is untouched (requirement 8's "current HTTP behaviour
 * except for the documented idempotency contract").
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    private static final String ORDERS_PATH = "/api/orders";

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && ORDERS_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String idempotencyKey = request.getHeader(IdempotencyContract.HEADER);
        if (!IdempotencyContract.isValidKey(idempotencyKey)) {
            writeError(response, HttpStatus.BAD_REQUEST, MessageKeys.IDEMPOTENCY_KEY_REQUIRED,
                    "The Idempotency-Key header is required and must be a UUID", request.getRequestURI());
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = idempotencyService.normalizedRequestHash(cachedRequest.cachedBody());

        IdempotencyDecision decision = idempotencyService.reserveOrReplay(idempotencyKey, requestHash);
        switch (decision) {
            case IdempotencyDecision.Replay replay -> {
                response.setStatus(replay.status());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader(IdempotencyContract.REPLAYED_HEADER, "true");
                response.getWriter().write(replay.body());
            }
            case IdempotencyDecision.Conflict conflict -> writeError(response, HttpStatus.CONFLICT,
                    conflict.messageKey(), conflict.message(), request.getRequestURI());
            case IdempotencyDecision.Proceed proceed -> proceed(cachedRequest, response, chain, proceed.reservationId());
        }
    }

    private void proceed(CachedBodyHttpServletRequest request, HttpServletResponse response, FilterChain chain,
                         long reservationId) throws ServletException, IOException {
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, cachedResponse);
        } finally {
            byte[] body = cachedResponse.getContentAsByteArray();
            String responseBody = new String(body, StandardCharsets.UTF_8);
            idempotencyService.complete(reservationId, cachedResponse.getStatus(), responseBody,
                    extractOrderNumber(cachedResponse.getStatus(), body));
            // ContentCachingResponseWrapper buffers internally and sends nothing to the
            // real client until this is called — must run even if chain.doFilter threw.
            cachedResponse.copyBodyToResponse();
        }
    }

    /** Only a genuine 201 carries an order number worth recording (class javadoc). */
    private String extractOrderNumber(int status, byte[] body) {
        if (status != HttpStatus.CREATED.value() || body.length == 0) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body).get("orderNumber");
            return node == null ? null : node.asText(null);
        } catch (IOException unreadable) {
            return null;
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String messageKey, String message,
                            String path) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .messageKey(messageKey)
                .message(message)
                .path(path)
                .build();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
