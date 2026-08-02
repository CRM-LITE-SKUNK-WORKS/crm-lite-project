package com.crm.product.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One basket entry: the offer being bought plus the characteristic values entered
 * for it. Which of these becomes the MAIN product is NOT declared by the caller —
 * it is derived from the offer's service type (AC-SALE-01-09, ADR-015 §5.3), so the
 * FR rule cannot be violated by a client.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductCreateItemRequest {

    @NotNull
    @Positive
    private Long offerId;

    /** Empty for an offer with no configurable characteristics (AC-SALE-01-21). */
    @Valid
    private List<ProductCharacteristicValueRequest> characteristics = new ArrayList<>();
}
