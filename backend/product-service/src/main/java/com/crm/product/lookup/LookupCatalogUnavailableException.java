package com.crm.product.lookup;

/**
 * The central catalog could not be reached (or answered with an unexpected error)
 * and the required value was not cached. Writes must fail closed on this
 * (503, MSG-SERVICE-UNAVAILABLE) — see ADR-002 §7.
 */
public class LookupCatalogUnavailableException extends RuntimeException {

    public LookupCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
