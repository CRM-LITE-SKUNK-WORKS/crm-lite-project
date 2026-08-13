package com.crm.product.product.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of the two lifecycle commands (ADR-015 §5.6/§5.7): {@code confirm} promotes
 * PNDG products to ACTV, {@code cancel} soft-passivates them. Both take the ids the
 * create call returned, so order-service never has to model product state itself.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductLifecycleRequest {

    @NotEmpty
    private List<@NotNull @Positive Long> productIds;
}
