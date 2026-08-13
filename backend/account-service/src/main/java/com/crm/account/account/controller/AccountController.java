package com.crm.account.account.controller;

import com.crm.account.account.dto.request.AccountCreateRequest;
import com.crm.account.account.dto.request.AccountUpdateRequest;
import com.crm.account.account.dto.request.ProductInvolvementRequest;
import com.crm.account.account.dto.response.AccountResponse;
import com.crm.account.account.dto.response.ProductInvolvementResponse;
import com.crm.account.account.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * FR-ACCT-01..04 (ADR-013 §3): exactly the five public endpoints — no pagination,
 * filters or exports — plus the two service-to-service involvement endpoints that
 * ADR-013 §7/§8 add to the boundary (the read side for product-service's FR-PROD-01
 * composition, the write side for order-service's FR-SALE-01 commit point).
 * {@code customerId} is always the public business customer number;
 * {@code accountNumber} the KR-11 public identifier.
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> list(
            @RequestParam("customerId") @Positive Long customerId) {
        return ResponseEntity.ok(accountService.list(customerId));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> detail(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getByAccountNumber(accountNumber));
    }

    // ADR-013 §5 read side: service-to-service support for product-service's
    // FR-PROD-01 composition (the involvement projection's single public reading
    // point — other services never touch account_db). Not routed through the
    // gateway; reached directly via Eureka with the user's token (ADR-010).
    @GetMapping("/{accountNumber}/product-ids")
    public ResponseEntity<List<Long>> productIds(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.listProductIds(accountNumber));
    }

    // ADR-013 §8 write side: the involvement command order-service invokes at the
    // commit point of a sale (ADR-016 §5.1 step 4). Bulk — one call per sale, never
    // N calls. Like the read side above it is reached directly via Eureka with the
    // user's token (ADR-010), not through the gateway.
    @PostMapping("/{accountNumber}/product-involvements")
    public ResponseEntity<ProductInvolvementResponse> addProductInvolvements(
            @PathVariable String accountNumber,
            @Valid @RequestBody ProductInvolvementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.addProductInvolvements(accountNumber, request));
    }

    @PutMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> update(@PathVariable String accountNumber,
                                                  @Valid @RequestBody AccountUpdateRequest request) {
        return ResponseEntity.ok(accountService.update(accountNumber, request));
    }

    @DeleteMapping("/{accountNumber}")
    public ResponseEntity<Void> delete(@PathVariable String accountNumber) {
        accountService.delete(accountNumber);
        // 204: the frontend shows MSG-ACCT-DELETED after this response;
        // MSG-ACCT-DELETE-CONFIRM is frontend-only (ADR-013 §3.5).
        return ResponseEntity.noContent().build();
    }
}
