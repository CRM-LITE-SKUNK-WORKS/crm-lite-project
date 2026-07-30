package com.crm.product.product.controller;

import com.crm.product.product.dto.response.ProductDetailResponse;
import com.crm.product.product.dto.response.ProductRowResponse;
import com.crm.product.product.service.ProductService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-PROD-01..02 — read-only Phase A slice: exactly these two endpoints. No
 * pagination (FR-PROD-01 defines none), no product creation/cancellation
 * (AC-PROD-01-04: out of this phase). {@code accountNumber} is the public KR-11
 * billing-account number; {@code id} the public product identifier (prod.id).
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductRowResponse>> list(@RequestParam("accountNumber") String accountNumber) {
        return ResponseEntity.ok(productService.list(accountNumber));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> detail(@PathVariable long id) {
        return ResponseEntity.ok(productService.getById(id));
    }
}
