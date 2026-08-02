package com.crm.order.product;

import java.util.List;

/**
 * product-service boundary (ADR-015 §5, ADR-016 §5). Reached directly via Eureka
 * with the user's token propagated (ADR-010).
 *
 * <p>order-service forwards the basket VERBATIM and writes no validation of its own
 * (ADR-015 §6): basket composition and characteristic rules are PROD_OFR/PROD_SPEC
 * questions only product-service can answer, and duplicating them here would create
 * a second, drifting copy of AC-SALE-01-08.
 */
public interface ProductServiceClient {

    /**
     * Creates the whole installation as PNDG and returns the created products
     * (ADR-016 §5.1 step 2).
     *
     * @throws ProductValidationException  the basket was rejected — carries
     *                                     product-service's own status and message
     *                                     key so it can be relayed unchanged
     * @throws ProductServiceUnavailableException nothing is known; fail closed
     */
    ProductCreationResult createProducts(ProductCreationCommand command);

    /** Promotes the PNDG products to ACTV (ADR-016 §5.1 step 5). Idempotent. */
    void confirmProducts(List<Long> productIds);

    /** Compensation: soft-passivates never-committed products (ADR-016 §5.1). */
    void cancelProducts(List<Long> productIds);
}
