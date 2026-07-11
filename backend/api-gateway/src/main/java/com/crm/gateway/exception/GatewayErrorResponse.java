package com.crm.gateway.exception;

import java.time.Instant;
import org.springframework.http.HttpStatus;

public record GatewayErrorResponse(Instant timestamp, int status, String error, String messageKey, String message, String path) {

    public static GatewayErrorResponse of(HttpStatus status, String messageKey, String message, String path) {
        return new GatewayErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), messageKey, message, path);
    }
}
