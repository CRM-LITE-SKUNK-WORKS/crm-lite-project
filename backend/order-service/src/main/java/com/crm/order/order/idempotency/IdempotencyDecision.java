package com.crm.order.order.idempotency;

/** The three outcomes {@link IdempotencyService#reserveOrReplay} can hand back to
 *  {@link IdempotencyKeyFilter} — a closed set, deliberately not exceptions: the
 *  filter is the ONE place that turns each of these into bytes on the wire, and a
 *  sealed switch keeps that mapping exhaustive and visible in one method. */
public sealed interface IdempotencyDecision {

    /** No prior attempt for this key: proceed to the real controller/service chain.
     *  {@code reservationId} is what {@link IdempotencyService#complete} closes out. */
    record Proceed(long reservationId) implements IdempotencyDecision {
    }

    /** A prior attempt for this exact key + normalized payload already produced a
     *  terminal response: replay it verbatim, unchanged, without touching the
     *  orchestration again. */
    record Replay(int status, String body) implements IdempotencyDecision {
    }

    /** The key is already spoken for and cannot answer this request: either the same
     *  key was used with a DIFFERENT payload, or a concurrent request for the same
     *  key + payload is still IN_PROGRESS. Either way: 409, nothing was (re)run. */
    record Conflict(String messageKey, String message) implements IdempotencyDecision {
    }
}
