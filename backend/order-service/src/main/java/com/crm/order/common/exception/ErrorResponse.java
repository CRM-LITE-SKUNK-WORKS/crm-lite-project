package com.crm.order.common.exception;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {

    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String messageKey;
    private final String message;
    private final String path;
    private final Map<String, String> validationErrors;
}
