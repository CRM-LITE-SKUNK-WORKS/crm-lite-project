package com.crm.order.messaging;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.MessageTypes;

/**
 * order-service's side of the wire contract (ADR-017 §6).
 *
 * <p>Producer-owned: these are the destinations order-service PUBLISHES to and the shape
 * it publishes. A consumer declares its own DTO with the fields it actually reads — there
 * is no shared payload jar to import, which is what keeps a field added here from forcing
 * a lock-step release of product-service and account-service.
 *
 * <p>The authoritative, language-neutral definition is
 * {@code docs/contracts/events/crm.order.order-submitted.v1.schema.json}; this class is
 * the Java projection of it.
 */
public final class OrderEventContracts {

    private OrderEventContracts() {
    }

    /** {@code crm.order.evt.order-submitted.v1} */
    public static final String ORDER_SUBMITTED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_ORDER, "order-submitted", 1);

    /**
     * The fact: an order header exists in {@code order_db} with a KR-12 number, its items
     * are known, and it is awaiting fulfilment.
     *
     * <p>Carries the offer ids and characteristic values, NOT product ids: products do not
     * exist at the moment this fact becomes true (ADR-016 §5.2 — the workbook's
     * CUST_ORD_ITEM.product_id is filled later). Publishing a field that is structurally
     * null at emission time would invite consumers to depend on it.
     *
     * @param orderNumber     the KR-12 number — also the envelope's aggregateId
     * @param accountNumber   the KR-11 billing account the sale is for
     * @param customerNumber  taken from the ACCOUNT, never from the client request
     *                        (the same rule ADR-015 §5.9 enforces synchronously today)
     * @param serviceAddressId  as submitted
     * @param campaignId        the PUBLIC campaign code (e.g. {@code CMP-ADSL-01}), never an
     *                          internal id — the same value the HTTP request carries, so a
     *                          consumer resolves it through the catalog exactly as
     *                          product-service does today
     * @param items           one entry per requested offer
     */
    public record OrderSubmittedPayload(
            String orderNumber,
            String accountNumber,
            Long customerNumber,
            Long serviceAddressId,
            String campaignId,
            java.util.List<Item> items) {

        public record Item(Long offerId, java.util.Map<Long, String> characteristics) {
        }
    }

    public static final String MESSAGE_TYPE = MessageTypes.ORDER_SUBMITTED;
    public static final String AGGREGATE_TYPE = MessageTypes.AGGREGATE_ORDER;
}
