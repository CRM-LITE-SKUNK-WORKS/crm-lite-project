package com.crm.customer.account;

/**
 * account-service could not be reached (or answered with an unexpected error) — mapped
 * to 503 {@code MSG-SERVICE-UNAVAILABLE}, fail closed, in both flows that raise it.
 *
 * <p>On <b>customer delete</b> (AC-CUST-05-04) the whole delete is refused; the local
 * passivation runs only after the account calls succeed, so nothing was persisted.
 *
 * <p>On the <b>KR-02 search</b> the criterion could not be resolved. Treating the
 * outage as "the number matched nothing" would answer MSG-CUST-NOT-FOUND for a
 * customer that exists, and treating it as "match everything" would leak the whole
 * list. Neither is an acceptable substitute for saying the query could not be run.
 */
public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
