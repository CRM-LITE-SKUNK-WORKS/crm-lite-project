package com.crm.product.catalog.controller;

import com.crm.product.catalog.dto.OfferResponse;
import com.crm.product.catalog.service.CatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
