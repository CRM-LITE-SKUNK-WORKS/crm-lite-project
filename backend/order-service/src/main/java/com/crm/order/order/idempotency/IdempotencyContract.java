package com.crm.order.order.idempotency;

import java.util.regex.Pattern;

/** Wire-level constants for the {@code POST /api/orders} idempotency contract
 *  (ADR-016 idempotency addendum, 2026-08-06). */
public final class IdempotencyContract {

    private IdempotencyContract() {
    }

    public static final String HEADER = "Idempotency-Key";

    /** Set on a replayed response so a caller (and a test) can tell "this is the
     *  original answer, nothing ran again" from a genuinely fresh one. Diagnostic
     *  only — no consumer is required to read it. */
    public static final String REPLAYED_HEADER = "Idempotency-Replayed";

    /**
     * The frontend always sends a {@code crypto.randomUUID()} value (order-submit.store.ts),
     * so requiring UUID shape is not an arbitrary tightening: it bounds the key space
     * to something the DB column (VARCHAR(64)) and the hashing path can treat as an
     * opaque, fixed-length token, and turns a client bug (empty header, a stray
     * sentence pasted into it) into a clean 400 rather than a silently-accepted
     * "idempotency key" that offers no real protection.
     */
    public static final Pattern KEY_FORMAT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public static boolean isValidKey(String key) {
        return key != null && KEY_FORMAT.matcher(key).matches();
    }
}
