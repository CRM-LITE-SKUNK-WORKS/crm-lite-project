package com.crm.order.account;

/**
 * account-service refused the involvement write because the target account is
 * Passive (409 MSG-ACCT-NOT-ACTIVE, ADR-013 §8.2). Distinct from an availability
 * failure: the account IS reachable and known, it simply may not be sold into
 * (AC-SALE-02-01), so this must surface as the account's own 409 rather than a
 * generic 503.
 */
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(String accountNumber) {
        super("Account " + accountNumber + " is passive and cannot receive products");
    }
}
