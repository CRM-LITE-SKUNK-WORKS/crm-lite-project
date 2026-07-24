package com.crm.account.customer;

/**
 * Client-side view of one row of customer-service's active address list
 * ({@code GET /api/customers/{customerNumber}/addresses}) — only the fields the
 * account domain needs. {@code addressId} is the identifier account-service stores
 * as its external {@code address_id} reference (ADR-013 §2.4).
 */
public record CustomerAddress(Long addressId, boolean primary) {
}
