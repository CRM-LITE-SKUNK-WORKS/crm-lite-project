package com.crm.customer.account;

/**
 * account-service refused to passivate the account because it still has active
 * products (409 MSG-ACCT-HAS-PRODUCTS, ADR-013 §3.5). Distinct from an availability
 * failure: account-service IS reachable and answered correctly, so this must surface
 * as the customer's own MSG-CUST-HAS-PRODUCTS conflict rather than a generic 503.
 */
public class AccountHasActiveProductsException extends RuntimeException {

    public AccountHasActiveProductsException(String accountNumber) {
        super("Account " + accountNumber + " has active products and cannot be passivated");
    }
}
