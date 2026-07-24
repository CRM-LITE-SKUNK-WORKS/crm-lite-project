package com.crm.account.common.exception;

import com.crm.account.account.number.AccountNumberCapacityExceededException;
import com.crm.account.customer.CustomerServiceUnavailableException;
import com.crm.account.lookup.LookupCatalogUnavailableException;
import com.crm.account.lookup.UnknownLookupCodeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(body(ex.getStatus(), ex.getMessageKey(), ex.getMessage(), request, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return badRequest(MessageKeys.VALIDATION_ERROR, "Request validation failed", request, validationErrors);
    }

    // AC-ACCT list contract: customerId is a REQUIRED query parameter.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getParameterName(), "is required");
        return badRequest(MessageKeys.VALIDATION_ERROR, "Request validation failed", request, validationErrors);
    }

    // A non-numeric customerId never reaches the handler body: it fails Spring's own
    // argument conversion first (same pattern as customer-service AC-CUST-01-07).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getName(), "must contain digits only");
        return badRequest(MessageKeys.VALIDATION_ERROR, "Request validation failed", request, validationErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            validationErrors.put(field, violation.getMessage());
        }
        return badRequest(MessageKeys.VALIDATION_ERROR, "Request validation failed", request, validationErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Root cause (invalid UTF-8 bytes, truncated JSON, ...) is logged in full here so
        // it is never lost — only a safe, generic message goes back to the client.
        log.warn("Malformed request body on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return badRequest(MessageKeys.VALIDATION_ERROR, "Malformed request body", request, null);
    }

    // ADR-014 §7 defence in depth: the generator never re-issues numbers, but any
    // account_number UNIQUE race (or the K-8 single-223 partial index under a
    // concurrent first-create) surfaces as a clean 409, never a 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT,
                MessageKeys.ACCT_DUP_NUMBER, "A conflicting account record already exists", request, null));
    }

    // ADR-014 §6: the KR-11 sequence for this segment+year is exhausted — a documented
    // domain error, never a raw 500. The transaction has been rolled back.
    @ExceptionHandler(AccountNumberCapacityExceededException.class)
    public ResponseEntity<ErrorResponse> handleCapacityExceeded(AccountNumberCapacityExceededException ex,
                                                                HttpServletRequest request) {
        log.error("Account number capacity exceeded on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT,
                MessageKeys.ACCT_NUMBER_CAPACITY_EXCEEDED, ex.getMessage(), request, null));
    }

    // ADR-002: unknown short code or wrong catalog domain — a clean, field-level 400.
    @ExceptionHandler(UnknownLookupCodeException.class)
    public ResponseEntity<ErrorResponse> handleUnknownLookupCode(UnknownLookupCodeException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getField(), ex.getMessage());
        return badRequest(MessageKeys.VALIDATION_ERROR, "Request validation failed", request, validationErrors);
    }

    // Fail-closed rule (ADR-002 for the catalog, ADR-013 for customer-service): a
    // required downstream service could not be reached — the write is refused,
    // nothing was persisted.
    @ExceptionHandler({LookupCatalogUnavailableException.class, CustomerServiceUnavailableException.class})
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

    private ResponseEntity<ErrorResponse> badRequest(String messageKey, String message, HttpServletRequest request,
                                                     Map<String, String> validationErrors) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, messageKey, message, request, validationErrors));
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
