package com.crm.order.account;

import java.util.List;
import java.util.Optional;

/**
 * account-service boundary (ADR-016 §5): the sale's precondition read and its commit
 * point. Reached directly via Eureka with the user's token propagated (ADR-010),
 * never through the gateway.
 *
 * <p>Implementations distinguish three outcomes and must never blur them:
 * <ul>
 *   <li>{@code Optional.empty()} — the account is not visible (unknown number or the
 *       K-8 223, indistinguishable by design, ADR-013 §4.5);
 *   <li>{@link AccountNotActiveException} — visible but Passive, so it may not be
 *       sold into (AC-SALE-02-01);
 *   <li>{@link AccountServiceUnavailableException} — nothing is known; fail closed.
 * </ul>
 */
public interface AccountServiceClient {

    /** Precondition read: exists, is a 224, and its Active/Passive state + owner. */
    Optional<AccountSummary> fetchAccount(String accountNumber);

    /**
     * The involvement write command (ADR-013 §8) — the sale's COMMIT POINT. Bulk and
     * idempotent per (account, product): a retry is safe.
     */
    void linkProducts(String accountNumber, List<Long> productIds);
}
