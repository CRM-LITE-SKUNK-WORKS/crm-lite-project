package com.crm.lookup.common.exception;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String error, String messageKey, String message, String path) {
}
