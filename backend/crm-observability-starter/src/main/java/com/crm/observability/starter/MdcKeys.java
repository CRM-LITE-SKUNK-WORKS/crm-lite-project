package com.crm.observability.starter;

/**
 * MDC key names shared across every service so the JSON log fragment
 * ({@code logback-json-base.xml}) and every service's own code agree on field
 * names without needing to import each other. Micrometer Tracing populates
 * {@code traceId}/{@code spanId} itself under these exact names — not repeated
 * here — everything below is CRM Lite-specific.
 *
 * <p>{@code sagaId}/{@code eventId} are reserved for the messaging-based SALE
 * flow this observability work is explicitly preparing for (see
 * docs/runbooks/resilience.md) — nothing sets them yet, and the JSON encoder
 * simply omits an MDC key that was never put, which is what "where available"
 * means for these two.
 */
public final class MdcKeys {

    private MdcKeys() {
    }

    /** Request-scoped correlation id, read from / generated for {@code X-Correlation-Id}. */
    public static final String CORRELATION_ID = "correlationId";

    /** KR-12 order number, set once order-service knows it (never invented client-side). */
    public static final String ORDER_NUMBER = "orderNumber";

    /** Reserved for the future saga/messaging orchestration id. Not populated today. */
    public static final String SAGA_ID = "sagaId";

    /** Reserved for the future domain-event id. Not populated today. */
    public static final String EVENT_ID = "eventId";

    /** Simple class name of a logged exception, set at the point an exception is logged. */
    public static final String EXCEPTION_TYPE = "exceptionType";
}
