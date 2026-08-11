package com.crm.account.messaging;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.MessageTypes;
import java.util.List;

/**
 * The outcome events account-service PUBLISHES back to the SALE saga (ADR-018 §5).
 *
 * <p>Producer-owned; the authoritative definitions are the JSON Schemas under
 * {@code docs/contracts/events/}. One event per step carrying a result — see
 * product-service's counterpart for why success and failure share a destination.
 *
 * <p>Distinct from {@link AccountEventContracts}, which owns
 * {@code crm.account.products-linked} — the terminal, choreography-only fact the
 * SYNCHRONOUS route publishes. Two shapes, because they answer different questions: that
 * one says "the sale is real", these say "here is how your step went". Merging them would
 * make a saga reply look like a terminal fact to every choreography consumer.
 */
public final class SaleReplyEventContracts {

    private SaleReplyEventContracts() {
    }

    /** {@code crm.account.evt.sale-account-checked.v1} */
    public static final String CHECKED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_ACCOUNT, "sale-account-checked", 1);

    /** {@code crm.account.evt.sale-products-linked.v1} */
    public static final String LINKED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_ACCOUNT, "sale-products-linked", 1);

    /** {@code crm.account.evt.sale-involvements-compensated.v1} */
    public static final String COMPENSATED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_ACCOUNT, "sale-involvements-compensated", 1);

    public static final String CHECKED = MessageTypes.SALE_ACCOUNT_CHECKED;
    public static final String LINKED = MessageTypes.SALE_PRODUCTS_LINKED;
    public static final String COMPENSATED = MessageTypes.SALE_INVOLVEMENTS_COMPENSATED;

    public static final String AGGREGATE_TYPE = MessageTypes.AGGREGATE_ACCOUNT;

    public enum Result {
        SUCCEEDED,
        FAILED
    }

    /**
     * The outcome of one saga step.
     *
     * @param customerNumber the account check only — the AUTHORITATIVE owner of the
     *                       account, which is what product-service later validates the
     *                       service address against
     * @param productIds     the link and compensation replies — the ids they acted on
     */
    public record SaleStepOutcome(
            String orderNumber,
            Result result,
            String failureCode,
            String failureMessageKey,
            Long customerNumber,
            List<Long> productIds) {

        public static SaleStepOutcome failed(String orderNumber, String failureCode, String failureMessageKey) {
            return new SaleStepOutcome(orderNumber, Result.FAILED, failureCode, failureMessageKey, null, null);
        }
    }
}
