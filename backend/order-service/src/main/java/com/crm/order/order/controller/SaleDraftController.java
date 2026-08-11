package com.crm.order.order.controller;

import com.crm.observability.starter.MdcKeys;
import com.crm.order.order.dto.request.OrderDraftCreateRequest;
import com.crm.order.order.dto.request.OrderSubmitRequest;
import com.crm.order.order.dto.response.OrderStatusResponse;
import com.crm.order.order.idempotency.IdempotencyContract;
import com.crm.order.order.service.SaleDraftService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The asynchronous SALE API (ADR-018 §6). Additive: {@link OrderController}'s
 * synchronous {@code POST /api/orders} is untouched and still live for the currently
 * merged frontend.
 *
 * <pre>
 *   POST   /api/orders/drafts                  201  a WAIT order with its KR-12 number
 *   DELETE /api/orders/{orderNumber}/draft     204  cancel before submit (WAIT only)
 *   POST   /api/orders/{orderNumber}/submit    202  WAIT -> MIDLWARE, saga starts
 *   GET    /api/orders/{orderNumber}/status    200  business + processing status
 * </pre>
 *
 * <p><b>Why the draft endpoint exists at all.</b> The analyst's clarification of
 * 2026-08-10 — "records should already be created in the relevant tables [but] because
 * the order has not yet been completed/approved their status is not MIDLWARE... therefore
 * the Order ID may be displayed at this point" — makes the Order Number a fact the Submit
 * screen shows BEFORE submission. That requires the order to exist before submission,
 * which supersedes ADR-016 §8.3's "order number only after submission".
 *
 * <p><b>Why 202 and not 201.</b> A 201 promises a created resource whose state is what
 * the body says. After Submit the products do not exist yet, so a 201 carrying the order
 * would be describing a sale that has not happened. 202 says exactly what is true: the
 * request is accepted, durable, and being worked on — and the {@code Location} header
 * says where to find out how it went.
 *
 * <p><b>Idempotency-Key is per HTTP command, not per sale</b> (ADR-018 §3). Draft
 * creation and submission are two independent commands and take two independent keys;
 * the sale's own identity is the order number, which is also the sagaId. This supersedes
 * ADR-017 §5.1, where the sale had no identity of its own before Submit and had to borrow
 * the client's key.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class SaleDraftController {

    private final SaleDraftService saleDraftService;

    /**
     * Start New Sale. The {@code Idempotency-Key} is validated and its replay/conflict
     * semantics fully handled by {@link com.crm.order.order.idempotency.IdempotencyKeyFilter}
     * before this method runs; {@code required = true} here is defence in depth, so a
     * missing header is a clean 400 rather than a null.
     *
     * <p>A replayed key returns the FIRST response — the same order number — so a client
     * that retried a lost request gets one draft, not two.
     */
    @PostMapping("/drafts")
    public ResponseEntity<OrderStatusResponse> createDraft(
            @RequestHeader(IdempotencyContract.HEADER) String idempotencyKey,
            @Valid @RequestBody OrderDraftCreateRequest request) {
        try {
            OrderStatusResponse draft = saleDraftService.createDraft(request);
            return ResponseEntity.created(statusUri(draft.orderNumber())).body(draft);
        } finally {
            // The order number is put into the MDC by SaleDraftPersistence once it is
            // known; this clears it for whatever runs next on this pooled thread.
            MDC.remove(MdcKeys.ORDER_NUMBER);
        }
    }

    /**
     * Cancel before submit. Idempotent, and WAIT-only: a submitted (MIDLWARE) order is
     * refused with 409, which is what stops this from quietly becoming the post-submit
     * order cancellation KR-7 keeps out of phase.
     *
     * <p>No {@code Idempotency-Key}: DELETE of a specific resource is already idempotent
     * by identity — the second call finds the draft cancelled and says so.
     */
    @DeleteMapping("/{orderNumber}/draft")
    public ResponseEntity<Void> abandonDraft(@PathVariable String orderNumber) {
        MDC.put(MdcKeys.ORDER_NUMBER, orderNumber);
        try {
            saleDraftService.abandonDraft(orderNumber);
            return ResponseEntity.noContent().build();
        } finally {
            MDC.remove(MdcKeys.ORDER_NUMBER);
        }
    }

    /**
     * Confirm Submit (AC-SALE-01-15). Returns as soon as order_db has committed the
     * transition, the saga and its first command — never after waiting for
     * product-service or account-service.
     */
    @PostMapping("/{orderNumber}/submit")
    public ResponseEntity<OrderStatusResponse> submit(
            @PathVariable String orderNumber,
            @RequestHeader(IdempotencyContract.HEADER) String idempotencyKey,
            @Valid @RequestBody OrderSubmitRequest request) {
        MDC.put(MdcKeys.ORDER_NUMBER, orderNumber);
        try {
            OrderStatusResponse accepted = saleDraftService.submitDraft(orderNumber, request);
            return ResponseEntity.accepted()
                    .location(statusUri(orderNumber))
                    .body(accepted);
        } finally {
            MDC.remove(MdcKeys.ORDER_NUMBER);
        }
    }

    /**
     * The status resource the client polls after a 202 (AC-SALE-01-15's processing
     * state). Cheap by design: two indexed primary-key reads in one transaction, no
     * downstream call — a poll loop must not cost what the sale itself costs.
     */
    @GetMapping("/{orderNumber}/status")
    public ResponseEntity<OrderStatusResponse> status(@PathVariable String orderNumber) {
        MDC.put(MdcKeys.ORDER_NUMBER, orderNumber);
        try {
            return ResponseEntity.ok(saleDraftService.status(orderNumber));
        } finally {
            MDC.remove(MdcKeys.ORDER_NUMBER);
        }
    }

    /** Relative on purpose: the browser reaches this service through the gateway (ADR-007). */
    private static URI statusUri(String orderNumber) {
        return URI.create("/api/orders/" + orderNumber + "/status");
    }
}
