package com.crm.customer.order;

/**
 * order-service could not be reached (or answered with an unexpected error) while
 * resolving an {@code orderNumber} search criterion. Fails closed with 503
 * {@code MSG-SERVICE-UNAVAILABLE} — see
 * {@link com.crm.customer.account.AccountServiceUnavailableException} for the reasoning.
 */
public class OrderServiceUnavailableException extends RuntimeException {

    public OrderServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
