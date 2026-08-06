package com.crm.customer.mernis;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Resilience4j boundary "mernis" (docs/runbooks/resilience.md): a durable
 * synchronous read this platform keeps calling the same way even after the SALE
 * flow moves to messaging. Despite the POST verb, {@link #verify} has no side
 * effect — a pure verification — so retrying it is safe; that is exactly why the
 * requirement names "Mernis/KPS verification" as a target, not an exception to
 * the read-only rule.
 *
 * <p>No fallback method: a timeout, an open circuit or a full bulkhead all
 * propagate as exceptions (timeouts/CB — {@link RestClientException}-family or
 * {@code CallNotPermittedException}), converted below into the SAME
 * {@link MernisUnavailableException} → 503 {@code MSG-MERNIS-UNAVAILABLE}
 * contract a genuine connection failure already used. KR-10 forbids creating the
 * customer either way — a fabricated "verified" result would be exactly the kind
 * of fake-success fallback this addendum's requirements forbid.
 */
@Component
public class HttpMernisClient implements MernisClient {

    private record VerifyRequest(String nationalityId, String firstName, String lastName, LocalDate birthDate) {
    }

    private record VerifyResponse(boolean verified) {
    }

    private final RestClient restClient;

    public HttpMernisClient(@Qualifier("mernisRestClient") RestClient mernisRestClient) {
        this.restClient = mernisRestClient;
    }

    @Override
    @CircuitBreaker(name = "mernis")
    @Bulkhead(name = "mernis")
    @Retry(name = "mernis")
    public boolean verify(String nationalityId, String firstName, String lastName, LocalDate birthDate) {
        try {
            VerifyResponse response = restClient.post()
                    .uri("/api/mernis/verify")
                    .body(new VerifyRequest(nationalityId, firstName, lastName, birthDate))
                    .retrieve()
                    .body(VerifyResponse.class);
            return response != null && response.verified();
        } catch (RestClientException | IllegalStateException e) {
            // Connection failures, 4xx/5xx and "no instances available" all mean the
            // verification could not be performed — KR-10 forbids creating the customer.
            throw new MernisUnavailableException("MERNIS verification service is unavailable", e);
        }
    }
}
