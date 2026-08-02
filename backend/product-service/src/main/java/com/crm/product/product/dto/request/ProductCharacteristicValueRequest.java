package com.crm.product.product.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One characteristic value entered on the Product Configuration screen
 * (AC-SALE-01-10). {@code value} is accepted as a raw string and validated against
 * the characteristic's declared {@code data_type} here in product-service — the
 * caller (order-service) forwards it verbatim and holds no validation logic of its
 * own (ADR-015 §6).
 *
 * <p>Bean validation covers only shape (present, not oversized); emptiness and format
 * are business rules with their own analyst message keys
 * (MSG-VAL-CHAR-REQUIRED / MSG-VAL-CHAR-FORMAT), so they are NOT expressed as
 * annotations — a blank optional value is legal and must not become a 400.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductCharacteristicValueRequest {

    @NotNull
    @Positive
    private Long characteristicId;

    @Size(max = 250)
    private String value;
}
