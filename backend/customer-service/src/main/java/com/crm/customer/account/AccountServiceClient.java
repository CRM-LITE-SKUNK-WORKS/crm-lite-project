package com.crm.customer.account;

import java.util.List;

/**
 * account-service boundary for the customer delete flow (AC-CUST-05-04): passivating
 * the customer's billing accounts. Reached directly via Eureka with the user's token
 * propagated (ADR-010), never through the gateway.
 */
public interface AccountServiceClient {

    /** The customer's Billing Accounts (224 only — the K-8 223 never appears here, ADR-013 §4.5). */
    List<AccountSummary> listAccounts(Long customerNumber);

    /** Passivates one account. Not idempotent: a Passive account rejects this with 409 (ADR-013 §3.5). */
    void passivateAccount(String accountNumber);
}
