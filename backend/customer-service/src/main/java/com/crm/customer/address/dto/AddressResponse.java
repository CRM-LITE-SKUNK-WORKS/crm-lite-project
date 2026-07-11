package com.crm.customer.address.dto;

import com.crm.customer.address.entity.Address;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AddressResponse {

    private final Long addressId;
    private final Long cityId;
    private final String cityName;
    private final Long districtId;
    private final String districtName;
    private final String street;
    private final String houseFlatNumber;
    private final String addressDescription;
    private final boolean primary;

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getCity().getId(),
                address.getCity().getName(),
                address.getDistrict().getId(),
                address.getDistrict().getName(),
                address.getStreet(),
                address.getHouseFlatNo(),
                address.getAddressDescription(),
                address.isPrimary());
    }
}
