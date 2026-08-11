package com.crm.account.account.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /api/accounts/{accountNumber}/product-involvements (ADR-013 §8): the
 * involvement write command, invoked service-to-service by order-service at the
 * commit point of a sale (ADR-016 §5.1 step 4).
 *
 * <p>Bulk by design — one call per sale, never N calls — so the whole projection
 * update is a single local transaction and a partial link set can never be
 * observed (ADR-013 §8.1).
 *
 * <p>Unlike the FR-ACCT create/update DTOs this one deliberately does NOT reject
 * unknown properties with MSG-ACCT-IMMUTABLE-FIELD: that key answers "you tried to
 * set a field the user may not set", which is a form-submission concern. This is an
 * internal command with exactly one field, so a malformed body is an ordinary
 * MSG-VALIDATION-ERROR.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductInvolvementRequest {

    @NotEmpty
    private List<@Positive Long> productIds;

    /**
     * The SALE saga that owns the rows this command creates — the KR-12 order number
     * (ADR-018 §7). Optional and purely additive.
     *
     * <p>Null from the legacy synchronous route, which has no saga and needs none: it
     * never compensates an involvement, because ADR-016 §5 writes the involvement LAST
     * and nothing follows it. Set by the asynchronous saga, where the involvement is
     * written before activation and therefore may have to be undone — and where undoing
     * it safely means knowing exactly which rows this sale created.
     */
    @jakarta.validation.constraints.Size(max = 64)
    private String saleOperationId;
}
