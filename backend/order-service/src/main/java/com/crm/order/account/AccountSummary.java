package com.crm.order.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The slice of account-service's representation the sale needs: who owns the account
 * and whether it may be sold into.
 *
 * <p>{@code customerNumber} is the field added by the ADR-013 §3.6 amendment — the
 * reason order-service can record {@code cust_ord.customer_number} while knowing
 * only an account number.
 *
 * <p>Unknown properties are ignored on purpose: account-service owns that contract
 * and may add fields (it just did); a consumer that breaks on additive changes would
 * make every upstream improvement a coordinated release.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountSummary(String accountNumber, Long customerNumber, String accountStatus) {

    /** AC-SALE-02-01: only an Active billing account may start a new sale. */
    public boolean isActive() {
        return "Active".equals(accountStatus);
    }
}
