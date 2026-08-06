package com.crm.product.product.idempotency;

import com.crm.product.common.exception.BusinessException;
import com.crm.product.common.exception.MessageKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/products} replay-safety (ADR-015 idempotency addendum, 2026-08-06).
 * Deliberately NOT {@code @Transactional} itself — like order-service's
 * {@code IdempotencyService}, it orchestrates independent short transactions on
 * {@link SaleOperationPersistence} rather than holding one open across the
 * reserve-then-maybe-requery sequence.
 */
@Service
@RequiredArgsConstructor
public class SaleOperationCoordinator {

    private final SaleOperationPersistence persistence;

    /**
     * The concurrency guard is {@link SaleOperationPersistence#reserve}'s
     * UNIQUE-constraint INSERT, not this method's control flow.
     *
     * @throws BusinessException (409 {@code MSG-SALE-OPERATION-IN-PROGRESS}) if a
     *         concurrent, still-in-flight request already reserved this operation id
     */
    public SaleOperationDecision resolve(String saleOperationId) {
        try {
            long reservationId = persistence.reserve(saleOperationId);
            return new SaleOperationDecision.Proceed(reservationId);
        } catch (DataIntegrityViolationException raceLost) {
            SaleOperation existing = persistence.findByOperationId(saleOperationId).orElseThrow(() -> raceLost);
            if (existing.getResponseSnapshot() == null) {
                // Bounded, logged residue if the owning request crashes before
                // completing (never gets to release() either) — the same class of
                // trade-off ADR-015 §8.4 already accepts for a stuck PNDG row, rather
                // than a speculative reclaim-after-timeout policy nothing requires.
                throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.SALE_OPERATION_IN_PROGRESS,
                        "A product-creation request for operation " + saleOperationId + " is already in progress");
            }
            return new SaleOperationDecision.Replay(existing.getResponseSnapshot());
        }
    }

    /** Records the terminal, successful response for a reservation this caller made. */
    public void complete(long reservationId, String responseSnapshot, Long mainProductId) {
        persistence.complete(reservationId, responseSnapshot, mainProductId);
    }

    /** Frees a reservation whose creation attempt failed — see {@link SaleOperationPersistence#release}. */
    public void release(long reservationId) {
        persistence.release(reservationId);
    }
}
