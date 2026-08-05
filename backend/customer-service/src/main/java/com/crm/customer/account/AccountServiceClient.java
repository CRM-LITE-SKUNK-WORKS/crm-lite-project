package com.crm.customer.account;

import java.util.List;
import java.util.Optional;

/**
 * account-service boundary. ADR-013 §5: {@code account_db} has exactly one public
 * reading point and it is this API — customer-service never touches that database,
 * never shares its entities and never joins across it.
 *
 * <p>Two independent customer-service flows meet here, and they stay independent:
 * <ul>
 *   <li><b>Customer delete (AC-CUST-05-04)</b> — {@link #listAccounts} +
 *       {@link #passivateAccount}: passivate every Billing Account before the local
 *       aggregate is touched;
 *   <li><b>KR-02 Account Number search (AC-CUST-01-04)</b> — {@link #fetchAccount}:
 *       resolve one public account number to the customer that owns it.
 * </ul>
 *
 * <p>Reached directly via Eureka with the end user's token propagated (ADR-010), never
 * through the gateway, which is the browser edge (ADR-007).
 */
public interface AccountServiceClient {

    /** The customer's Billing Accounts (224 only — the K-8 223 never appears here, ADR-013 §4.5). */
    List<AccountSummary> listAccounts(Long customerNumber);

    /** Passivates one account. Not idempotent: a Passive account rejects this with 409 (ADR-013 §3.5). */
    void passivateAccount(String accountNumber);

    /**
     * One account by its public KR-11 number, for the search criterion.
     *
     * <p>Three outcomes that must stay distinct:
     * <ul>
     *   <li>{@code Optional.empty()} — no visible account with that number: unknown, or
     *       the K-8 223 Customer Account, which is indistinguishable from unknown by
     *       design (ADR-013 §4.5) and must therefore never be searchable;
     *   <li>a summary — the account exists; its {@code accountStatus} says whether it is
     *       still Active;
     *   <li>{@link AccountServiceUnavailableException} — nothing is known. The search
     *       must fail closed rather than silently answer as if nothing matched.
     * </ul>
     */
    Optional<AccountSummary> fetchAccount(String accountNumber);
}
