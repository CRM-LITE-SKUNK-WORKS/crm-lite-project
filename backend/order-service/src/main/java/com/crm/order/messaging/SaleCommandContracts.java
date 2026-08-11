package com.crm.order.messaging;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.MessageTypes;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The commands order-service ISSUES as the SALE saga orchestrator, plus the one
 * terminal event it publishes (ADR-018 §5/§7).
 *
 * <p>Producer-owned, like every other {@code *Contracts} class in this project: the
 * authoritative, language-neutral definitions are the JSON Schemas under
 * {@code docs/contracts/events/}, and each consumer declares its own DTO with the
 * fields it actually reads. There is no shared payload jar — that is what keeps a field
 * added here from forcing a lock-step release of product-service and account-service.
 *
 * <p><b>Commands are imperative and have exactly one intended handler; the terminal
 * event is a fact with any number of readers.</b> The naming reflects it and so does the
 * destination marker ({@code cmd} vs {@code evt}). The distinction is not cosmetic: a
 * command that nobody handles is a stuck sale and must be alerted on, while an event
 * that nobody reads is simply a fact nobody needed.
 *
 * <p>Every payload carries {@code orderNumber}, which for the live SALE flow IS the
 * sagaId (ADR-018 §3) and is also the envelope's {@code aggregateId} and partition key.
 * Carrying it in the body as well as the envelope is deliberate: ADR-017 §7.7 forbids
 * making a business decision from anything outside the payload contract.
 */
public final class SaleCommandContracts {

    private SaleCommandContracts() {
    }

    // ---------------------------------------------------------------- destinations

    /** {@code crm.account.cmd.check-sale-account.v1} */
    public static final String CHECK_ACCOUNT_DESTINATION =
            Destinations.command(Destinations.DOMAIN_ACCOUNT, "check-sale-account", 1);

    /** {@code crm.product.cmd.prepare-sale-products.v1} */
    public static final String PREPARE_PRODUCTS_DESTINATION =
            Destinations.command(Destinations.DOMAIN_PRODUCT, "prepare-sale-products", 1);

    /** {@code crm.account.cmd.link-sale-products.v1} */
    public static final String LINK_PRODUCTS_DESTINATION =
            Destinations.command(Destinations.DOMAIN_ACCOUNT, "link-sale-products", 1);

    /** {@code crm.product.cmd.activate-sale-products.v1} */
    public static final String ACTIVATE_PRODUCTS_DESTINATION =
            Destinations.command(Destinations.DOMAIN_PRODUCT, "activate-sale-products", 1);

    /** {@code crm.product.cmd.compensate-sale-products.v1} */
    public static final String COMPENSATE_PRODUCTS_DESTINATION =
            Destinations.command(Destinations.DOMAIN_PRODUCT, "compensate-sale-products", 1);

    /** {@code crm.account.cmd.compensate-sale-involvements.v1} */
    public static final String COMPENSATE_INVOLVEMENTS_DESTINATION =
            Destinations.command(Destinations.DOMAIN_ACCOUNT, "compensate-sale-involvements", 1);

    /** {@code crm.sale.evt.sale-completed.v1} — the terminal fact, choreography only. */
    public static final String SALE_COMPLETED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_SALE, "sale-completed", 1);

    public static final String AGGREGATE_TYPE = MessageTypes.AGGREGATE_ORDER;

    // ------------------------------------------------------------------- payloads

    /**
     * Step 1. Re-asks the question the Submit request already answered synchronously.
     *
     * <p>Not redundant: the synchronous check happened before the local transaction
     * committed, and the account can be passivated in the window between Submit and
     * fulfilment. The saga's first step therefore establishes the precondition inside
     * the flow that depends on it, at the moment it depends on it.
     */
    public record CheckSaleAccount(String orderNumber, String accountNumber) {
    }

    /**
     * Step 2. Create this sale's products as PNDG.
     *
     * @param customerNumber taken from account-service's own reply, never from the
     *                       browser — it is what product-service checks the service
     *                       address against (ADR-015 §5.9), so accepting a client-supplied
     *                       value would defeat the check it enables
     * @param campaignId     the PUBLIC campaign code, resolved by the consumer through the
     *                       catalog exactly as the synchronous path does
     */
    public record PrepareSaleProducts(
            String orderNumber,
            String accountNumber,
            Long customerNumber,
            Long serviceAddressId,
            String campaignId,
            List<Item> items) {

        /** Characteristic values keyed by characteristic id, forwarded verbatim (ADR-015 §6). */
        public record Item(Long offerId, Map<Long, String> characteristics) {
        }
    }

    /** Step 3. Link the prepared products to the billing account — the sale's commit point. */
    public record LinkSaleProducts(String orderNumber, String accountNumber, List<Long> productIds) {
    }

    /**
     * Step 4. Promote the products PNDG → ACTV.
     *
     * <p>Deliberately AFTER the involvement (the reverse of the synchronous ADR-016 §5
     * ordering): a product must not be committed while no account claims it, and an
     * involvement that outlives its products is the harder residue to reason about. The
     * cost of this ordering is that an activation failure has an involvement to undo,
     * which is exactly what {@link CompensateSaleInvolvements} exists for.
     */
    public record ActivateSaleProducts(String orderNumber, List<Long> productIds) {
    }

    /**
     * Compensation. Sale-scoped by construction: the consumer refuses any product whose
     * {@code saleOperationId} is not this order number, so a saga can never passivate
     * another saga's products.
     */
    public record CompensateSaleProducts(String orderNumber, List<Long> productIds) {
    }

    /**
     * Compensation for the involvement rows THIS saga created — internal, never a
     * user-facing unlink. account-service matches on {@code saleOperationId} and touches
     * nothing it did not write for this order.
     */
    public record CompensateSaleInvolvements(String orderNumber, String accountNumber) {
    }

    /**
     * The terminal fact: this sale reached core consistency (ADR-018 §7).
     *
     * <p>Published only from {@code COMPLETED}, i.e. after the products are ACTV and the
     * involvement rows exist. A reaction to it therefore cannot fail the sale, which is
     * precisely what makes it choreography rather than a saga step (ADR-017 §2).
     */
    public record SaleCompleted(
            String orderNumber,
            String accountNumber,
            Long customerNumber,
            List<Long> productIds,
            BigDecimal totalAmount) {
    }
}
