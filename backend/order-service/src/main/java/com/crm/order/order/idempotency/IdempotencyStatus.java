package com.crm.order.order.idempotency;

/** Values of {@link IdempotencyKeyRecord#getStatus()}. Stored as plain text, not a
 *  central GNL_ST id — this table is project-local bookkeeping, not workbook domain
 *  state, so it does not go through the shared catalog (ADR-002 does not apply). */
public final class IdempotencyStatus {

    private IdempotencyStatus() {
    }

    /** The reservation row exists; no terminal response has been recorded yet. */
    public static final String IN_PROGRESS = "IN_PROGRESS";

    /** A terminal HTTP response (success or a handled failure alike) was recorded. */
    public static final String COMPLETED = "COMPLETED";
}
