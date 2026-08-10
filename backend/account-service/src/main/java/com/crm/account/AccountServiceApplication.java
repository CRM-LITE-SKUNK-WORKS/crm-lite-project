package com.crm.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Billing-account service (FR-ACCT-01..04, KR-11 — ADR-013/014).
 * Owns account_db (acct_tp, cust_acct, cust_acct_prod_invl, acct_number_seq);
 * zero-trust JWT resource server via crm-security-starter (ADR-009).
 *
 * <p>The scan annotations pick up crm-messaging-starter's outbox/inbox entities and
 * repositories alongside this service's own (ADR-017 §8) — see
 * {@code OrderServiceApplication} for why they are declared here and not in the starter.
 */
@SpringBootApplication
@EntityScan({"com.crm.account", "com.crm.messaging"})
@EnableJpaRepositories({"com.crm.account", "com.crm.messaging"})
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
