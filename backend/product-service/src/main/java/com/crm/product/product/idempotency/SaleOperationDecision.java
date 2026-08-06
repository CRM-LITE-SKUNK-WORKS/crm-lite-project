package com.crm.product.product.idempotency;

/** The two outcomes {@link SaleOperationCoordinator#resolve} hands back to
 *  {@code ProductServiceImpl.create} (ADR-015 idempotency addendum). A third outcome —
 *  a concurrent, still-in-flight reservation for the same id — is reported as
 *  {@link SaleOperationInProgressException} instead of a decision variant, because
 *  unlike order-service's filter-level {@code IdempotencyDecision}, this call site is a
 *  normal {@code @Transactional} service method reachable by
 *  {@code GlobalExceptionHandler}, so an exception is the natural way to abort it. */
public sealed interface SaleOperationDecision {

    /** No prior attempt for this operation id: proceed with the real creation. */
    record Proceed(long reservationId) implements SaleOperationDecision {
    }

    /** This operation id already completed successfully: replay its stored response
     *  verbatim; nothing new is written. */
    record Replay(String responseSnapshot) implements SaleOperationDecision {
    }
}
