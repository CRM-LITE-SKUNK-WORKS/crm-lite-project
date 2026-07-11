package com.crm.customer.mernis;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
