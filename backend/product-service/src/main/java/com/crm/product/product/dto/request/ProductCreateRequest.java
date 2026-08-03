package com.crm.product.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
}
