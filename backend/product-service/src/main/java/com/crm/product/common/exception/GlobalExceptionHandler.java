package com.crm.product.common.exception;

import com.crm.product.account.AccountServiceUnavailableException;
import com.crm.product.customer.CustomerServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Same error contract as the other domain services. Phase A is read-only, so the
 * write-side handlers of the account/customer templates (body validation, data
 * integrity, ...) are deliberately absent — there is no request body to validate.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(body(ex.getStatus(), ex.getMessageKey(), ex.getMessage(), request, null));
    }

    // FR-PROD-01 list contract: accountNumber is a REQUIRED query parameter.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getParameterName(), "is required");
        return badRequest(request, validationErrors);
    }

    // A non-numeric product id never reaches the handler body: it fails Spring's own
    // argument conversion first (same pattern as the other services).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getName(), "must contain digits only");
        return badRequest(request, validationErrors);
    }

    // Fail-closed rule: a required upstream (account-service for the FR-PROD-01
    // composition, customer-service for service-address resolution) could not be
    // reached — no partial or fabricated answer is returned.
    @ExceptionHandler({AccountServiceUnavailableException.class, CustomerServiceUnavailableException.class})
    public ResponseEntity<ErrorResponse> handleUpstreamUnavailable(RuntimeException ex, HttpServletRequest request) {
        log.error("Upstream dependency unavailable on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body(HttpStatus.SERVICE_UNAVAILABLE,
                MessageKeys.SERVICE_UNAVAILABLE,
                "A required service is unavailable; the operation was not performed", request, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(body(HttpStatus.INTERNAL_SERVER_ERROR,
                MessageKeys.INTERNAL_ERROR, "Unexpected error", request, null));
    }

    private ResponseEntity<ErrorResponse> badRequest(HttpServletRequest request, Map<String, String> validationErrors) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                "Request validation failed", request, validationErrors));
    }

    private ErrorResponse body(HttpStatus status, String messageKey, String message, HttpServletRequest request,
                               Map<String, String> validationErrors) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .messageKey(messageKey)
                .message(message)
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
    }
}
