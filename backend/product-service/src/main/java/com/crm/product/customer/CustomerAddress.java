package com.crm.product.customer;

/**
 * Client-side view of one customer-service address
 * ({@code GET /api/addresses/{addressId}}, the internal service-to-service
 * resolution endpoint) — only the fields the FR-PROD-02 Service Address display
 * needs. {@code addressId} is the same public identifier customer-service's
 * address API exposes and account-service stores as {@code billingAddressId}.
 */
public record CustomerAddress(
        Long addressId,
        String cityName,
        String districtName,
        String street,
        String houseFlatNumber,
        String addressDescription,
        boolean primary) {
}
