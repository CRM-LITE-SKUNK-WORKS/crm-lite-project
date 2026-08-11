package com.crm.order.saga;

/**
 * The SALE saga's INTERNAL state (ADR-018 §4). Deliberately richer than anything a
 * client ever sees.
 *
 * <p><b>These are not GNL_ST values and must never become any.</b> GNL_ST is the
 * analyst-owned shared catalog (ADR-002); its ORDER domain has exactly three rows —
 * WAIT, MIDLWARE, CANCELLED — and adding a COMPENSATING row to it would put an
 * implementation detail of this service's process manager into a catalog every other
 * service reads. The business status of an order stays one of those three; this enum
 * says only how far the orchestration got, and it is collapsed to the four public
 * values of {@link com.crm.order.order.ProcessingStatus} at the API boundary.
 *
 * <p>The distinction is load-bearing in one specific place: a sale whose activation
 * failed after the involvement rows existed is {@link #COMPENSATING_INVOLVEMENT}
 * internally — an operator can see exactly which compensation is outstanding — while
 * the customer-facing contract says only {@code FAILED}. One audience needs the step,
 * the other needs the outcome.
 */
public enum SaleSagaState {

    /** Created by Submit, in the same transaction as WAIT → MIDLWARE. Transient. */
    STARTED,

    /** Waiting for account-service to confirm the billing account is still sellable into. */
    AWAITING_ACCOUNT_CHECK,

    /** Waiting for product-service to create this sale's products as PNDG. */
    AWAITING_PRODUCT_PREPARATION,

    /** Waiting for account-service to link the products to the billing account. */
    AWAITING_INVOLVEMENT,

    /** Waiting for product-service to promote the products PNDG → ACTV. */
    AWAITING_ACTIVATION,

    /** Core consistency reached. The terminal SaleCompleted fact is published from here. */
    COMPLETED,

    /**
     * Activation failed AFTER involvement rows existed, so the involvement has to go
     * first: the products must not be passivated while an account still claims them.
     */
    COMPENSATING_INVOLVEMENT,

    /** Passivating the PNDG products this saga created. Reached directly, or via the above. */
    COMPENSATING_PRODUCTS,

    /** Terminally failed and fully compensated. The order is CANCELLED. */
    FAILED,

    /**
     * Compensation itself failed, or the retry budget ran out. NOT a silent outcome:
     * it is a distinct state precisely so "a partial compensation failure must remain
     * recoverable and observable rather than being logged and forgotten" is a query
     * (`current_state = 'MANUAL_INTERVENTION'`) and a gauge, not a grep through logs.
     */
    MANUAL_INTERVENTION;

    /** No further command will ever be issued for a saga in this state. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == MANUAL_INTERVENTION;
    }

    /** Waiting for a reply — the states the recovery job may reissue a command for. */
    public boolean isAwaitingReply() {
        return this == AWAITING_ACCOUNT_CHECK
                || this == AWAITING_PRODUCT_PREPARATION
                || this == AWAITING_INVOLVEMENT
                || this == AWAITING_ACTIVATION
                || this == COMPENSATING_INVOLVEMENT
                || this == COMPENSATING_PRODUCTS;
    }

    /** Undoing rather than progressing — separated for the compensation metrics. */
    public boolean isCompensating() {
        return this == COMPENSATING_INVOLVEMENT || this == COMPENSATING_PRODUCTS;
    }
}
