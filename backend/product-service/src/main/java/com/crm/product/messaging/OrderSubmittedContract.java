package com.crm.product.messaging;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.MessageTypes;
import java.util.List;
import java.util.Map;

/**
 * product-service's <b>consumer-owned</b> view of order-service's
 * {@code crm.order.order-submitted} event (ADR-017 §6).
 *
 * <p>This is a deliberate duplicate of a shape order-service also declares, and the
 * duplication is the design. Importing the producer's class would make a shared domain
 * model out of two services that are supposed to be independently deployable: adding a
 * field over there would force a rebuild over here, and removing one would break the
 * build of a service that never used it.
 *
 * <p>The DTO therefore lists only the fields this service would read. Anything the
 * producer adds is ignored by the codec, which is exactly the tolerance a versioned
 * contract is supposed to buy.
 */
public final class OrderSubmittedContract {

    private OrderSubmittedContract() {
    }

    /** The destination this service reads from — produced by order-service. */
    public static final String SOURCE_DESTINATION =
            Destinations.event(Destinations.DOMAIN_ORDER, "order-submitted", 1);

    /** {@code product-service.crm.order.evt.order-submitted.v1} */
    public static final String CONSUMER_GROUP =
            Destinations.consumerGroup("product-service", SOURCE_DESTINATION);

    public static final String MESSAGE_TYPE = MessageTypes.ORDER_SUBMITTED;

    /** Only what product-service needs to build a basket; no order-service class is imported. */
    public record OrderSubmitted(
            String orderNumber,
            Long customerNumber,
            Long serviceAddressId,
            String campaignId,
            List<Item> items) {

        public record Item(Long offerId, Map<Long, String> characteristics) {
        }
    }
}
