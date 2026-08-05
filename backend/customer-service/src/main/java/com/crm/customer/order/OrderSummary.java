package com.crm.customer.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The slice of order-service's {@code OrderResponse} the KR-02 Order Number search
 * needs: who the order belongs to and whether it is a live order.
 *
 * <p>An order references its customer DIRECTLY ({@code cust_ord.customer_number},
 * ADR-016 §2.3), not through the billing account, so resolving an Order Number never
 * goes through account-service.
 *
 * <p>Unknown properties are ignored on purpose — same additive-change tolerance as
 * {@code com.crm.customer.account.AccountSummary}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSummary(String orderNumber, Long customerNumber, String orderStatus) {

    /**
     * An order's live state is <b>MIDLWARE</b>, not ACTV (ADR-016 §6): that is the only
     * status a user ever sees and the only one this domain writes for an accepted
     * order. {@code CANCELLED} is reached solely by the sale orchestration's own
     * compensation — a sale that was rolled back — and must not surface its customer as
     * a search hit, which is the order-domain equivalent of the "no passive child
     * record" rule applied to accounts.
     *
     * <p>Deliberately not a generic {@code isActive()}: order-service's own
     * {@code CustomerOrder.isInProgress()} makes exactly this distinction, and a
     * generic ACTV check would answer "no" for every perfectly valid order.
     */
    public boolean isInProgress() {
        return "MIDLWARE".equals(orderStatus);
    }
}
