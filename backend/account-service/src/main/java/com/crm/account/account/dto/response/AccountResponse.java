package com.crm.account.account.dto.response;

/**
 * The single account representation (list row, detail, create/update result —
 * ADR-013 §3). Never exposes internal ids (cust_acct.id, acct_tp.id) and carries
 * no "Action" field (UI concern). {@code accountStatus} is "Active"/"Passive".
 */
public record AccountResponse(
        String accountNumber,
        String accountName,
        String accountTypeCode,
        String accountTypeName,
        Long billingAddressId,
        String accountStatus) {
}
