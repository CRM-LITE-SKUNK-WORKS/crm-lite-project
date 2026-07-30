package com.crm.product.product.dto.response;

/**
 * The FR-PROD-02 detail modal (AC-PROD-02-01): Product Offer Name, Product Offer
 * ID, Product Spec ID, Campaign, Service Address. {@code campaign} is the campaign
 * name ({@code null} when the product was bought outside a campaign);
 * {@code serviceAddress} is the product's own service address or — for a child
 * product — its parent's ({@code null} only when neither carries one).
 */
public record ProductDetailResponse(
        String productOfferName,
        Long productOfferId,
        Long productSpecId,
        String campaign,
        ServiceAddressResponse serviceAddress) {
}
