package com.crm.account.account.dto.response;

/**
 * The single account representation (list row, detail, create/update result —
 * ADR-013 §3). Never exposes internal ids (cust_acct.id, acct_tp.id) and carries
 * no "Action" field (UI concern). {@code accountStatus} is "Active"/"Passive".
 *
 * <p>{@code customerNumber} was added by the ADR-013 §3.6 amendment: it is the same
 * PUBLIC business number the list endpoint already takes as its {@code customerId}
 * query parameter, not an internal id. order-service needs it to record
 * {@code cust_ord.customer_number} (ADR-016 §2.3) while knowing only the account it
 * is selling into. Purely additive — no field was removed or renamed.
 */
public record AccountResponse(
        String accountNumber,
        Long customerNumber,
        String accountName,
        String accountTypeCode,
        String accountTypeName,
        Long billingAddressId,
        String accountStatus) {
}
