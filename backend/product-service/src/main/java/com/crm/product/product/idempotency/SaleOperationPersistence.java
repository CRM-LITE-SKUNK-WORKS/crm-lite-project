package com.crm.product.product.idempotency;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every write here runs in its OWN, independent transaction ({@code REQUIRES_NEW}),
 * never the caller's — {@link com.crm.product.product.service.impl.ProductServiceImpl#create}
 * already holds a transaction across the whole basket write, and {@code REQUIRES_NEW}
 * is what lets {@link #reserve}'s UNIQUE-constraint INSERT commit (or fail) immediately,
 * independent of whether that outer transaction ever commits. Without it, a caught
 * constraint violation would leave the outer transaction unusable (Postgres aborts a
 * transaction on any statement error until it is rolled back) — the same reasoning
 * {@code order-service}'s {@code IdempotencyPersistence} records for its own reserve step.
 */
@Service
@RequiredArgsConstructor
public class SaleOperationPersistence {

    private final SaleOperationRepository repository;
    private final Clock clock;

    /** The concurrency guard: an INSERT whose UNIQUE constraint on
     *  {@code sale_operation_id} either succeeds or throws — never a prior SELECT. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long reserve(String saleOperationId) {
        SaleOperation operation = new SaleOperation();
        operation.setSaleOperationId(saleOperationId);
        operation.setCreatedDate(Instant.now(clock));
        return repository.save(operation).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<SaleOperation> findByOperationId(String saleOperationId) {
        return repository.findBySaleOperationId(saleOperationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(long reservationId, String responseSnapshot, Long mainProductId) {
        repository.findById(reservationId).ifPresent(operation -> {
            operation.setResponseSnapshot(responseSnapshot);
            operation.setMainProductId(mainProductId);
            operation.setUpdatedDate(Instant.now(clock));
        });
    }

    /**
     * Releases a reservation whose creation attempt failed (basket rejected, address
     * invalid, catalog unreachable — the outer {@code create()} transaction rolled
     * back, so NOTHING was persisted). Deleting rather than leaving it stuck lets a
     * genuine retry with the same {@code saleOperationId} succeed cleanly: unlike a
     * SUCCESSFUL creation, a failed one leaves no products to protect from
     * duplication, so there is nothing this ledger needs to remember.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(long reservationId) {
        repository.deleteById(reservationId);
    }
}
