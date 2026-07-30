package com.crm.product.product.dto.response;

/**
 * One row of the FR-PROD-01 product table (AC-PROD-01-03). {@code productId} is
 * the public product identifier (prod.id); {@code campaignId} is the PUBLIC
 * campaign code (cmpg.campaign_code) — internal campaign ids never leave this
 * service. Products bought outside a campaign carry {@code null} campaign fields
 * (the "-" rendering is the frontend's job); {@code productStatus} is
 * "Active"/"Passive". There is no "Action" field — that column is a pure UI
 * concern (AC-PROD-01-04).
 */
public record ProductRowResponse(
        Long productId,
        String productName,
        String campaignName,
        String campaignId,
        String productStatus) {
}
