package com.crm.account.messaging;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.MessageTypes;
import java.util.List;

/**
 * account-service's side of the wire contract (ADR-017 §6).
 *
 * <p>Producer-owned; the authoritative definition is
 * {@code docs/contracts/events/crm.account.products-linked.v1.schema.json}.
 *
 * <p>This is the SALE's terminal fact. ADR-017 approves <b>choreography</b> for
 * non-critical reactions to exactly this event — a reaction that must not be able to fail
 * the sale is precisely one that should not be a saga step, because by the time this is
 * published the sale is already real and visible (ADR-016 §5, step 5).
 */
public final class AccountEventContracts {

    private AccountEventContracts() {
    }

    /** {@code crm.account.evt.products-linked.v1} */
    public static final String PRODUCTS_LINKED_DESTINATION =
            Destinations.event(Destinations.DOMAIN_ACCOUNT, "products-linked", 1);

    public static final String MESSAGE_TYPE = MessageTypes.PRODUCTS_LINKED;
    public static final String AGGREGATE_TYPE = MessageTypes.AGGREGATE_ACCOUNT;

    /**
     * The fact: {@code cust_acct_prod_invl} now links these products to this billing
     * account.
     *
     * @param accountNumber the KR-11 number — also the envelope's aggregateId
     * @param linkedProductIds ids linked by THIS command (empty when every requested id was
     *                       already linked — the command is idempotent per (account, product),
     *                       ADR-013 §8)
     * @param allProductIds  the account's full involvement set after the command, i.e. the
     *                       same list the synchronous response returns. Consumers that want
     *                       state rather than a delta read this one and need no back-call.
     */
    public record ProductsLinkedPayload(
            String accountNumber,
            List<Long> linkedProductIds,
            List<Long> allProductIds) {
    }
}
