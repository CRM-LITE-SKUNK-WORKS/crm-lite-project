package com.crm.order.order;

/**
 * The PUBLIC asynchronous processing state of a sale (ADR-018 §6) — four values, and
 * they are not GNL_ST.
 *
 * <p><b>Two status axes, deliberately not merged.</b> {@code orderStatus} is the
 * analyst-owned business status of the order (WAIT / MIDLWARE / CANCELLED, GNL_ST ORDER
 * domain, ADR-002). This is the technical answer to a different question: has the
 * asynchronous work finished? They can disagree legitimately, and the most important
 * case is the successful one — a completed sale is {@code MIDLWARE} + {@code COMPLETED},
 * because the analyst defined MIDLWARE at approval and no accepted requirement defines
 * another terminal ORDER status.
 *
 * <p>Adding COMPLETED/FAILED to GNL_ST instead would have put a project-invented row
 * into a catalog three services read and an analyst owns. This enum is order-service's
 * own API contract and is documented as such.
 *
 * <p>Internal saga states are richer than this on purpose (see
 * {@link com.crm.order.saga.SaleSagaState}): an operator needs to know that a sale is
 * COMPENSATING_INVOLVEMENT, a client needs to know only that it failed.
 */
public enum ProcessingStatus {

    /** The order exists with a number and is WAIT: started, never submitted. No saga. */
    DRAFT,

    /** Submitted; the saga is running. The client polls until this changes. */
    PROCESSING,

    /** Core consistency reached: products ACTV and linked to the account. */
    COMPLETED,

    /**
     * Terminal, unsuccessful — whether cleanly compensated, abandoned before Submit, or
     * escalated for an operator. The distinction between those matters to operations and
     * not to the client; {@code failureMessageKey} carries whatever the client may be
     * told.
     */
    FAILED
}
