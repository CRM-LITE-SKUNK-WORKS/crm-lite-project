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
 * PUT /api/accounts/{accountNumber} (FR-ACCT-03). Mutable fields are EXACTLY
 * accountName and addressId; accountNumber and the account type are immutable
 * (AC-ACCT-03-01). Any other submitted property is captured by {@link #any} and
 * rejected with 400 MSG-ACCT-IMMUTABLE-FIELD — never silently ignored (ADR-013 §3.4).
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountUpdateRequest {

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
