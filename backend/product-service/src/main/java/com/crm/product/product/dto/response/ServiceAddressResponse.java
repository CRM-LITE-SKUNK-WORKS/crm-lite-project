package com.crm.product.product.dto.response;

/**
 * The FR-PROD-02 Service Address block, resolved through customer-service.
 * {@code addressId} is the public address identifier customer-service's own API
 * exposes (and account-service returns as billingAddressId) — not an internal key.
 */
public record ServiceAddressResponse(
        Long addressId,
        String street,
        String houseFlatNumber,
        String districtName,
        String cityName) {
}
