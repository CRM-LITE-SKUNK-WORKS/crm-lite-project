package com.crm.account.messaging;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.MessageTypes;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * account-service's <b>consumer-owned</b> view of the three SALE saga commands
 * order-service issues to it (ADR-017 §6, ADR-018 §5).
 *
 * <p>Deliberate partial duplicates of order-service's own contracts class. Importing the
 * producer's type would make a shared domain model of two services that must stay
 * independently deployable; each record here lists only the fields this service reads,
 * and anything else the producer sends is ignored by the codec.
 */
public final class SaleCommandContracts {

    private SaleCommandContracts() {
    }

    private static final String CONSUMER = "account-service";

    /** {@code crm.account.cmd.check-sale-account.v1} */
    public static final String CHECK_DESTINATION =
            Destinations.command(Destinations.DOMAIN_ACCOUNT, "check-sale-account", 1);

    /** {@code crm.account.cmd.link-sale-products.v1} */
    public static final String LINK_DESTINATION =
            Destinations.command(Destinations.DOMAIN_ACCOUNT, "link-sale-products", 1);

    /** {@code crm.account.cmd.compensate-sale-involvements.v1} */
    public static final String COMPENSATE_DESTINATION =
            Destinations.command(Destinations.DOMAIN_ACCOUNT, "compensate-sale-involvements", 1);

    public static String consumerGroup(String destination) {
        return Destinations.consumerGroup(CONSUMER, destination);
    }

    public static final String CHECK = MessageTypes.CHECK_SALE_ACCOUNT;
    public static final String LINK = MessageTypes.LINK_SALE_PRODUCTS;
    public static final String COMPENSATE = MessageTypes.COMPENSATE_SALE_INVOLVEMENTS;

    /**
     * Is this billing account still sellable into? The saga's first step, and the reason
     * the customer number in the reply is authoritative: the sale learns who owns the
     * account from the account, never from the browser (ADR-015 §5.9).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckSaleAccount(String orderNumber, String accountNumber) {
    }

    /** Link this sale's products to the account — the sale's commit point (ADR-013 §8). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinkSaleProducts(String orderNumber, String accountNumber, List<Long> productIds) {
    }

    /**
     * Soft-remove the involvement rows THIS saga created. Scoped by
     * {@code orderNumber} — which is the saga id stored on the rows — so it can never
     * touch another sale's involvement, and never one written before sagas existed.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompensateSaleInvolvements(String orderNumber, String accountNumber) {
    }
}
