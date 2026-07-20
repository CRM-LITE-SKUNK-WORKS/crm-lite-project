package com.crm.gateway.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Spring Cloud Gateway Server WebMVC (servlet stack - see SecurityConfig for the WebMVC vs
 * WebFlux distinction) does not translate a downstream-unavailable failure into a client-facing
 * status on its own: when Eureka has no instance for a route's service, LoadBalancerFilterFunctions
 * throws an HttpServerErrorException with the real status embedded (e.g. "503 Unable to find
 * instance for customer-service"), but nothing reads that status back out, so it falls through to
 * Spring Boot's default error handling as a generic 500. This advice extracts it.
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    // Covers both HttpClientErrorException and HttpServerErrorException. In practice this is
    // hit by LoadBalancerFilterFunctions with a 503 when no service instance is registered.
    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<GatewayErrorResponse> handleDownstreamStatus(HttpStatusCodeException ex, HttpServletRequest request) {
        log.warn("Downstream error on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        }
        String messageKey = status == HttpStatus.SERVICE_UNAVAILABLE ? "MSG-SERVICE-UNAVAILABLE" : "MSG-DOWNSTREAM-ERROR";
        return ResponseEntity.status(status).body(GatewayErrorResponse.of(status, messageKey,
                "Downstream service is unavailable or returned an error", request.getRequestURI()));
    }

    // Thrown when a service instance was resolved by the load balancer but the TCP connection
    // itself fails (e.g. the instance is registered in Eureka but has crashed).
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<GatewayErrorResponse> handleUnreachable(ResourceAccessException ex, HttpServletRequest request) {
        log.warn("Downstream unreachable on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(GatewayErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE, "MSG-SERVICE-UNAVAILABLE",
                "Downstream service is unavailable", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GatewayErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected gateway error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(GatewayErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR, "MSG-INTERNAL-ERROR", "Unexpected error", request.getRequestURI()));
    }
}
