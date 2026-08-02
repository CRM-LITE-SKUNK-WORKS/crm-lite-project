package com.crm.order.common.exception;

import com.crm.order.account.AccountNotActiveException;
import com.crm.order.account.AccountServiceUnavailableException;
import com.crm.order.lookup.LookupCatalogUnavailableException;
import com.crm.order.lookup.UnknownLookupCodeException;
import com.crm.order.order.number.OrderNumberCapacityExceededException;
import com.crm.order.product.ProductServiceUnavailableException;
import com.crm.order.product.ProductValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** The established error contract, shared with the other domain services. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(body(ex.getStatus(), ex.getMessageKey(), ex.getMessage(), request, null));
    }

    /**
     * product-service rejected the basket (ADR-015 §6). Its status and message key are
     * relayed <b>unchanged</b> — MSG-SALE-NO-INTERNET, MSG-VAL-CHAR-REQUIRED and the
     * rest (ADR-016 §7).
     *
     * <p>Collapsing them into a generic 400 would be a real defect, not a
     * simplification: §2.7's validation table exists so the user is told which rule
     * they broke, and the frontend renders a different catalog text per key.
     */
    @ExceptionHandler(ProductValidationException.class)
    public ResponseEntity<ErrorResponse> handleProductValidation(ProductValidationException ex,
                                                                 HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(body(ex.getStatus(), ex.getMessageKey(), ex.getMessage(), request, null));
    }

    /**
     * account-service refused the involvement write because the account is Passive
     * (AC-SALE-02-01). A 409 with the account's own key, NOT a 503: the account is
     * perfectly reachable — "the account is passive" is actionable, "a service is
     * down" is not.
     */
    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException ex,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body(HttpStatus.CONFLICT, MessageKeys.ACCT_NOT_ACTIVE, ex.getMessage(), request, null));
    }

    // Bean validation on the request body: shape only. The AC-SALE rules with their
    // own analyst keys are product-service's and are relayed above — they must never
    // collapse into this generic 400.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> validationErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return badRequest(request, validationErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put("body", "is missing or malformed");
        return badRequest(request, validationErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getName(), "has the wrong type");
        return badRequest(request, validationErrors);
    }

    // ADR-002 §5: an unknown short code or one in the wrong domain is a 400 with
    // field-level detail — never a silent acceptance.
    @ExceptionHandler(UnknownLookupCodeException.class)
    public ResponseEntity<ErrorResponse> handleUnknownLookupCode(UnknownLookupCodeException ex,
                                                                 HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getField(), ex.getMessage());
        return badRequest(request, validationErrors);
    }

    /**
     * KR-12 sequence exhausted for this segment+year (ADR-016 §4.4) — a documented
     * domain error, mapped to 409, never a raw 500. The transaction rolled back,
     * taking the sequence increment with it.
     */
    @ExceptionHandler(OrderNumberCapacityExceededException.class)
    public ResponseEntity<ErrorResponse> handleCapacityExceeded(OrderNumberCapacityExceededException ex,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT,
                MessageKeys.ORDER_NUMBER_CAPACITY_EXCEEDED, ex.getMessage(), request, null));
    }

    /**
     * Defence in depth (the ADR-014 §7 pattern): the generator never re-issues a
     * number, but any {@code order_number} UNIQUE violation surfaces as a clean 409
     * rather than a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.error("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT,
                MessageKeys.ORDER_DUP_NUMBER, "Order number conflict", request, null));
    }

    /**
     * Fail-closed rule (ADR-016 §5.5): a required downstream could not be reached. By
     * the time this is thrown the orchestration has already compensated — the order
     * is CANCELLED and nothing the customer can see was created.
     */
    @ExceptionHandler({LookupCatalogUnavailableException.class, AccountServiceUnavailableException.class,
            ProductServiceUnavailableException.class})
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

    private ResponseEntity<ErrorResponse> badRequest(HttpServletRequest request,
                                                     Map<String, String> validationErrors) {
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
