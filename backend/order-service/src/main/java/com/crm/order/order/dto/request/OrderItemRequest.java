package com.crm.order.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One basket entry: the offer and the characteristic values entered for it. */
@Getter
@Setter
@NoArgsConstructor
public class OrderItemRequest {

    @NotNull
    @Positive
    private Long offerId;

    /** Empty for an offer with no configurable characteristics (AC-SALE-01-21). */
    @Valid
    private List<OrderCharacteristicRequest> characteristics = new ArrayList<>();
}
