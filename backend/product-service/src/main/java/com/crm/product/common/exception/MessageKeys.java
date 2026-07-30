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

    // Documented project addition (NOT in the analyst catalog — see
    // docs/api/product-service.md): FR-PROD-02's detail modal needs a clean
    // unknown-product outcome.
    public static final String PROD_NOT_FOUND = "MSG-PROD-NOT-FOUND";

    // Established platform keys (same semantics as the other domain services).
    public static final String VALIDATION_ERROR = "MSG-VALIDATION-ERROR";
    public static final String INTERNAL_ERROR = "MSG-INTERNAL-ERROR";
    // account-service (FR-PROD-01 composition) OR customer-service (service-address
    // resolution) unreachable: fail closed, never a fabricated partial answer.
    public static final String SERVICE_UNAVAILABLE = "MSG-SERVICE-UNAVAILABLE";
}
