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
 * <p>Covers the THREE state-changing sale commands and nothing else (ADR-018 §3):
 * {@code POST /api/orders} (legacy synchronous), {@code POST /api/orders/drafts} and
 * {@code POST /api/orders/{orderNumber}/submit}. {@code GET .../status} and
 * {@code DELETE .../draft} are excluded on purpose — a read has nothing to replay, and
 * DELETE of a named resource is already idempotent by identity.
 *
 * <p><b>The three commands take three independent keys.</b> The sale's identity is the
 * order number (which is also the sagaId); the key identifies one HTTP attempt. Reusing
 * one key across draft creation and submission is therefore a client error, and it is
 * caught rather than tolerated: the new endpoints hash the request PATH along with the
 * body, so the same key on a different endpoint is a
 * {@code MSG-IDEMPOTENCY-KEY-CONFLICT} instead of a replay of the wrong response.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    private static final String ORDERS_PATH = "/api/orders";
    private static final String DRAFTS_PATH = "/api/orders/drafts";
    /** {@code /api/orders/{orderNumber}/submit} — the order number is a KR-12 10-digit string. */
    private static final java.util.regex.Pattern SUBMIT_PATH =
            java.util.regex.Pattern.compile("^/api/orders/[^/]+/submit$");

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !(ORDERS_PATH.equals(uri) || DRAFTS_PATH.equals(uri) || SUBMIT_PATH.matcher(uri).matches());
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
        // The legacy endpoint keeps its original body-only hash. Changing it would make
        // every key already recorded in a live idempotency_key table hash differently
        // after a deploy, turning an in-flight retry into a spurious 409 — a migration
        // cost with no benefit for a route this ADR is retiring anyway.
        String uri = request.getRequestURI();
        String requestHash = ORDERS_PATH.equals(uri)
                ? idempotencyService.normalizedRequestHash(cachedRequest.cachedBody())
                : idempotencyService.scopedRequestHash(uri, cachedRequest.cachedBody());

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

    /**
     * Only a successful create or accept carries an order number worth recording: 201
     * from the legacy submit and from draft creation, 202 from the asynchronous submit.
     * Every other status is a failure whose body has nothing to record.
     */
    private String extractOrderNumber(int status, byte[] body) {
        boolean carriesOrderNumber = status == HttpStatus.CREATED.value()
                || status == HttpStatus.ACCEPTED.value();
        if (!carriesOrderNumber || body.length == 0) {
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
