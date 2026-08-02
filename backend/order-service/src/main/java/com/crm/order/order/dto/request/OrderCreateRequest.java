package com.crm.order.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /api/orders — the single Submit-Order command (AC-SALE-01-15, ADR-016 §3.1).
 *
 * <p>The whole basket arrives in ONE body because the basket is frontend/session
 * state and is never persisted incrementally (ADR-016 §2.6). That is what makes
 * AC-SALE-01-16 ("an abandoned sale must never be processed later") true by
 * construction rather than by a cleanup job: there is nothing to abandon.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderCreateRequest {

    /**
     * The KR-11 billing account the sale was started from (AC-SALE-01-01). Ten
     * digits — validated for shape here so an obviously malformed number is a clean
     * 400 rather than a downstream 404 that would read as "no such account".
     */
    @NotBlank
    @Pattern(regexp = "\\d{10}")
    private String accountNumber;

    /** AC-SALE-01-11: the service address chosen for the main product. */
    @NotNull
    @Positive
    private Long serviceAddressId;

    /** Optional PUBLIC campaign code (e.g. CMP-ADSL-01), never an internal id. */
    private String campaignId;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;
}
