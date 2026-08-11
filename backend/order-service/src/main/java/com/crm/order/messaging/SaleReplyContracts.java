package com.crm.order.messaging;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.MessageTypes;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * The replies order-service CONSUMES, as order-service's OWN types (ADR-017 §6).
 *
 * <p>These are deliberately partial duplicates of what product-service and
 * account-service publish. The duplication is the mechanism, not an oversight: it is
 * what lets either producer add a field without this service failing on the next
 * message, and what keeps three services independently deployable with no shared
 * payload jar.
 *
 * <p><b>One DTO for six reply types.</b> Every step answers the same two questions —
 * did it succeed, and if not, what may the client be told — and differs only in which
 * extra facts it carries. A separate record per step would be six nearly identical
 * classes plus six nearly identical handlers, and the branch that actually differs
 * (what the saga does next) is already a switch on the message type in one place. Fields
 * that a given reply does not carry arrive as null, which is exactly what the state
 * machine expects when it never reads them for that step.
 */
public final class SaleReplyContracts {

    private SaleReplyContracts() {
    }

    private static final String CONSUMER = "order-service";

    /** {@code crm.account.evt.sale-account-checked.v1} */
    public static final String ACCOUNT_CHECKED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_ACCOUNT, "sale-account-checked", 1);

    /** {@code crm.product.evt.sale-products-prepared.v1} */
    public static final String PRODUCTS_PREPARED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_PRODUCT, "sale-products-prepared", 1);

    /** {@code crm.account.evt.sale-products-linked.v1} */
    public static final String PRODUCTS_LINKED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_ACCOUNT, "sale-products-linked", 1);

    /** {@code crm.product.evt.sale-products-activated.v1} */
    public static final String PRODUCTS_ACTIVATED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_PRODUCT, "sale-products-activated", 1);

    /** {@code crm.product.evt.sale-products-compensated.v1} */
    public static final String PRODUCTS_COMPENSATED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_PRODUCT, "sale-products-compensated", 1);

    /** {@code crm.account.evt.sale-involvements-compensated.v1} */
    public static final String INVOLVEMENTS_COMPENSATED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_ACCOUNT, "sale-involvements-compensated", 1);

    public static String consumerGroup(String destination) {
        return Destinations.consumerGroup(CONSUMER, destination);
    }

    /** The message types this service accepts on the reply destinations above. */
    public static final String ACCOUNT_CHECKED = MessageTypes.SALE_ACCOUNT_CHECKED;
    public static final String PRODUCTS_PREPARED = MessageTypes.SALE_PRODUCTS_PREPARED;
    public static final String PRODUCTS_LINKED = MessageTypes.SALE_PRODUCTS_LINKED;
    public static final String PRODUCTS_ACTIVATED = MessageTypes.SALE_PRODUCTS_ACTIVATED;
    public static final String PRODUCTS_COMPENSATED = MessageTypes.SALE_PRODUCTS_COMPENSATED;
    public static final String INVOLVEMENTS_COMPENSATED = MessageTypes.SALE_INVOLVEMENTS_COMPENSATED;

    /** Every reply's outcome. Two values, because the saga's next move has two branches. */
    public enum Result {
        SUCCEEDED,
        FAILED
    }

    /**
     * A step's outcome.
     *
     * @param orderNumber         the sagaId; the only field every reply must carry
     * @param result              SUCCEEDED or FAILED
     * @param failureCode         operator-facing detail; never returned to a client
     * @param failureMessageKey   the SAFE key the status endpoint may expose. A producer
     *                            that sends an exception message here would be sending a
     *                            key that no catalog defines, and the consumer's
     *                            {@code safeFailureMessageKey()} refuses to pass it on
     * @param customerNumber      account check only — the authoritative owner
     * @param totalAmount         preparation only — the authoritative amount snapshot
     * @param products            preparation only — one entry per created product
     * @param productIds          link/activate/compensate replies — the ids they acted on
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SaleStepOutcome(
            String orderNumber,
            Result result,
            String failureCode,
            String failureMessageKey,
            Long customerNumber,
            BigDecimal totalAmount,
            List<PreparedProduct> products,
            List<Long> productIds) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PreparedProduct(Long productId, Long offerId, BigDecimal amount, boolean main) {
        }

        public boolean succeeded() {
            return result == Result.SUCCEEDED;
        }
    }
}
