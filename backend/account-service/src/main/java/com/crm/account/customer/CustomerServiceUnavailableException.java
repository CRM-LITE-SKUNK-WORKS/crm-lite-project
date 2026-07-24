package com.crm.account.customer;

/**
 * customer-service could not be reached (or answered with an unexpected error).
 * Writes must fail closed on this (503, MSG-SERVICE-UNAVAILABLE) — ADR-013.
 */
public class CustomerServiceUnavailableException extends RuntimeException {

    public CustomerServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
