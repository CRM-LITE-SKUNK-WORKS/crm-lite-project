package com.crm.order.account;

/** account-service could not be reached — mapped to 503 MSG-SERVICE-UNAVAILABLE (fail closed). */
public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
