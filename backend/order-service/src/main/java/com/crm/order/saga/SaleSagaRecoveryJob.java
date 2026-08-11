package com.crm.order.saga;

import com.crm.order.order.service.impl.SaleDraftPersistence;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * Stuck-saga recovery and draft hygiene (ADR-018 §8).
 *
 * <p><b>The problem it solves is the one the transport cannot see.</b> A message whose
 * handler THREW is redelivered by the broker and deduplicated by the Inbox — that path is
 * already covered and needs nothing here. What nothing covers is a command that was
 * published successfully and simply never answered: the consumer was down, the reply was
 * dead-lettered, or the answering service lost the message between the two. From this
 * service's side both look identical — a saga sitting in an awaiting state — and no
 * broker feature can distinguish them either.
 *
 * <p><b>Why reissuing is safe.</b> Every command in the flow is idempotent at its
 * receiver: product preparation through the sale-operation coordinator (ADR-015
 * idempotency addendum), involvement per (account, product) (ADR-013 §8), activation by
 * skipping non-PNDG rows, and both compensations by their own definition. A reissue that
 * races a late reply therefore produces work the receiver recognises and skips — which is
 * what makes "the same order is never fulfilled twice" hold even while recovery is
 * running.
 */
@Component
@RequiredArgsConstructor
public class SaleSagaRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(SaleSagaRecoveryJob.class);

    private static final Set<SaleSagaState> RECOVERABLE = EnumSet.of(
            SaleSagaState.AWAITING_ACCOUNT_CHECK,
            SaleSagaState.AWAITING_PRODUCT_PREPARATION,
            SaleSagaState.AWAITING_INVOLVEMENT,
            SaleSagaState.AWAITING_ACTIVATION,
            SaleSagaState.COMPENSATING_INVOLVEMENT,
            SaleSagaState.COMPENSATING_PRODUCTS);

    private final SaleSagaRepository sagaRepository;
    private final SaleSagaOrchestrator orchestrator;
    private final SaleDraftPersistence draftPersistence;
    private final SaleSagaProperties properties;
    private final Clock clock;

    /**
     * One tick. Each saga is reissued in its OWN transaction (the orchestrator's), so a
     * single optimistic-lock loss — the expected outcome when the real reply arrives at
     * the same moment — costs that saga's reissue and nothing else.
     *
     * @return how many sagas were examined
     */
    public int recoverOnce() {
        List<SaleSaga> due = findDue();
        for (SaleSaga saga : due) {
            try {
                orchestrator.reissueDueCommand(saga.getSagaId());
            } catch (OptimisticLockingFailureException raceLost) {
                // The saga moved while we were deciding to nudge it. That is the good
                // case: the reply we were about to work around has arrived.
                log.debug("Saga {} advanced concurrently; skipping reissue", saga.getSagaId());
            } catch (RuntimeException e) {
                // Logged and skipped, never rethrown: one saga must not be able to stop
                // the tick from reaching the rest.
                log.error("Could not reissue the outstanding command for saga {}", saga.getSagaId(), e);
            }
        }
        return due.size();
    }

    /**
     * Not {@code @Transactional}: it is called from {@link #recoverOnce()} on this same
     * bean, and a self-invocation bypasses the proxy entirely — the annotation would
     * document a boundary that does not exist. The repository method opens its own
     * read transaction, which is all a bounded, ordered read needs.
     */
    private List<SaleSaga> findDue() {
        return sagaRepository.findDue(RECOVERABLE, Instant.now(clock),
                Limit.of(properties.getRecoveryBatchSize()));
    }

    /**
     * Expire WAIT drafts the browser never came back for.
     *
     * <p>It only ever CANCELS. There is deliberately no path from here — or from anywhere
     * — that submits or fulfils a draft, which is what makes an abandoned sale incapable
     * of being processed later (AC-SALE-01-16) without depending on the browser to say it
     * was abandoned.
     */
    public int cleanupStaleDraftsOnce() {
        if (!properties.getDraft().isCleanupEnabled()) {
            return 0;
        }
        Instant staleBefore = Instant.now(clock).minus(properties.getDraft().getStaleAfter());
        return draftPersistence.cancelStaleDrafts(staleBefore, properties.getDraft().getCleanupBatchSize());
    }
}
