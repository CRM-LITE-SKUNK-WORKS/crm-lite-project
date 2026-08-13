package com.crm.product.common.exception;

public final class MessageKeys {

    private MessageKeys() {
    }

    // From the FR/AC v8-1 Final message catalog (23.07.2026 revision).
    // MSG-PROD-NONE is a frontend-only key and is deliberately ABSENT here: an
    // account without products is a 200 empty array; the frontend renders the
    // MSG-PROD-NONE info text (AC-PROD-01-02).
    // The account-not-found contract mirrors account-service (ADR-013 §3.3):
    // an unknown — or K-8-hidden 223 — account number is 404 MSG-ACCT-NOT-FOUND.
    public static final String ACCT_NOT_FOUND = "MSG-ACCT-NOT-FOUND";

    // §2.7 basket validation table (AC-SALE-01-05/08). All 400. These live HERE and
    // not in order-service because they are PROD_OFR/PROD_SPEC questions — "is this
    // offer active?", "which service type does it derive from?" — and validating at
    // the point of persistence makes them unbypassable (ADR-015 §6).
    public static final String SALE_OFFER_INACTIVE = "MSG-SALE-OFFER-INACTIVE";
    public static final String SALE_NO_INTERNET = "MSG-SALE-NO-INTERNET";
    public static final String SALE_NO_RESOURCE = "MSG-SALE-NO-RESOURCE";
    public static final String SALE_NO_ACTIVATION = "MSG-SALE-NO-ACTIVATION";
    public static final String SALE_MULTI_INTERNET = "MSG-SALE-MULTI-INTERNET";
    public static final String SALE_MULTI_RESOURCE = "MSG-SALE-MULTI-RESOURCE";
    public static final String SALE_MULTI_ACTIVATION = "MSG-SALE-MULTI-ACTIVATION";
    public static final String SALE_DUP_OFFER = "MSG-SALE-DUP-OFFER";

    // §2.7 characteristic validation (AC-SALE-01-18/19). Analyst catalog keys.
    public static final String VAL_CHAR_REQUIRED = "MSG-VAL-CHAR-REQUIRED";
    public static final String VAL_CHAR_FORMAT = "MSG-VAL-CHAR-FORMAT";

    // MSG-SALE-ORDER-CONFIRM is frontend-only (the AC-SALE-01-15 modal) and is
    // deliberately ABSENT here, same reasoning as MSG-PROD-NONE above.

    // Documented project addition (NOT in the analyst catalog — see
    // docs/api/product-service.md): FR-PROD-02's detail modal needs a clean
    // unknown-product outcome. Also answers a PNDG product, which is not yet a
    // product the customer owns (ADR-015 §5.5).
    public static final String PROD_NOT_FOUND = "MSG-PROD-NOT-FOUND";

    // Product-creation idempotency (ADR-015 idempotency addendum, 2026-08-06) — project
    // additions, no analyst key exists for any of this.
    /** A concurrent, still-in-flight POST /api/products for the same saleOperationId. */
    public static final String SALE_OPERATION_IN_PROGRESS = "MSG-SALE-OPERATION-IN-PROGRESS";
    /** POST /api/products/compensate named a product that belongs to a DIFFERENT
     *  sale operation — the sale-scoped ownership check refusing to touch it. */
    public static final String SALE_OPERATION_MISMATCH = "MSG-SALE-OPERATION-MISMATCH";

    // Established platform keys (same semantics as the other domain services).
    public static final String VALIDATION_ERROR = "MSG-VALIDATION-ERROR";
    public static final String INTERNAL_ERROR = "MSG-INTERNAL-ERROR";
    // account-service (FR-PROD-01 composition) OR customer-service (service-address
    // resolution) unreachable: fail closed, never a fabricated partial answer.
    public static final String SERVICE_UNAVAILABLE = "MSG-SERVICE-UNAVAILABLE";
    // Unscoped routing/protocol key (same pattern as the gateway's MSG-NOT-FOUND):
    // an unsupported HTTP method on an otherwise valid path is a client error, not
    // a server failure — must not fall into the generic 500 handler below.
    public static final String METHOD_NOT_ALLOWED = "MSG-METHOD-NOT-ALLOWED";
}
