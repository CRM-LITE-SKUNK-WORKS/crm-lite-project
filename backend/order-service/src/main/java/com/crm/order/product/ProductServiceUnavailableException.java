package com.crm.order.product;

/** product-service could not be reached — mapped to 503 MSG-SERVICE-UNAVAILABLE (fail closed). */
public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
