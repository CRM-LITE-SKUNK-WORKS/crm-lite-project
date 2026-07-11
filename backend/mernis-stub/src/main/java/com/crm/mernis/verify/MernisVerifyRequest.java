package com.crm.mernis.verify;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Mirrors what the real KPS verification asks for: identity number plus the
 * demographics it must match. The stub only applies deterministic rules to
 * nationalityId; the other fields are accepted for contract realism.
 */
public record MernisVerifyRequest(
        @NotBlank String nationalityId,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull LocalDate birthDate) {
}
