package com.crm.order.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A characteristic value, forwarded to product-service verbatim (ADR-015 §6).
 *
 * <p>Only shape is validated here — present id, bounded length. Emptiness and format
 * are AC-SALE-01-18/19 rules with their own analyst keys, and they belong to
 * product-service, which alone knows the declared data type. Adding a
 * {@code @NotBlank} here would silently pre-empt MSG-VAL-CHAR-REQUIRED with a
 * generic 400 and break the very contract this boundary exists to preserve.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderCharacteristicRequest {

    @NotNull
    @Positive
    private Long characteristicId;

    @Size(max = 250)
    private String value;
}
