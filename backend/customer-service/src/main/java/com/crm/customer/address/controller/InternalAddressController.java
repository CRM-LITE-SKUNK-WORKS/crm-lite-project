package com.crm.customer.address.controller;

import com.crm.customer.address.dto.AddressResponse;
import com.crm.customer.address.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * INTERNAL service-to-service address resolution (documented project addition,
 * 29.07.2026 — see docs/api/customer-service.md). product-service resolves the
 * FK-less {@code prod.service_address_id} reference here for the FR-PROD-02
 * Service Address block: it holds only the bare address id, so the
 * customer-scoped {@code /api/customers/{n}/addresses} list cannot serve it.
 *
 * Deliberately NOT routed through the gateway (no browser use case — the
 * customer-scoped API remains the only public address surface); callers reach it
 * directly via Eureka and still need a valid crm-user JWT (ADR-009/010).
 * Read-only: active addresses only; soft-deleted = 404 (same contract as the
 * customer-scoped lookups).
 */
@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class InternalAddressController {

    private final AddressService addressService;

    @GetMapping("/{addressId}")
    public ResponseEntity<AddressResponse> detail(@PathVariable Long addressId) {
        return ResponseEntity.ok(addressService.getActiveAddress(addressId));
    }
}
