package com.crm.product.customer;

/** customer-service could not be reached — mapped to 503 MSG-SERVICE-UNAVAILABLE (fail closed). */
public class CustomerServiceUnavailableException extends RuntimeException {

    public CustomerServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
