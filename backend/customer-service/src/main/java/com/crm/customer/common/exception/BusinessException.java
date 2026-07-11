package com.crm.customer.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String messageKey;

    public BusinessException(HttpStatus status, String messageKey, String message) {
        super(message);
        this.status = status;
        this.messageKey = messageKey;
    }
}
