package com.crm.product.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /api/products (ADR-015 §5.1): creates one whole installation — a main product
 * and its children — in a single local transaction. Bulk by design: a per-product
 * endpoint would make a partially-created installation observable.
 *
 * <p>Invoked service-to-service by order-service at step 2 of the sale orchestration
 * (ADR-016 §5.1). Everything it carries is basket content the user assembled on the
 * Offer Selection and Product Configuration screens.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductCreateRequest {

    /**
     * The PUBLIC business customer number the sale belongs to. Not stored — `PROD`
     * deliberately has no customer column (ADR-013 §5) — but required, because it is
     * the only way to check that {@link #serviceAddressId} actually belongs to this
     * customer (ADR-015 §5.9). The caller knows it: order-service reads it from the
     * billing account's representation.
     */
    @NotNull
    @Positive
    private Long customerNumber;

    /**
     * AC-SALE-01-11: the service address chosen for the MAIN product. Children
     * inherit it by displaying their parent's (FR-PROD-02), so it is stored only on
     * the main row. An FK-less external reference into customer_db (ADR-015 §2.3),
     * validated against the customer's active address list before persisting.
     */
    @NotNull
    @Positive
    private Long serviceAddressId;

    /**
     * Optional PUBLIC campaign code (`cmpg.campaign_code`, e.g. CMP-ADSL-01) — never
     * the internal id. Null for a basket assembled offer-by-offer outside any
     * campaign. When present it must be Active and every submitted offer must be one
     * of its members (ADR-015 §6).
     */
    private String campaignId;

    @NotEmpty
    @Valid
    private List<ProductCreateItemRequest> items;

    /**
     * Stable operation identifier (ADR-015 idempotency addendum, 2026-08-06):
     * order-service forwards its own client-supplied {@code Idempotency-Key} here
     * verbatim. A second call carrying an id already seen returns the FIRST call's
     * response unchanged, dedup'd by a database UNIQUE constraint — not merely an
     * in-memory check — so a re-entered order-service submit cannot create a second
     * set of PNDG products (ADR-016 §5.3b's original httpclient5-retry incident is
     * exactly the failure mode this closes off one layer further up the call chain).
     */
    @NotBlank
    @Size(max = 64)
    private String saleOperationId;
}
