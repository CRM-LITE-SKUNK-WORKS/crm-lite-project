package com.crm.product.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of {@code POST /api/products/compensate} (ADR-015 §5.7, revised by the
 * idempotency addendum, 2026-08-06). Replaces the old generic
 * {@code /cancel(productIds)} contract: {@code saleOperationId} scopes the call to
 * ONE sale — the endpoint refuses to touch a product created by a different
 * operation, rather than passivating whatever PNDG ids it is handed.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductCompensationRequest {

    /** The same id {@link ProductCreateRequest#getSaleOperationId()} carried — the
     *  ownership key every requested product must match. */
    @NotBlank
    @Size(max = 64)
    private String saleOperationId;

    @NotEmpty
    private List<@Positive Long> productIds;
}
