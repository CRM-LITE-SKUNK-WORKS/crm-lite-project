package com.crm.product.account;

import java.util.List;
import java.util.Optional;

/**
 * Transport boundary to account-service — the single owner of the product ↔
 * billing-account link (ADR-013 §5). FR-PROD-01 composes over this API; this
 * service NEVER reads or writes account_db directly.
 *
 * Implementations return {@code Optional.empty()} for an unknown (or K-8-hidden
 * 223) account number and throw {@link AccountServiceUnavailableException} for any
 * transport/availability failure — the list fails closed on that (503). Mockable
 * in tests; production code must never bypass this boundary.
 */
public interface AccountServiceClient {

    /**
     * The account's involved product ids
     * ({@code GET /api/accounts/{accountNumber}/product-ids}): non-deleted
     * involvement rows only, but deliberately NOT filtered by involvement status —
     * AC-PROD-01-03 lists passive products too; the displayed status comes from
     * the product itself.
     */
    Optional<List<Long>> fetchProductIds(String accountNumber);
}
