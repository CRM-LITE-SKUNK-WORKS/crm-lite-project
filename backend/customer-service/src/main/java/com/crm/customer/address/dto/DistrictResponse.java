package com.crm.customer.address.dto;

import com.crm.customer.address.entity.District;

public record DistrictResponse(Long districtId, String name) {

    public static DistrictResponse from(District district) {
        return new DistrictResponse(district.getId(), district.getName());
    }
}
