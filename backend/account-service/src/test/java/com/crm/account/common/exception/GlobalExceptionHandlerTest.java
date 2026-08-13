package com.crm.account.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/accounts");
    }

    // EAT-8377: text/plain instead of application/json previously fell through to the
    // generic Exception.class handler (500 MSG-INTERNAL-ERROR) because Spring's own
    // 415 resolution never gets a chance to run once a catch-all @ExceptionHandler is
    // registered for Exception.class.
    @Test
    void unsupportedMediaType_mapsTo415WithClearMessage() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponse> response = handler.handleUnsupportedMediaType(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().getMessageKey()).isEqualTo(MessageKeys.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().getMessage()).contains("text/plain").contains("application/json");
    }
}
