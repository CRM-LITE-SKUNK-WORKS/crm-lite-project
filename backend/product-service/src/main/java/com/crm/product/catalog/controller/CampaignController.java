package com.crm.product.catalog.controller;

import com.crm.product.catalog.dto.CampaignResponse;
import com.crm.product.catalog.service.CatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only campaign catalog (member offers + derived total price). */
@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CatalogService catalogService;

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> list() {
        return ResponseEntity.ok(catalogService.listCampaigns());
    }
}
