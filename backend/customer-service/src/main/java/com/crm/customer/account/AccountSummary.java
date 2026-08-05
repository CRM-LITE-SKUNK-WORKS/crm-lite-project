package com.crm.customer.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The slice of account-service's {@code AccountResponse} that customer-service needs.
 *
 * <p>{@code accountNumber} + {@code accountStatus} serve the customer-delete flow
 * (AC-CUST-05-04: which accounts exist, which are still Active). {@code customerNumber}
 * serves the KR-02 Account Number search: it is the PUBLIC business customer number
 * (added by the ADR-013 §3.6 amendment) — exactly the identifier
 * {@code cust.customer_number} carries, so no id translation is needed anywhere.
 *
 * <p>Unknown properties are ignored on purpose: account-service owns that contract
 * and may add fields; a consumer that breaks on additive changes would make every
 * upstream improvement a coordinated release.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountSummary(String accountNumber, Long customerNumber, String accountStatus) {

    /**
     * account-service derives this label from its own soft-delete invariant
     * ({@code AccountContract.STATUS_LABEL_ACTIVE}, never stored): "Passive" means the
     * row was passivated by FR-ACCT-04, which is that domain's soft delete. Reading the
     * published label rather than re-deriving a status is the established consumer
     * pattern (order-service's own {@code AccountSummary.isActive}).
     */
    public boolean isActive() {
        return "Active".equals(accountStatus);
    }
}
