package com.crm.customer.mernis;

/**
 * KR-10 fail-closed rule: when the KPS/MERNIS verification service cannot be reached,
 * the customer is NOT created and the caller receives a system error (503).
 */
public class MernisUnavailableException extends RuntimeException {

    public MernisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
