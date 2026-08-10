package com.crm.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Read-only product/catalog service (FR-PROD-01..02, Phase A slice).
 * Owns product_db (prod_spec, prod_ofr, cmpg, cmpg_prod_ofr, prod, prod_spec_char,
 * prod_spec_char_use, prod_char_val, prod_catal, prod_catal_prod_ofr); zero-trust
 * JWT resource server via crm-security-starter (ADR-009). The product ↔ account
 * link is owned by account-service (ADR-013 §5) and composed over its API.
 *
 * <p>The scan annotations pick up crm-messaging-starter's outbox/inbox entities and
 * repositories alongside this service's own (ADR-017 §8) — see
 * {@code OrderServiceApplication} for why they are declared here and not in the starter.
 */
@SpringBootApplication
@EntityScan({"com.crm.product", "com.crm.messaging"})
@EnableJpaRepositories({"com.crm.product", "com.crm.messaging"})
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
