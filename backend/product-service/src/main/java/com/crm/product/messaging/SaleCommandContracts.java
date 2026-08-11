package com.crm.product.messaging;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.MessageTypes;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * product-service's <b>consumer-owned</b> view of the three SALE saga commands
 * order-service issues to it (ADR-017 §6, ADR-018 §5).
 *
 * <p>Deliberate partial duplicates of order-service's own
 * {@code SaleCommandContracts}. Importing the producer's class would make a shared
 * domain model of two services that are supposed to be independently deployable: a field
 * added there would force a rebuild here, and one removed there would break the build of
 * a service that never read it.
 *
 * <p>Each record lists only the fields this service actually uses. Everything else the
 * producer sends is ignored by the codec, which is the tolerance a versioned contract
 * exists to buy.
 */
public final class SaleCommandContracts {

    private SaleCommandContracts() {
    }

    private static final String CONSUMER = "product-service";

    /** {@code crm.product.cmd.prepare-sale-products.v1} */
    public static final String PREPARE_DESTINATION =
            Destinations.command(Destinations.DOMAIN_PRODUCT, "prepare-sale-products", 1);

    /** {@code crm.product.cmd.activate-sale-products.v1} */
    public static final String ACTIVATE_DESTINATION =
            Destinations.command(Destinations.DOMAIN_PRODUCT, "activate-sale-products", 1);

    /** {@code crm.product.cmd.compensate-sale-products.v1} */
    public static final String COMPENSATE_DESTINATION =
            Destinations.command(Destinations.DOMAIN_PRODUCT, "compensate-sale-products", 1);

    public static String consumerGroup(String destination) {
        return Destinations.consumerGroup(CONSUMER, destination);
    }

    public static final String PREPARE = MessageTypes.PREPARE_SALE_PRODUCTS;
    public static final String ACTIVATE = MessageTypes.ACTIVATE_SALE_PRODUCTS;
    public static final String COMPENSATE = MessageTypes.COMPENSATE_SALE_PRODUCTS;

    /**
     * Create this sale's products as PNDG.
     *
     * <p>{@code orderNumber} doubles as the {@code saleOperationId} handed to the
     * existing creation path (ADR-018 §3), which is what makes a redelivered or reissued
     * command replay the FIRST result instead of creating a second installation.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrepareSaleProducts(
            String orderNumber,
            Long customerNumber,
            Long serviceAddressId,
            String campaignId,
            List<Item> items) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Item(Long offerId, Map<Long, String> characteristics) {
        }
    }

    /** Promote this sale's PNDG products to ACTV. Idempotent: non-PNDG ids are skipped. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivateSaleProducts(String orderNumber, List<Long> productIds) {
    }

    /**
     * Passivate the PNDG products owned by this sale. The existing compensation path
     * refuses any product whose {@code saleOperationId} differs, so one saga can never
     * compensate another's products even if it is handed their ids.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompensateSaleProducts(String orderNumber, List<Long> productIds) {
    }
}
