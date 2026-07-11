package com.crm.customer.lookup;

/** Client-side view of a GNL_TP catalog entry served by lookup-service. */
public record LookupTypeResponse(Long id, String shortCode, String name, String typeDomain) {
}
