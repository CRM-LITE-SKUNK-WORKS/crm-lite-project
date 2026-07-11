package com.crm.customer.address.dto;

import com.crm.customer.address.entity.City;

public record CityResponse(Long cityId, String name) {

    public static CityResponse from(City city) {
        return new CityResponse(city.getId(), city.getName());
    }
}
