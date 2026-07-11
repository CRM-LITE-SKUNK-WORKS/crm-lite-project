package com.crm.lookup.catalog.dto;

import com.crm.lookup.catalog.entity.GeneralStatus;

public record LookupStatusResponse(Long id, String shortCode, String name, String statusDomain) {

    public static LookupStatusResponse from(GeneralStatus entity) {
        return new LookupStatusResponse(entity.getId(), entity.getShortCode(), entity.getName(), entity.getStatusDomain());
    }
}
