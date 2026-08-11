package com.crm.order.order.service;

import com.crm.order.order.dto.request.OrderDraftCreateRequest;
import com.crm.order.order.dto.request.OrderSubmitRequest;
import com.crm.order.order.dto.response.OrderStatusResponse;

/**
 * The asynchronous SALE contract (ADR-018 §6): draft → submit → poll, plus the explicit
 * abandon.
 *
 * <p>Separate from {@link OrderService} rather than added to it, deliberately. That
 * interface is the synchronous ADR-016 §5 orchestration, which stays in the build as the
 * documented legacy/rollback route until the frontend PR lands. Two interfaces make the
 * two routes separately addressable, separately testable and — most importantly —
 * impossible to invoke by accident from each other: there is no method here that a
 * synchronous submit calls, and none there that a saga does.
 */
public interface SaleDraftService {

    /**
     * Start New Sale. Creates the WAIT order and allocates its KR-12 number, so the
     * Submit screen can show the Order Number before anything is confirmed
     * (AC-SALE-01-12, analyst decision of 2026-08-10).
     */
    OrderStatusResponse createDraft(OrderDraftCreateRequest request);

    /** Cancel before submit. WAIT only, idempotent, never a post-submit cancellation. */
    void abandonDraft(String orderNumber);

    /**
     * Confirm Submit: WAIT → MIDLWARE and the saga starts. Returns as soon as the local
     * transaction commits — it never waits for product-service or account-service.
     */
    OrderStatusResponse submitDraft(String orderNumber, OrderSubmitRequest request);

    /** The status resource the client polls after a 202. */
    OrderStatusResponse status(String orderNumber);
}
