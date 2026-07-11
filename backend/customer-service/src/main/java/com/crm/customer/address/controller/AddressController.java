package com.crm.customer.address.controller;

import com.crm.customer.address.dto.AddressRequest;
import com.crm.customer.address.dto.AddressResponse;
import com.crm.customer.address.service.AddressService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-ADDR-01..05 under the customer aggregate (ADR-001). */
@RestController
@RequestMapping("/api/customers/{customerNumber}/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<List<AddressResponse>> list(@PathVariable Long customerNumber) {
        return ResponseEntity.ok(addressService.list(customerNumber));
    }

    @PostMapping
    public ResponseEntity<AddressResponse> add(@PathVariable Long customerNumber,
                                               @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.add(customerNumber, request));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponse> update(@PathVariable Long customerNumber,
                                                  @PathVariable Long addressId,
                                                  @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(addressService.update(customerNumber, addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(@PathVariable Long customerNumber, @PathVariable Long addressId) {
        addressService.delete(customerNumber, addressId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{addressId}/primary")
    public ResponseEntity<AddressResponse> setPrimary(@PathVariable Long customerNumber,
                                                      @PathVariable Long addressId) {
        return ResponseEntity.ok(addressService.setPrimary(customerNumber, addressId));
    }
}
