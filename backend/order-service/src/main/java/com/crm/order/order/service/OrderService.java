package com.crm.order.order.service;

import com.crm.order.order.dto.request.OrderCreateRequest;
import com.crm.order.order.dto.response.OrderResponse;

public interface OrderService {

    /**
     * AC-SALE-01-15: the Submit-Order command and the whole sale orchestration (ADR-016 §5).
     *
     * @param idempotencyKey the client's {@code Idempotency-Key}, forwarded to
     *                       product-service as the stable operation identifier that
     *                       makes product creation replay-safe (ADR-015 idempotency
     *                       addendum) — order-service does not interpret it otherwise.
     */
    OrderResponse submit(OrderCreateRequest request, String idempotencyKey);

    /** ADR-016 §3.2: order detail by its KR-12 public number. */
    OrderResponse getByOrderNumber(String orderNumber);
}
