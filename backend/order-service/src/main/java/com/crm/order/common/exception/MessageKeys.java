package com.crm.order.common.exception;

public final class MessageKeys {

    private MessageKeys() {
    }

    // Documented project additions (ADR-016 §7 — the analyst catalog names no order
    // outcomes because §2.7 never describes an order being looked up or refused).
    public static final String ORDER_NOT_FOUND = "MSG-ORDER-NOT-FOUND";
    public static final String ORDER_DUP_NUMBER = "MSG-ORDER-DUP-NUMBER";
    public static final String ORDER_NUMBER_CAPACITY_EXCEEDED = "MSG-ORDER-NUMBER-CAPACITY-EXCEEDED";

    // Mirrored from account-service's contract (ADR-013 §6) because the sale's
    // precondition check produces them verbatim: an unknown/223 account number, and
    // AC-SALE-02-01's "only an Active account may be sold into".
    public static final String ACCT_NOT_FOUND = "MSG-ACCT-NOT-FOUND";
    public static final String ACCT_NOT_ACTIVE = "MSG-ACCT-NOT-ACTIVE";

    // MSG-SALE-* and MSG-VAL-CHAR-* are NOT declared here on purpose: they are
    // product-service's to produce (ADR-015 §6) and order-service passes them through
    // unchanged. Declaring them would invite someone to raise them from here, which
    // is exactly the duplicated basket logic the boundary exists to prevent.
    // MSG-SALE-ORDER-CONFIRM is frontend-only (the AC-SALE-01-15 modal).

    // Established platform keys (same semantics as the other domain services).
    public static final String VALIDATION_ERROR = "MSG-VALIDATION-ERROR";
    public static final String INTERNAL_ERROR = "MSG-INTERNAL-ERROR";
    // Any downstream (lookup, account or product service) unreachable during the
    // sale: fail closed — the order is CANCELLED and nothing the customer can see
    // was created (ADR-016 §5).
    public static final String SERVICE_UNAVAILABLE = "MSG-SERVICE-UNAVAILABLE";
}
