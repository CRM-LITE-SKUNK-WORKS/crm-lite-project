package com.crm.account.account.controller;

import com.crm.account.account.dto.request.AccountCreateRequest;
import com.crm.account.account.dto.request.AccountUpdateRequest;
import com.crm.account.account.dto.response.AccountResponse;
import com.crm.account.account.service.AccountService;
import jakarta.validation.Valid;
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
 * FR-ACCT-01..04 (ADR-013 §3). Exactly these five endpoints — no pagination,
 * filters, exports or bulk operations. {@code customerId} is always the public
 * business customer number; {@code accountNumber} the KR-11 public identifier.
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> list(@RequestParam("customerId") Long customerId) {
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
