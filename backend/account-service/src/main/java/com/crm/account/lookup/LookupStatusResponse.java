package com.crm.account.lookup;

/** Client-side view of a GNL_ST catalog entry served by lookup-service. */
public record LookupStatusResponse(Long id, String shortCode, String name, String statusDomain) {
}
