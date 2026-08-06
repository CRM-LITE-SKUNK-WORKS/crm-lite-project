package com.crm.order.order.controller;

import com.crm.order.order.dto.request.OrderCreateRequest;
import com.crm.order.order.dto.response.OrderResponse;
import com.crm.order.order.idempotency.IdempotencyContract;
import com.crm.order.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * FR-SALE-01/02 (ADR-016 §3). Exactly two endpoints.
 *
 * <p>Note what is absent, deliberately:
 * <ul>
 *   <li><b>No order-cancel endpoint.</b> KR-7 leaves cancellation out of phase and no
 *       AC moves an order out of MIDLWARE. CANCELLED is reached only by the
 *       orchestration's own compensation (ADR-016 §6).
 *   <li><b>No basket endpoints.</b> The basket is frontend/session state; the backend
 *       learns about a sale exactly once, at Submit. That is what makes
 *       AC-SALE-01-16 true by construction — there is nothing to abandon.
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
    @PostMapping
    public ResponseEntity<OrderResponse> submit(
            @RequestHeader(IdempotencyContract.HEADER) String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.submit(request, idempotencyKey));
    }

    /**
     * Order detail by its KR-12 public number. Two concrete justifications: it makes
     * the 201 verifiable, and it is what customer-service's KR-02 {@code orderNumber}
     * search (today a 501) will resolve against — that wiring is a separate
     * follow-up PR, exactly as the {@code accountNumber} search was.
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> detail(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getByOrderNumber(orderNumber));
    }
}
