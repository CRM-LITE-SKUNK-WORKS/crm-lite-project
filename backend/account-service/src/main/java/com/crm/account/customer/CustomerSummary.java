package com.crm.account.customer;

/**
 * Client-side view of customer-service's detail payload — only the fields the
 * account domain needs. Unknown response fields are ignored on deserialization.
 * {@code status} is the GNL_ST short code ("ACTV").
 */
public record CustomerSummary(Long customerNumber, String status) {
}
