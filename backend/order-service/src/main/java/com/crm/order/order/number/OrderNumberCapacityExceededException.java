package com.crm.order.order.number;

/**
 * The KR-12 per-segment/per-year sequence is exhausted (issued value would exceed
 * 999999). A documented domain error — mapped to 409
 * MSG-ORDER-NUMBER-CAPACITY-EXCEEDED, never a raw 500 (ADR-016 §4.4). The
 * surrounding transaction rolls back, taking the sequence increment with it.
 */
public class OrderNumberCapacityExceededException extends RuntimeException {

    public OrderNumberCapacityExceededException(int segment, int year) {
        super("Order number capacity for segment " + segment + " and year " + year + " is exhausted");
    }
}
