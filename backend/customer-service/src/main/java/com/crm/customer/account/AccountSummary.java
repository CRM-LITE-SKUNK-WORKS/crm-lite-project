package com.crm.customer.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The slice of account-service's representation the customer delete flow needs.
 *
 * <p>Unknown properties are ignored on purpose: account-service owns that contract
 * and may add fields; a consumer that breaks on additive changes would make every
 * upstream improvement a coordinated release.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountSummary(String accountNumber, String accountStatus) {

    public boolean isActive() {
        return "Active".equals(accountStatus);
    }
}
