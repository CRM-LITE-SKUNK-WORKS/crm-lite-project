package com.crm.account.account.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /api/accounts (FR-ACCT-02). The account type is NOT client-selectable
 * (always 224 — ADR-013 §3.2); {@code customerId} is the PUBLIC business customer
 * number. Any property outside the three declared fields (e.g. accountType,
 * accountNumber, accountStatus) is captured by {@link #any} and rejected with
 * 400 MSG-ACCT-IMMUTABLE-FIELD — never silently ignored.
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountCreateRequest {

    @NotNull
    @Positive
    private Long customerId;

    @NotBlank
    @Size(max = 100)
    private String accountName;

    @NotNull
    @Positive
    private Long addressId;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void any(String name, Object value) {
        unknownFields.put(name, value);
    }
}
