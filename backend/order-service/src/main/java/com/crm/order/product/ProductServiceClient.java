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
     * (ADR-016 §5.1 step 2). Replay-safe: {@link ProductCreationCommand#saleOperationId}
     * is a stable identifier dedup'd by a database UNIQUE constraint in product_db —
     * a second call carrying the same id returns the FIRST call's result unchanged
     * instead of creating a second set of products (ADR-015 idempotency addendum).
     *
     * @throws ProductValidationException  the basket was rejected — carries
     *                                     product-service's own status and message
     *                                     key so it can be relayed unchanged
     * @throws ProductServiceUnavailableException nothing is known; fail closed
     */
    ProductCreationResult createProducts(ProductCreationCommand command);

    /** Promotes the PNDG products to ACTV (ADR-016 §5.1 step 5). Idempotent. */
    void confirmProducts(List<Long> productIds);

    /**
     * Compensation: soft-passivates never-committed products belonging to THIS sale
     * operation (ADR-016 §5.1; ADR-015 idempotency addendum). Sale-scoped and
     * idempotent: already-passivated products are a no-op, and a product created by a
     * DIFFERENT operation is refused rather than silently skipped or touched.
     */
    void compensateProducts(String saleOperationId, List<Long> productIds);
}
