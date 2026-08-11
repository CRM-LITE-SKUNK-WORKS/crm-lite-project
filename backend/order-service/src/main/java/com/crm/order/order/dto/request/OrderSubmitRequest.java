package com.crm.order.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code POST /api/orders/{orderNumber}/submit} — the basket, at the moment the user
 * confirms Submit (AC-SALE-01-15, ADR-018 §6).
 *
 * <p>Identical to {@link OrderCreateRequest} minus {@code accountNumber}, and that
 * omission is the point: the account was fixed when the draft was created and is read
 * from the draft. Accepting it again would let a caller submit basket A against draft B's
 * account, which no amount of validation downstream can make meaningful.
 *
 * <p>The basket still arrives whole, in one body: it remains frontend/session state that
 * the backend learns about exactly once (ADR-016 §2.6). What the draft changed is that
 * the ORDER exists beforehand — not the basket.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderSubmitRequest {

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
