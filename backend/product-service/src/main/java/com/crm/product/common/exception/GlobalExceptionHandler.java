package com.crm.product.common.exception;

import com.crm.product.account.AccountServiceUnavailableException;
import com.crm.product.customer.CustomerServiceUnavailableException;
import com.crm.product.lookup.LookupCatalogUnavailableException;
import com.crm.product.lookup.UnknownLookupCodeException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Same error contract as the other domain services. The write-side handlers (body
 * validation, catalog outages) arrived with the FR-SALE slice — the Phase A note
 * that "there is no request body to validate" no longer holds.
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

    // Bean validation on the write-slice request bodies (ADR-015 §5): shape only —
    // the business rules with their own analyst keys (MSG-SALE-*, MSG-VAL-CHAR-*)
    // are thrown as BusinessExceptions above and never collapse into this generic
    // 400, or the frontend could not render the specific text the catalog defines.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> validationErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return badRequest(request, validationErrors);
    }

    // A malformed/unparseable JSON body is a client defect, not a server error.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put("body", "is missing or malformed");
        return badRequest(request, validationErrors);
    }

    // ADR-002 §5: an unknown short code or a code in the wrong domain is a 400 with
    // field-level detail — never a silent acceptance.
    @ExceptionHandler(UnknownLookupCodeException.class)
    public ResponseEntity<ErrorResponse> handleUnknownLookupCode(UnknownLookupCodeException ex,
                                                                 HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getField(), ex.getMessage());
        return badRequest(request, validationErrors);
    }

    // ADR-002 §7 fail-closed: the shared catalog is unreachable and the value was not
    // cached, so no status can be resolved — nothing is persisted. Reads are
    // unaffected (they use the local LookupContract constants).
    @ExceptionHandler(LookupCatalogUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCatalogUnavailable(LookupCatalogUnavailableException ex,
                                                                   HttpServletRequest request) {
        log.error("Shared catalog unavailable on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body(HttpStatus.SERVICE_UNAVAILABLE,
                MessageKeys.SERVICE_UNAVAILABLE,
                "A required service is unavailable; the operation was not performed", request, null));
    }

    // Resilience4j fail-closed rule (docs/runbooks/resilience.md): an open circuit
    // breaker or a full bulkhead is thrown by the AOP proxy BEFORE the Http*Client
    // method body runs — without this it would fall through to the generic 500
    // handler below rather than the same 503 contract a genuine connection failure uses.
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
