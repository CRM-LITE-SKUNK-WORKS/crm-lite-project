package com.crm.order.order.controller;

import com.crm.observability.starter.MdcKeys;
import com.crm.order.order.dto.request.OrderCreateRequest;
import com.crm.order.order.dto.response.OrderResponse;
import com.crm.order.order.idempotency.IdempotencyContract;
import com.crm.order.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-SALE-01/02 (ADR-016 §3): the LEGACY synchronous sale, plus the order detail read.
 *
 * <p><b>{@code POST /api/orders} is deprecated (ADR-018 §10).</b> The live SALE flow is
 * the asynchronous contract in {@link SaleDraftController} — draft → submit → poll. This
 * endpoint stays in the build for exactly two reasons and no others: the currently merged
 * frontend still calls it, and it is the rollback route if the asynchronous cutover has
 * to be switched off. It is removed by the frontend PR that moves the browser over.
 *
 * <p><b>One sale can never enter both routes.</b> Not by convention — by construction:
 * this endpoint always creates a NEW order, already MIDLWARE, and
 * {@code POST /api/orders/{orderNumber}/submit} accepts only a WAIT draft. There is no
 * order that both can act on, and no request that reaches both.
 *
 * <p>Note what is absent, deliberately:
 * <ul>
 *   <li><b>No order-cancel endpoint.</b> KR-7 leaves cancellation out of phase and no
 *       AC moves an order out of MIDLWARE. CANCELLED is reached only by the
 *       orchestration's own compensation, by an abandoned draft, or by an expired one
 *       (ADR-016 §6, ADR-018 §6) — never by a user cancelling a submitted order.
 *   <li><b>No basket endpoints.</b> The basket is still frontend/session state; the
 *       backend learns about it exactly once, at Submit. The draft added by ADR-018
 *       creates the ORDER early, not the basket — which is why an abandoned draft still
 *       has nothing to process (AC-SALE-01-16).
 *   <li><b>No order list.</b> No FR asks for one, and inventing a list contract means
 *       inventing sorting, filtering and pagination rules an analyst has not written.
 * </ul>
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * AC-SALE-01-15: the single Submit-Order command; the whole basket in one body.
     *
     * <p>{@code Idempotency-Key} is validated and its replay/conflict semantics are
     * fully handled by {@link com.crm.order.order.idempotency.IdempotencyKeyFilter}
     * BEFORE this method ever runs — {@code required = true} here is defence in depth
     * (Spring's own {@code MissingRequestHeaderException} → 400), not the primary
     * enforcement point. The same key value is also the stable operation identifier
     * forwarded to product-service so a re-entered {@link OrderService#submit} (the
     * filter's own crash-recovery gap, documented on {@code IdempotencyPersistence})
     * cannot create a second set of products (ADR-015 idempotency addendum).
     */
    @Deprecated(since = "ADR-018", forRemoval = true)
    @PostMapping
    public ResponseEntity<OrderResponse> submit(
            @RequestHeader(IdempotencyContract.HEADER) String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request) {
        try {
            // orderNumber is unknown until step 1 (OrderPersistence#persistOrder) sets
            // it into the MDC itself — this clears it for whatever runs next on this
            // (pooled) thread. See MdcKeys for why sagaId/eventId are not set anywhere.
            return ResponseEntity.status(HttpStatus.CREATED).body(orderService.submit(request, idempotencyKey));
        } finally {
            MDC.remove(MdcKeys.ORDER_NUMBER);
        }
    }

    /**
     * Order detail by its KR-12 public number. Two concrete justifications: it makes
     * the 201 verifiable, and it is what customer-service's KR-02 {@code orderNumber}
     * search (today a 501) will resolve against — that wiring is a separate
     * follow-up PR, exactly as the {@code accountNumber} search was.
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> detail(@PathVariable String orderNumber) {
        MDC.put(MdcKeys.ORDER_NUMBER, orderNumber);
        try {
            return ResponseEntity.ok(orderService.getByOrderNumber(orderNumber));
        } finally {
            MDC.remove(MdcKeys.ORDER_NUMBER);
        }
    }
}
