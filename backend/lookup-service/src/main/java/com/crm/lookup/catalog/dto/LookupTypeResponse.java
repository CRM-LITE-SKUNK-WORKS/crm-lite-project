package com.crm.lookup.catalog.dto;

import com.crm.lookup.catalog.entity.GeneralType;

public record LookupTypeResponse(Long id, String shortCode, String name, String typeDomain) {

    public static LookupTypeResponse from(GeneralType entity) {
        return new LookupTypeResponse(entity.getId(), entity.getShortCode(), entity.getName(), entity.getTypeDomain());
    }
}
