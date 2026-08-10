package com.crm.product.messaging;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.MessageTypes;
import java.math.BigDecimal;
import java.util.List;

/**
 * product-service's side of the wire contract (ADR-017 §6).
 *
 * <p>Producer-owned, like every other {@code *EventContracts} class in the project: the
 * authoritative definition is
 * {@code docs/contracts/events/crm.product.products-created.v1.schema.json}, and a
 * consumer writes its own DTO with only the fields it reads rather than importing this
 * one. There is no shared payload jar between services, by decision.
 */
public final class ProductEventContracts {

    private ProductEventContracts() {
    }

    /** {@code crm.product.evt.products-created.v1} */
    public static final String PRODUCTS_CREATED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_PRODUCT, "products-created", 1);

    public static final String MESSAGE_TYPE = MessageTypes.PRODUCTS_CREATED;
    public static final String AGGREGATE_TYPE = MessageTypes.AGGREGATE_PRODUCT;

    /**
     * The fact: the requested products now exist in {@code product_db} as PNDG, with ids
     * and amounts assigned.
     *
     * <p>PNDG, not ACTV, and deliberately so: this event says the products were created,
     * not that the sale completed. Confirmation (ADR-015 §5.6) and the account
     * involvement that is the actual commit point (ADR-013 §8) are separate steps, and a
     * single "sale done" event covering all three would be a lie for the window between
     * them.
     *
     * @param saleOperationId the operation this creation belongs to — the same value as the
     *                        order's Idempotency-Key and the envelope's sagaId, which is
     *                        what makes the whole sale traceable under ONE id
     * @param mainProductId   the INTERNET product; the others hang off it (ADR-015 §5.3)
     */
    public record ProductsCreatedPayload(
            String saleOperationId,
            Long mainProductId,
            BigDecimal totalAmount,
            List<Item> items) {

        public record Item(Long productId, Long offerId, BigDecimal amount, boolean main) {
        }
    }
}
