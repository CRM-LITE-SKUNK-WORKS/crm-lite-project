package com.crm.order.order.mapper;

import com.crm.order.lookup.LookupContract;
import com.crm.order.order.dto.response.OrderItemResponse;
import com.crm.order.order.dto.response.OrderResponse;
import com.crm.order.order.entity.CustomerOrder;
import com.crm.order.order.entity.CustomerOrderItem;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    /** Must run inside a transaction (the lazy businessInteraction/items are read here). */
    public OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.getOrderNumber(),
                statusCode(order),
                order.getBusinessInteraction().getCustomerAccountNumber(),
                order.getCustomerNumber(),
                order.getTotalAmount(),
                order.getItems().stream().map(this::toItemResponse).toList());
    }

    private OrderItemResponse toItemResponse(CustomerOrderItem item) {
        return new OrderItemResponse(item.getProductOfferId(), item.getProductId(), item.getAmount());
    }

    /**
     * The ORDER-domain short code, derived from the stored external reference. All
     * THREE ORDER-domain values are reachable since ADR-018: WAIT for a started but
     * unsubmitted sale (the analyst's "the records already exist, their status is not
     * yet MIDLWARE"), MIDLWARE from Submit onwards, CANCELLED for an abandoned draft or
     * a compensated sale. Anything else would mean order_db holds a status this domain
     * never writes, so it is surfaced as the raw id rather than guessed at — a wrong
     * label is worse than an obviously odd one.
     */
    public String statusCode(CustomerOrder order) {
        Long statusId = order.getStatusId();
        if (Long.valueOf(LookupContract.STATUS_WAITING_ID).equals(statusId)) {
            return LookupContract.STATUS_WAITING;
        }
        if (Long.valueOf(LookupContract.STATUS_MIDDLEWARE_ID).equals(statusId)) {
            return LookupContract.STATUS_MIDDLEWARE;
        }
        if (Long.valueOf(LookupContract.STATUS_CANCELLED_ID).equals(statusId)) {
            return LookupContract.STATUS_CANCELLED;
        }
        return String.valueOf(statusId);
    }
}
