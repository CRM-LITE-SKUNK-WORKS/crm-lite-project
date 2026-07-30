package com.crm.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Read-only product/catalog service (FR-PROD-01..02, Phase A slice).
 * Owns product_db (prod_spec, prod_ofr, cmpg, cmpg_prod_ofr, prod, prod_spec_char,
 * prod_spec_char_use, prod_char_val, prod_catal, prod_catal_prod_ofr); zero-trust
 * JWT resource server via crm-security-starter (ADR-009). The product ↔ account
 * link is owned by account-service (ADR-013 §5) and composed over its API.
 */
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
