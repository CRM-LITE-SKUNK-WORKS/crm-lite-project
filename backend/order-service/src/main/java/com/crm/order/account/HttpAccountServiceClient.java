package com.crm.order.account;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpAccountServiceClient implements AccountServiceClient {

    private final RestClient restClient;

    public HttpAccountServiceClient(@Qualifier("accountRestClient") RestClient accountRestClient) {
        this.restClient = accountRestClient;
    }

    @Override
    public Optional<AccountSummary> fetchAccount(String accountNumber) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/accounts/{accountNumber}", accountNumber)
                    .retrieve()
                    .body(AccountSummary.class));
        } catch (HttpClientErrorException.NotFound e) {
            // Unknown number, or the K-8 223 Customer Account (indistinguishable by
            // design — ADR-013 §4.5).
            return Optional.empty();
        } catch (RestClientException | IllegalStateException e) {
            throw new AccountServiceUnavailableException("account-service is unavailable", e);
        }
    }

    @Override
    public void linkProducts(String accountNumber, List<Long> productIds) {
        try {
            restClient.post()
                    .uri("/api/accounts/{accountNumber}/product-involvements", accountNumber)
                    .body(Map.of("productIds", productIds))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            // The only 409 this endpoint produces is MSG-ACCT-NOT-ACTIVE (ADR-013 §8.2).
            // Surfacing it as the account's own conflict rather than a 503 matters:
            // "the account is passive" is actionable, "a service is down" is not.
            throw new AccountNotActiveException(accountNumber);
        } catch (RestClientException | IllegalStateException e) {
            // Everything else — connection failures, 5xx, "no instances available",
            // and a 404 that can only mean the account vanished between the
            // precondition read and here — is an availability failure. The sale is
            // compensated and fails closed (ADR-016 §5.1 step 4).
            throw new AccountServiceUnavailableException("account-service is unavailable", e);
        }
    }
}
