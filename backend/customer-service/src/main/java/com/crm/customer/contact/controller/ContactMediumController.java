package com.crm.customer.contact.controller;

import com.crm.customer.contact.dto.ContactMediumRequest;
import com.crm.customer.contact.dto.ContactMediumResponse;
import com.crm.customer.contact.service.ContactMediumService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-CNTC-01/02 under the customer aggregate (ADR-001). */
@RestController
@RequestMapping("/api/customers/{customerNumber}/contact-medium")
@RequiredArgsConstructor
public class ContactMediumController {

    private final ContactMediumService contactMediumService;

    @GetMapping
    public ResponseEntity<ContactMediumResponse> get(@PathVariable Long customerNumber) {
        return ResponseEntity.ok(contactMediumService.get(customerNumber));
    }

    @PutMapping
    public ResponseEntity<ContactMediumResponse> update(@PathVariable Long customerNumber,
                                                        @Valid @RequestBody ContactMediumRequest request) {
        return ResponseEntity.ok(contactMediumService.update(customerNumber, request));
    }
}
