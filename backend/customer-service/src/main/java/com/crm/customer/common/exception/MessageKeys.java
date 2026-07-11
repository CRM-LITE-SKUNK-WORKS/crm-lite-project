package com.crm.customer.common.exception;

public final class MessageKeys {

    private MessageKeys() {
    }

    // From the FR/AC v8 Final message catalog.
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

    // Documented project additions (not in the original catalog — see
    // docs/requirements/functional-requirements.md): framework-level failures and
    // service-integration outcomes that the catalog does not name.
    public static final String SEARCH_CRITERIA_REQUIRED = "MSG-SEARCH-CRITERIA-REQUIRED";
    public static final String FEATURE_NOT_IMPLEMENTED = "MSG-FEATURE-NOT-IMPLEMENTED";
    public static final String VALIDATION_ERROR = "MSG-VALIDATION-ERROR";
    public static final String INTERNAL_ERROR = "MSG-INTERNAL-ERROR";
    // KR-10: fake MERNIS rejected the Nationality ID (customer must not be created).
    public static final String NATID_VERIFY_FAILED = "MSG-NATID-VERIFY-FAILED";
    // Shared catalog (ADR-002) or MERNIS (KR-10) unreachable: writes fail closed.
    public static final String SERVICE_UNAVAILABLE = "MSG-SERVICE-UNAVAILABLE";
    // Backend guards for FR-ADDR-04 rules the UI normally prevents.
    public static final String ADDR_LAST_DELETE = "MSG-ADDR-LAST-DELETE";
    public static final String ADDR_PRIMARY_DELETE = "MSG-ADDR-PRIMARY-DELETE";
}
