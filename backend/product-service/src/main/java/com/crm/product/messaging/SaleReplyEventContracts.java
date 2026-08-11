package com.crm.product.messaging;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.MessageTypes;
import java.math.BigDecimal;
import java.util.List;

/**
 * The outcome events product-service PUBLISHES back to the SALE saga (ADR-018 §5).
 *
 * <p>Producer-owned; the authoritative, language-neutral definitions are the JSON
 * Schemas under {@code docs/contracts/events/}.
 *
 * <p><b>One event per step, carrying a result, rather than a success type and a failure
 * type.</b> Both are facts about the same step and the orchestrator decides its next move
 * with the same state guard either way. Two destinations per step would double the
 * binding table and make "what is this saga waiting for" two questions instead of one.
 *
 * <p><b>{@code failureMessageKey} is a catalog key, never an exception message.</b> That
 * is what lets a rejected basket reach the user as its own MSG-SALE-* key while an
 * infrastructure failure never reaches them at all: this service only ever puts a key it
 * already publishes over HTTP here, and the consumer refuses anything that is not shaped
 * like one.
 */
public final class SaleReplyEventContracts {

    private SaleReplyEventContracts() {
    }

    /** {@code crm.product.evt.sale-products-prepared.v1} */
    public static final String PREPARED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_PRODUCT, "sale-products-prepared", 1);

    /** {@code crm.product.evt.sale-products-activated.v1} */
    public static final String ACTIVATED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_PRODUCT, "sale-products-activated", 1);

    /** {@code crm.product.evt.sale-products-compensated.v1} */
    public static final String COMPENSATED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_PRODUCT, "sale-products-compensated", 1);

    public static final String PREPARED = MessageTypes.SALE_PRODUCTS_PREPARED;
    public static final String ACTIVATED = MessageTypes.SALE_PRODUCTS_ACTIVATED;
    public static final String COMPENSATED = MessageTypes.SALE_PRODUCTS_COMPENSATED;

    public static final String AGGREGATE_TYPE = MessageTypes.AGGREGATE_PRODUCT;

    public enum Result {
        SUCCEEDED,
        FAILED
    }

    /**
     * The outcome of one saga step.
     *
     * @param products    populated by the preparation reply only — the ids and amount
     *                    snapshots order-service writes into {@code cust_ord_item}
     * @param productIds  populated by the activation and compensation replies — the ids
     *                    they acted on
     */
    public record SaleStepOutcome(
            String orderNumber,
            Result result,
            String failureCode,
            String failureMessageKey,
            BigDecimal totalAmount,
            List<PreparedProduct> products,
            List<Long> productIds) {

        public record PreparedProduct(Long productId, Long offerId, BigDecimal amount, boolean main) {
        }

        public static SaleStepOutcome failed(String orderNumber, String failureCode, String failureMessageKey) {
            return new SaleStepOutcome(orderNumber, Result.FAILED, failureCode, failureMessageKey,
                    null, null, null);
        }
    }
}
