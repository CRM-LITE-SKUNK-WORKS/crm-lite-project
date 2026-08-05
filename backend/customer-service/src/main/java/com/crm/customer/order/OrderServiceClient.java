package com.crm.customer.order;

import java.util.Optional;

/**
 * order-service boundary for the KR-02 {@code orderNumber} search criterion
 * (ADR-016 §3.2, which records this endpoint as the resolution point the customer
 * search would use). order_db is never read directly, joined against or shared as
 * entities — this API is the only door.
 *
 * <p>Outcomes, kept distinct:
 * <ul>
 *   <li>{@code Optional.empty()} — no order with that number;
 *   <li>a summary — the order exists; its {@code orderStatus} says whether it is live
 *       (MIDLWARE) or a compensated CANCELLED record;
 *   <li>{@link OrderServiceUnavailableException} — nothing is known; fail closed.
 * </ul>
 *
 * <p>Reached directly via Eureka with the end user's token propagated (ADR-010).
 */
public interface OrderServiceClient {

    Optional<OrderSummary> fetchOrder(String orderNumber);
}
