package com.crm.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * order-service (ADR-016): FR-SALE-01/02 §2.7, the Offer Selection → Product
 * Configuration → Submit Order flow's backend. Owns order_db (bsn_inter, cust_ord,
 * cust_ord_item, order_number_seq) and orchestrates the sale across product-service
 * and account-service without a distributed transaction (ADR-016 §5).
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
