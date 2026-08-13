package com.crm.account.common.exception;

import com.crm.account.account.number.AccountNumberCapacityExceededException;
import com.crm.account.customer.CustomerServiceUnavailableException;
import com.crm.account.lookup.LookupCatalogUnavailableException;
import com.crm.account.lookup.UnknownLookupCodeException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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

    // Resilience4j fail-closed rule (docs/runbooks/resilience.md): an open circuit
    // breaker or a full bulkhead is thrown by the AOP proxy BEFORE the Http*Client
    // method body runs, so without this it would fall through to the generic 500
    // handler below — never a fake fallback, always the same 503 contract a genuine
    // connection failure already uses.
    @ExceptionHandler({CallNotPermittedException.class, BulkheadFullException.class})
    public ResponseEntity<ErrorResponse> handleResilienceProtectionEngaged(RuntimeException ex,
                                                                           HttpServletRequest request) {
        log.error("Circuit breaker or bulkhead protection engaged on {} {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body(HttpStatus.SERVICE_UNAVAILABLE,
                MessageKeys.SERVICE_UNAVAILABLE,
                "A required service is unavailable; the operation was not performed", request, null));
    }

    // A routing/protocol-level client error, not a server failure: without this it
    // is a subtype of Exception and falls into the catch-all below, turning a
    // routine unsupported-method call into a 500 with a logged stack trace.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest request) {
        log.debug("Unsupported HTTP method {} on {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body(HttpStatus.METHOD_NOT_ALLOWED,
                MessageKeys.METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint", request, null));
    }

    // A Content-Type Spring cannot map to any registered message converter for this
    // endpoint (e.g. text/plain instead of application/json) is a client contract
    // violation, not a server fault — same reasoning as handleMethodNotSupported above,
    // for content negotiation instead of routing. Without this it falls through to the
    // generic 500 handler below instead of a clean 415.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                     HttpServletRequest request) {
        String message = "Content type '" + ex.getContentType() + "' is not supported; expected one of "
                + ex.getSupportedMediaTypes();
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                MessageKeys.UNSUPPORTED_MEDIA_TYPE, message, request, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // exceptionType (requirement 4, "where available"): set only around this log line.
        org.slf4j.MDC.put(com.crm.observability.starter.MdcKeys.EXCEPTION_TYPE, ex.getClass().getName());
        try {
            log.error("Unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        } finally {
            org.slf4j.MDC.remove(com.crm.observability.starter.MdcKeys.EXCEPTION_TYPE);
        }
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
