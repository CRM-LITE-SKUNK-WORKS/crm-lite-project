package com.crm.customer.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * FR-ADDR-02: all address fields are mandatory; District must belong to the selected
 * City (validated in the service layer). {@code primary} is optional — on the very
 * first address it is forced to true regardless (AC-ADDR-02-04).
 */
@Getter
@Setter
public class AddressRequest {

    @NotNull(message = "cityId is required")
    private Long cityId;

    @NotNull(message = "districtId is required")
    private Long districtId;

    @NotBlank(message = "street is required")
    @Size(max = 200)
    private String street;

    @NotBlank(message = "houseFlatNumber is required")
    @Size(max = 20)
    private String houseFlatNumber;

    @NotBlank(message = "addressDescription is required")
    @Size(max = 200)
    private String addressDescription;

    private Boolean primary;

    public boolean isPrimaryRequested() {
        return Boolean.TRUE.equals(primary);
    }
}
