package com.crm.account.common.exception;

public final class MessageKeys {

    private MessageKeys() {
    }

    // From the FR/AC v8-1 Final message catalog (23.07.2026 revision).
    // MSG-ACCT-DELETE-CONFIRM and MSG-ACCT-DELETED are frontend-only keys and are
    // deliberately ABSENT here: the backend never produces them (ADR-013 §3.5).
    public static final String ACCT_HAS_PRODUCTS = "MSG-ACCT-HAS-PRODUCTS";
    public static final String CUST_NOT_FOUND = "MSG-CUST-NOT-FOUND";

    // Documented project additions (ADR-013 §6; EN/TR texts in
    // docs/architecture/account-service-decisions.md).
    public static final String ACCT_NOT_FOUND = "MSG-ACCT-NOT-FOUND";
    public static final String ACCT_NOT_ACTIVE = "MSG-ACCT-NOT-ACTIVE";
    public static final String ACCT_IMMUTABLE_FIELD = "MSG-ACCT-IMMUTABLE-FIELD";
    public static final String ACCT_DUP_NUMBER = "MSG-ACCT-DUP-NUMBER";
    public static final String ACCT_NUMBER_CAPACITY_EXCEEDED = "MSG-ACCT-NUMBER-CAPACITY-EXCEEDED";

    // Established platform keys (same semantics as customer-service).
    public static final String VALIDATION_ERROR = "MSG-VALIDATION-ERROR";
    public static final String INTERNAL_ERROR = "MSG-INTERNAL-ERROR";
    // Lookup catalog (ADR-002) OR customer-service (ADR-013) unreachable during a
    // write: fail closed, nothing persisted.
    public static final String SERVICE_UNAVAILABLE = "MSG-SERVICE-UNAVAILABLE";
}
