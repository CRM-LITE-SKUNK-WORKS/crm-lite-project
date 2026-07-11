package com.crm.customer.lookup;

import lombok.Getter;

/**
 * The requested code does not exist in the central catalog, or exists in a different
 * domain than expected (e.g. asking for gender "INDV"). Maps to a 400 validation
 * error with field-level detail — see ADR-002.
 */
@Getter
public class UnknownLookupCodeException extends RuntimeException {

    private final String field;

    public UnknownLookupCodeException(String field, String message) {
        super(message);
        this.field = field;
    }
}
