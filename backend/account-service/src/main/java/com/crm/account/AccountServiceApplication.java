package com.crm.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Billing-account service (FR-ACCT-01..04, KR-11 — ADR-013/014).
 * Owns account_db (acct_tp, cust_acct, cust_acct_prod_invl, acct_number_seq);
 * zero-trust JWT resource server via crm-security-starter (ADR-009).
 */
@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
