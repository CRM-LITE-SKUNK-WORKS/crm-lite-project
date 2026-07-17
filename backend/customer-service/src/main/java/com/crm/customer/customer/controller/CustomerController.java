package com.crm.customer.customer.controller;

import com.crm.customer.customer.dto.request.CustomerCreateRequest;
import com.crm.customer.customer.dto.request.CustomerUpdateRequest;
import com.crm.customer.customer.dto.response.CustomerDetailResponse;
import com.crm.customer.customer.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canonical customer API. GET /api/customers is the single list AND filter endpoint
 * (ADR-005): with no query parameters it browses all active customers (AC-CUST-01-00),
 * with parameters it filters (KR-01); the old /api/customers/search alias was removed —
 * no released consumers. All public identifiers ({customerNumber} path variables and
 * the customerId query parameter) are the business customer number, never the internal
 * database id.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Validated
public class CustomerController {

    // AC-CUST-01-07: numeric-only criterion fields; the given catalog has no message
    // key for this rule, so a plain description is used.
    private static final String NUMERIC_REGEX = "^[0-9]+$";
    private static final String NUMERIC_ONLY_MESSAGE = "must contain digits only";

    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<Page<CustomerDetailResponse>> search(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) @Pattern(regexp = NUMERIC_REGEX, message = NUMERIC_ONLY_MESSAGE) String nationalityId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @Pattern(regexp = NUMERIC_REGEX, message = NUMERIC_ONLY_MESSAGE) String accountNumber,
            @RequestParam(required = false) @Pattern(regexp = NUMERIC_REGEX, message = NUMERIC_ONLY_MESSAGE) String gsmNumber,
            @RequestParam(required = false) @Pattern(regexp = NUMERIC_REGEX, message = NUMERIC_ONLY_MESSAGE) String orderNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // KR-04 / AC-CUST-01-00: firstName ASC, then lastName ASC (A-Z); customerNumber
        // as a stable tiebreak so server-side pages never shuffle equal names.
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("partyRole.party.individual.firstName"),
                        Sort.Order.asc("partyRole.party.individual.lastName"),
                        Sort.Order.asc("customerNumber")));

        Page<CustomerDetailResponse> result = customerService.search(
                firstName, lastName, nationalityId, customerId, gsmNumber, accountNumber, orderNumber, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{customerNumber}")
    public ResponseEntity<CustomerDetailResponse> getByCustomerNumber(@PathVariable Long customerNumber) {
        return ResponseEntity.ok(customerService.getByCustomerNumber(customerNumber));
    }

    @PostMapping
    public ResponseEntity<CustomerDetailResponse> create(@Valid @RequestBody CustomerCreateRequest request) {
        CustomerDetailResponse response = customerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{customerNumber}")
    public ResponseEntity<CustomerDetailResponse> update(@PathVariable Long customerNumber,
                                                         @Valid @RequestBody CustomerUpdateRequest request) {
        return ResponseEntity.ok(customerService.update(customerNumber, request));
    }

    @DeleteMapping("/{customerNumber}")
    public ResponseEntity<Void> delete(@PathVariable Long customerNumber) {
        customerService.delete(customerNumber);
        return ResponseEntity.noContent().build();
    }
}
