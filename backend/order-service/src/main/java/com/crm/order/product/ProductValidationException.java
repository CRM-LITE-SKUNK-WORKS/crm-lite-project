package com.crm.order.product;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * product-service rejected the basket (ADR-015 §6). Carries the upstream's OWN status
 * and message key — MSG-SALE-NO-INTERNET, MSG-VAL-CHAR-REQUIRED and friends — so
 * order-service can relay them **unchanged** (ADR-016 §7).
 *
 * <p>Collapsing them into a generic 400 would be a real defect, not a simplification:
 * the frontend renders a different catalog text per key, and the whole point of
 * §2.7's validation table is that the user is told which rule they broke.
 */
@Getter
public class ProductValidationException extends RuntimeException {

    private final HttpStatus status;
    private final String messageKey;

    public ProductValidationException(HttpStatus status, String messageKey, String message) {
        super(message);
        this.status = status;
        this.messageKey = messageKey;
    }
}
