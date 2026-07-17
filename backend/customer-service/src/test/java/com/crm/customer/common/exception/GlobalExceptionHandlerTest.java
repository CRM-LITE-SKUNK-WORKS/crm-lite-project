package com.crm.customer.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crm.customer.lookup.LookupCatalogUnavailableException;
import com.crm.customer.lookup.UnknownLookupCodeException;
import com.crm.customer.mernis.MernisRejectedException;
import com.crm.customer.mernis.MernisUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.type.SimpleType;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/customers");
    }

    @Test
    void businessException_mapsToItsOwnStatusAndMessageKey() {
        BusinessException ex = new BusinessException(HttpStatus.CONFLICT, MessageKeys.CUST_DUP_NATID, "duplicate nationalityId");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessageKey()).isEqualTo(MessageKeys.CUST_DUP_NATID);
        assertThat(response.getBody().getMessage()).isEqualTo("duplicate nationalityId");
        assertThat(response.getBody().getPath()).isEqualTo("/api/customers");
    }

    @Test
    void unreadableBody_genericCause_returnsSafeMessageWithNoValidationErrors() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("boom", (HttpInputMessage) null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed request body");
        assertThat(response.getBody().getValidationErrors()).isNull();
    }

    @Test
    void unreadableBody_invalidEnumCause_returnsFieldLevelValidationError() {
        JavaType genderType = SimpleType.constructUnsafe(com.crm.customer.customer.dto.Gender.class);
        IllegalArgumentException enumCause = new IllegalArgumentException("Invalid gender value: Unknown");
        ValueInstantiationException vie = ValueInstantiationException.from(null, "boom", genderType, enumCause);
        vie.prependPath(new JacksonException.Reference(new Object(), "gender"));

        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("boom", vie, (HttpInputMessage) null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Request validation failed");
        assertThat(response.getBody().getValidationErrors()).containsEntry("gender", "Invalid gender value: Unknown");
    }

    @Test
    void dataIntegrityViolation_mapsToConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("constraint violated");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessageKey()).isEqualTo(MessageKeys.CUST_DUP_NATID);
    }

    @Test
    void unexpectedException_mapsToInternalServerErrorWithoutLeakingDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("some internal detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("Unexpected error");
        assertThat(response.getBody().getMessage()).doesNotContain("some internal detail");
    }

    @Test
    void unknownLookupCode_mapsToFieldLevel400() {
        UnknownLookupCodeException ex = new UnknownLookupCodeException("gender", "Unknown type code: XX");

        ResponseEntity<ErrorResponse> response = handler.handleUnknownLookupCode(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessageKey()).isEqualTo(MessageKeys.VALIDATION_ERROR);
        assertThat(response.getBody().getValidationErrors()).containsEntry("gender", "Unknown type code: XX");
    }

    @Test
    void lookupCatalogUnavailable_mapsTo503FailClosed() {
        LookupCatalogUnavailableException ex =
                new LookupCatalogUnavailableException("down", new RuntimeException("connect refused"));

        ResponseEntity<ErrorResponse> response = handler.handleUpstreamUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessageKey()).isEqualTo(MessageKeys.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage()).doesNotContain("connect refused");
    }

    @Test
    void mernisUnavailable_mapsTo503WithMernisUnavailableKey() {
        MernisUnavailableException ex = new MernisUnavailableException("down", new RuntimeException());

        ResponseEntity<ErrorResponse> response = handler.handleMernisUnavailable(ex, request);

        // AC-CUST-03-06 (v8 Final): KPS/MERNIS outages use the analyst catalog key.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessageKey()).isEqualTo(MessageKeys.MERNIS_UNAVAILABLE);
        assertThat(response.getBody().getMessageKey()).isEqualTo("MSG-MERNIS-UNAVAILABLE");
    }

    @Test
    void mernisRejected_mapsTo400WithVerificationFailedKey() {
        MernisRejectedException ex = new MernisRejectedException("Nationality ID could not be verified by MERNIS");

        ResponseEntity<ErrorResponse> response = handler.handleMernisRejected(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessageKey()).isEqualTo(MessageKeys.CUST_NATID_VERIFICATION_FAILED);
        assertThat(response.getBody().getMessageKey()).isEqualTo("MSG-CUST-NATID-VERIFICATION-FAILED");
    }
}
