package com.crm.order.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code POST /api/orders/drafts} — everything the backend needs when Start New Sale
 * begins (ADR-018 §6).
 *
 * <p><b>One field, deliberately.</b> The draft exists to give the sale an identity, not
 * to record a basket: at Start New Sale the user has chosen an account and nothing else.
 * Accepting offers here would create a second place a basket can live and immediately
 * raise the question of which one Submit is authoritative for.
 *
 * <p><b>No customer identity.</b> The customer number is resolved from the billing
 * account, never accepted from the browser — the same rule the rest of the sale follows
 * (ADR-015 §5.9), and the reason a caller cannot start a sale for someone else's account
 * by supplying their customer number.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderDraftCreateRequest {

    /** The KR-11 billing account the sale is started from (AC-SALE-01-01). */
    @NotBlank
    @Pattern(regexp = "\\d{10}")
    private String accountNumber;
}
