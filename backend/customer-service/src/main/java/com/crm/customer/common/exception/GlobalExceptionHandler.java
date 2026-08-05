package com.crm.customer.common.exception;

import com.crm.customer.account.AccountHasActiveProductsException;
import com.crm.customer.account.AccountServiceUnavailableException;
import com.crm.customer.lookup.LookupCatalogUnavailableException;
import com.crm.customer.lookup.UnknownLookupCodeException;
import com.crm.customer.mernis.MernisRejectedException;
import com.crm.customer.mernis.MernisUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.ValueInstantiationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .messageKey(ex.getMessageKey())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .messageKey(MessageKeys.VALIDATION_ERROR)
                .message("Request validation failed")
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    // AC-CUST-01-07: @Pattern on @RequestParam (enabled by @Validated on the
    // controller) fails via a ConstraintViolationException, not
    // MethodArgumentNotValidException, since there is no @RequestBody involved.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            validationErrors.put(field, violation.getMessage());
        }
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .messageKey(MessageKeys.VALIDATION_ERROR)
                .message("Request validation failed")
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    // AC-CUST-01-07 also covers customerId, which is bound as a Long: a
    // non-numeric value never reaches the method body, it fails Spring's own
    // argument conversion first and previously fell through to the generic
    // Exception handler below (500 instead of a clean 400).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getName(), "must contain digits only");
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .messageKey(MessageKeys.VALIDATION_ERROR)
                .message("Request validation failed")
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    // A REQUIRED query parameter that was not sent is a client mistake, not a server
    // fault — same reasoning as the type-mismatch handler above: Spring rejects the
    // request before the method body runs, so without this it fell through to the
    // generic Exception handler and answered 500. The offending parameter name is
    // reported in validationErrors, like every other field-level rejection.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getParameterName(), "is required");
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .messageKey(MessageKeys.VALIDATION_ERROR)
                .message("Request validation failed")
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Root cause (invalid UTF-8 bytes, bad enum value, truncated JSON, ...) is logged in
        // full here so it is never lost - only a safe, generic message goes back to the client.
        log.warn("Malformed request body on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        String message = "Malformed request body";
        Map<String, String> validationErrors = null;

        // An invalid enum value (e.g. gender: "Unknown") surfaces as Jackson failing to
        // instantiate the enum via its @JsonCreator, wrapping the IllegalArgumentException
        // Gender.fromApiValue throws. Report that as a field-level validation error instead
        // of the generic "malformed body" message.
        Throwable cause = ex.getCause();
        if (cause instanceof ValueInstantiationException vie && vie.getCause() instanceof IllegalArgumentException iae) {
            List<JacksonException.Reference> path = vie.getPath();
            String field = path.isEmpty() ? "value" : path.get(path.size() - 1).getPropertyName();
            message = "Request validation failed";
            validationErrors = new LinkedHashMap<>();
            validationErrors.put(field, iae.getMessage());
        }

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .messageKey(MessageKeys.VALIDATION_ERROR)
                .message(message)
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    // Defense in depth: the app-level active-uniqueness check (CustomerBusinessRules) is the
    // primary guard for nationalityId duplicates, but a race between two concurrent requests
    // could still slip past it and hit a DB constraint. Translate that into a clean 409
    // instead of letting it fall through to the generic 500 handler below.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .messageKey(MessageKeys.CUST_DUP_NATID)
                .message("A conflicting record already exists")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // ADR-002: unknown short code or wrong catalog domain — a clean, field-level 400.
    @ExceptionHandler(UnknownLookupCodeException.class)
    public ResponseEntity<ErrorResponse> handleUnknownLookupCode(UnknownLookupCodeException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getField(), ex.getMessage());
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .messageKey(MessageKeys.VALIDATION_ERROR)
                .message("Request validation failed")
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    // ADR-002 fail-closed rule: the shared catalog could not be reached — the write is
    // refused, nothing was persisted.
    @ExceptionHandler(LookupCatalogUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamUnavailable(LookupCatalogUnavailableException ex,
                                                                   HttpServletRequest request) {
        log.error("Upstream dependency unavailable on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex);
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase())
                .messageKey(MessageKeys.SERVICE_UNAVAILABLE)
                .message("A required service is unavailable; the operation was not performed")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    // AC-CUST-05-03: account-service refused to passivate a billing account because it
    // still has active products. Reuses the analyst catalog key MSG-CUST-HAS-PRODUCTS —
    // from the customer's point of view this is the same rejection as the (currently
    // no-op) upfront checkCustomerHasNoActiveProducts guard, just discovered one layer
    // deeper. Nothing was persisted (this runs before local passivation).
    @ExceptionHandler(AccountHasActiveProductsException.class)
    public ResponseEntity<ErrorResponse> handleAccountHasActiveProducts(AccountHasActiveProductsException ex,
                                                                        HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .messageKey(MessageKeys.CUST_HAS_PRODUCTS)
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // AC-CUST-05-04 fail-closed rule: account-service could not be reached while
    // passivating the customer's billing accounts — the whole delete is refused,
    // nothing was persisted (the local passivation runs AFTER this step succeeds).
    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAccountServiceUnavailable(AccountServiceUnavailableException ex,
                                                                         HttpServletRequest request) {
        log.error("Upstream dependency unavailable on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex);
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase())
                .messageKey(MessageKeys.SERVICE_UNAVAILABLE)
                .message("A required service is unavailable; the operation was not performed")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    // KR-10 / AC-CUST-03-06 fail-closed rule: MERNIS/KPS could not be reached — the
    // customer is not created. Uses the analyst catalog key MSG-MERNIS-UNAVAILABLE
    // (v8 Final), not the generic MSG-SERVICE-UNAVAILABLE.
    @ExceptionHandler(MernisUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMernisUnavailable(MernisUnavailableException ex,
                                                                 HttpServletRequest request) {
        log.error("MERNIS unavailable on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex);
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase())
                .messageKey(MessageKeys.MERNIS_UNAVAILABLE)
                .message("The identity verification service is currently unavailable; the operation was not performed")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    // KR-10 / AC-CUST-03-06: MERNIS answered and rejected the identity — the customer
    // is not created (analyst catalog key MSG-CUST-NATID-VERIFICATION-FAILED, v8 Final).
    @ExceptionHandler(MernisRejectedException.class)
    public ResponseEntity<ErrorResponse> handleMernisRejected(MernisRejectedException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .messageKey(MessageKeys.CUST_NATID_VERIFICATION_FAILED)
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .messageKey(MessageKeys.INTERNAL_ERROR)
                .message("Unexpected error")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.internalServerError().body(body);
    }
}
