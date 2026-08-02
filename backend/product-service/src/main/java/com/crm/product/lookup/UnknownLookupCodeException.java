package com.crm.product.lookup;

import lombok.Getter;

/**
 * The requested code does not exist in the central catalog, or exists in a different
 * domain than expected. Maps to a 400 validation error with field-level detail (ADR-002).
 */
@Getter
public class UnknownLookupCodeException extends RuntimeException {

    private final String field;

    public UnknownLookupCodeException(String field, String message) {
        super(message);
        this.field = field;
    }
}
