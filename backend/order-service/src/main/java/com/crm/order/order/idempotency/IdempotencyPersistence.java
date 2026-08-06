package com.crm.order.order.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every write here runs in its OWN, independent transaction ({@code REQUIRES_NEW}) —
 * deliberately, not the default {@code REQUIRED}. {@link #reserve} is called from
 * {@link IdempotencyKeyFilter}, which runs BEFORE the whole order orchestration and
 * therefore normally has no ambient transaction open — but {@code REQUIRES_NEW} makes
 * that true by construction rather than by accident, and guarantees the reservation
 * INSERT commits (or fails on the UNIQUE constraint) immediately and independently of
 * whatever the request goes on to do. That immediacy is what makes the constraint a
 * real concurrency guard: a second, truly concurrent request must see the first
 * request's row the instant it is committed, not only after the whole HTTP call
 * finishes.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyPersistence {

    /** Replay eligibility window. Purely a retention horizon (§ table requirement) —
     *  it does not gate IN_PROGRESS reclaiming, which this design deliberately does
     *  not attempt (a stuck row is a bounded, logged residue, the same class of
     *  trade-off ADR-015 §8.4 accepts for a stuck PNDG product). */
    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    /**
     * The concurrency guard itself: an INSERT whose UNIQUE constraint on
     * {@code idempotency_key} either succeeds (this caller owns the key now) or throws
     * (some other row — possibly written microseconds ago by a concurrent request —
     * already owns it). The caller decides what a conflict means; this method only
     * ever returns on success.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long reserve(String idempotencyKey, String requestHash) {
        Instant now = Instant.now(clock);
        IdempotencyKeyRecord record = new IdempotencyKeyRecord();
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyStatus.IN_PROGRESS);
        record.setCreatedDate(now);
        record.setExpiresAt(now.plus(RETENTION));
        // saveAndFlush (via repository.save + the transaction's own flush-on-commit)
        // is enough here: REQUIRES_NEW means this method's commit — and therefore the
        // constraint check — happens the moment the method returns, not when some
        // outer transaction eventually commits.
        return repository.save(record).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyKeyRecord> findByKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    /**
     * Records the terminal response for a reservation this caller made itself
     * (success or a handled failure alike — ADR-016's own paths already fail closed,
     * so replaying a recorded failure verbatim is cheaper and no less honest than
     * re-running the whole orchestration for the same answer).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(long reservationId, int responseStatus, String responseBody, String orderNumber) {
        repository.findById(reservationId).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseStatus(responseStatus);
            record.setResponseBody(responseBody);
            record.setOrderNumber(orderNumber);
            record.setUpdatedDate(Instant.now(clock));
        });
    }
}
