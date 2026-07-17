package com.crm.customer.common.exception;

public final class MessageKeys {

    private MessageKeys() {
    }

    // From the FR/AC v8 Final message catalog (2026-07-16 revision).
    public static final String CUST_NOT_FOUND = "MSG-CUST-NOT-FOUND";
    public static final String CUST_DUP_NATID = "MSG-CUST-DUP-NATID";
    public static final String CUST_HAS_PRODUCTS = "MSG-CUST-HAS-PRODUCTS";
    public static final String ADDR_IN_USE = "MSG-ADDR-IN-USE";
    public static final String VAL_NATID = "MSG-VAL-NATID";
    public static final String VAL_BIRTHDATE = "MSG-VAL-BIRTHDATE";
    public static final String VAL_AGE_MIN = "MSG-VAL-AGE-MIN";
    public static final String VAL_NAME = "MSG-VAL-NAME";
    public static final String VAL_EMAIL = "MSG-VAL-EMAIL";
    public static final String VAL_PHONE = "MSG-VAL-PHONE";
    // KR-10 / AC-CUST-03-06 (v8 Final catalog keys — replaced the older project-specific
    // MSG-NATID-VERIFY-FAILED and the shared MSG-SERVICE-UNAVAILABLE for MERNIS):
    // rejection means the customer must not be created; unavailability fails closed.
    public static final String CUST_NATID_VERIFICATION_FAILED = "MSG-CUST-NATID-VERIFICATION-FAILED";
    public static final String MERNIS_UNAVAILABLE = "MSG-MERNIS-UNAVAILABLE";

    // Documented project additions (not in the analyst catalog — see
    // docs/requirements/functional-requirements.md): framework-level failures and
    // service-integration outcomes that the catalog does not name.
    // (MSG-SEARCH-CRITERIA-REQUIRED was removed with the mandatory-criteria rule, ADR-005.)
    public static final String FEATURE_NOT_IMPLEMENTED = "MSG-FEATURE-NOT-IMPLEMENTED";
    public static final String VALIDATION_ERROR = "MSG-VALIDATION-ERROR";
    public static final String INTERNAL_ERROR = "MSG-INTERNAL-ERROR";
    // Shared catalog (ADR-002) unreachable: writes fail closed (MERNIS outages use the
    // catalog key MSG-MERNIS-UNAVAILABLE above).
    public static final String SERVICE_UNAVAILABLE = "MSG-SERVICE-UNAVAILABLE";
    // Backend guards for FR-ADDR-04 rules the UI normally prevents.
    public static final String ADDR_LAST_DELETE = "MSG-ADDR-LAST-DELETE";
    public static final String ADDR_PRIMARY_DELETE = "MSG-ADDR-PRIMARY-DELETE";
}
