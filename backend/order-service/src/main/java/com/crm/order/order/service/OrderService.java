package com.crm.order.order.service;

import com.crm.order.order.dto.request.OrderCreateRequest;
import com.crm.order.order.dto.response.OrderResponse;

public interface OrderService {

    /** AC-SALE-01-15: the Submit-Order command and the whole sale orchestration (ADR-016 §5). */
    OrderResponse submit(OrderCreateRequest request);

    /** ADR-016 §3.2: order detail by its KR-12 public number. */
    OrderResponse getByOrderNumber(String orderNumber);
}
