package com.crm.customer.account;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Resilience4j boundary "account-service-read" (docs/runbooks/resilience.md)
 * applies ONLY to {@link #listAccounts} and {@link #fetchAccount} — both plain
 * GETs. {@link #passivateAccount} is deliberately left WITHOUT
 * {@code @CircuitBreaker}/{@code @Bulkhead}/{@code @Retry}: it is a DELETE, and
 * per the class's pre-existing note on {@code accountRestClient}, is already
 * known not to be idempotent — automatically retrying it is exactly the failure
 * mode this whole addendum exists to prevent, so it stays outside the
 * resilience-annotated boundary entirely rather than being given a
 * no-retry-configured instance that would be easy to misconfigure later.
 */
@Component
public class HttpAccountServiceClient implements AccountServiceClient {

    private final RestClient restClient;

    public HttpAccountServiceClient(@Qualifier("accountRestClient") RestClient accountRestClient) {
        this.restClient = accountRestClient;
    }

    @Override
    @CircuitBreaker(name = "account-service-read")
    @Bulkhead(name = "account-service-read")
    @Retry(name = "account-service-read")
    public List<AccountSummary> listAccounts(Long customerNumber) {
        try {
            List<AccountSummary> accounts = restClient.get()
                    .uri("/api/accounts?customerId={customerId}", customerNumber)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return accounts == null ? List.of() : accounts;
        } catch (RestClientException | IllegalStateException e) {
            // Covers connection failures, 5xx responses and "no instances available"
            // from the load balancer: account-service is unavailable, never "the
            // customer has no accounts".
            throw new AccountServiceUnavailableException("account-service is unavailable", e);
        }
    }

    @Override
    public void passivateAccount(String accountNumber) {
        try {
            restClient.delete()
                    .uri("/api/accounts/{accountNumber}", accountNumber)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            // account-service IS reachable and answered correctly: the account still has
            // active products (MSG-ACCT-HAS-PRODUCTS, ADR-013 §3.5). This is a real
            // business conflict, not an availability failure — surface it as one.
            throw new AccountHasActiveProductsException(accountNumber);
        } catch (RestClientException | IllegalStateException e) {
            // Genuine availability failures: connection errors, 5xx, "no instances
            // available". Fails the whole customer delete rather than leaving some
            // accounts silently unpassivated.
            throw new AccountServiceUnavailableException("account-service is unavailable", e);
        }
    }

    @Override
    @CircuitBreaker(name = "account-service-read")
    @Bulkhead(name = "account-service-read")
    @Retry(name = "account-service-read")
    public Optional<AccountSummary> fetchAccount(String accountNumber) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/accounts/{accountNumber}", accountNumber)
                    .retrieve()
                    .body(AccountSummary.class));
        } catch (HttpClientErrorException.NotFound e) {
            // Unknown number, or the K-8 223 Customer Account (indistinguishable by
            // design — ADR-013 §4.5). Either way: nothing to match.
            //
            // Note this catch is what separates fetchAccount from listAccounts above:
            // there, a 404 cannot occur (the list endpoint answers 200 [] for an unknown
            // customer), so every RestClientException there IS an availability failure.
            return Optional.empty();
        } catch (RestClientException | IllegalStateException e) {
            // Connection failures, 5xx responses and "no instances available" from the
            // load balancer all mean the account domain is unavailable — never "the
            // account does not exist".
            throw new AccountServiceUnavailableException("account-service is unavailable", e);
        }
    }
}
