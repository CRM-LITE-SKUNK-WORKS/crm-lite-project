package com.crm.product.catalog.controller;

import com.crm.product.catalog.dto.CharacteristicResponse;
import com.crm.product.catalog.dto.OfferResponse;
import com.crm.product.catalog.service.CatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only offer catalog (Offer Selection support for the future §2.7 sale flow). */
@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class OfferController {

    private final CatalogService catalogService;

    @GetMapping
    public ResponseEntity<List<OfferResponse>> list() {
        return ResponseEntity.ok(catalogService.listOffers());
    }

    /**
     * AC-SALE-01-10/17/20/21: the configurable fields the Product Configuration
     * screen must render for this offer. Empty list = an offer with no
     * characteristics (AC-SALE-01-21), which is a valid answer, not a 404.
     */
    @GetMapping("/{offerId}/characteristics")
    public ResponseEntity<List<CharacteristicResponse>> characteristics(@PathVariable long offerId) {
        return ResponseEntity.ok(catalogService.listOfferCharacteristics(offerId));
    }
}
