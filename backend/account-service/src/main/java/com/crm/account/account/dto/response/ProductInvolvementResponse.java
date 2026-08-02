package com.crm.account.account.dto.response;

import java.util.List;

/**
 * Result of the involvement write command (ADR-013 §8): the account's full set of
 * involved product ids after the call, in the same shape and ordering as
 * {@code GET /api/accounts/{accountNumber}/product-ids} (§7).
 *
 * <p>Returning the resulting state rather than "how many rows were inserted" is
 * what makes the command's idempotency (§8.4) observable: a retry answers with the
 * same list instead of a different insert count.
 */
public record ProductInvolvementResponse(
        String accountNumber,
        List<Long> productIds) {
}
