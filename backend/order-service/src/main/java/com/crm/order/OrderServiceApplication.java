package com.crm.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * order-service (ADR-016): FR-SALE-01/02 §2.7, the Offer Selection → Product
 * Configuration → Submit Order flow's backend. Owns order_db (bsn_inter, cust_ord,
 * cust_ord_item, order_number_seq) and orchestrates the sale across product-service
 * and account-service without a distributed transaction (ADR-016 §5).
 *
 * <p>The two scan annotations exist because {@code com.crm.messaging} (crm-messaging-starter,
 * ADR-017) contributes the {@code outbox_message}/{@code inbox_message} entities and their
 * repositories, and Boot's default scan only covers this application's own package. They
 * are declared HERE rather than in the starter's auto-configuration on purpose: registering
 * entity packages from an auto-configuration REPLACES Boot's auto-configuration package
 * scan instead of adding to it, which would silently stop {@code com.crm.order}'s own
 * entities from being found at all.
 */
@SpringBootApplication
@EntityScan({"com.crm.order", "com.crm.messaging"})
@EnableJpaRepositories({"com.crm.order", "com.crm.messaging"})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
