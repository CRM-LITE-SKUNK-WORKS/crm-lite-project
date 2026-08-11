package com.crm.order.saga;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs saga recovery and stale-draft cleanup on their own single-threaded scheduler.
 *
 * <p>A {@link SmartLifecycle} with a private executor rather than {@code @Scheduled} +
 * {@code @EnableScheduling}, for the same reason {@code OutboxScheduler} gives: switching
 * Spring scheduling on changes behaviour for everything else in the context, and this
 * needs one thread for two jobs, not a container-wide capability.
 *
 * <p>One thread, and the two tasks share it deliberately. Neither is latency-sensitive —
 * one nudges sales that have already been waiting half a minute, the other cancels drafts
 * that have been idle for hours — and a pool would only make it possible for cleanup to
 * run while recovery was still deciding what to reissue.
 *
 * <p>Every task body is wrapped: an escaping exception silently cancels a
 * {@code scheduleWithFixedDelay} task, which would leave a process that looks healthy and
 * recovers nothing — the exact failure this job exists to prevent, reintroduced one level
 * up.
 */
public class SaleSagaScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SaleSagaScheduler.class);

    private final SaleSagaRecoveryJob recoveryJob;
    private final SaleSagaProperties properties;

    private ScheduledExecutorService executor;
    private volatile boolean running;

    public SaleSagaScheduler(SaleSagaRecoveryJob recoveryJob, SaleSagaProperties properties) {
        this.recoveryJob = recoveryJob;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        if (running || !properties.isRecoveryEnabled()) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "crm-sale-saga");
            thread.setDaemon(true);
            return thread;
        });
        long recoveryMillis = Math.max(1000, properties.getRecoveryInterval().toMillis());
        executor.scheduleWithFixedDelay(guarded("recovery", recoveryJob::recoverOnce),
                recoveryMillis, recoveryMillis, TimeUnit.MILLISECONDS);
        if (properties.getDraft().isCleanupEnabled()) {
            // Checked at a fraction of the staleness window: often enough that an expired
            // draft is cancelled promptly, rarely enough that the query is invisible.
            long cleanupMillis = Math.max(60_000, properties.getDraft().getStaleAfter().toMillis() / 4);
            executor.scheduleWithFixedDelay(guarded("draft-cleanup", recoveryJob::cleanupStaleDraftsOnce),
                    cleanupMillis, cleanupMillis, TimeUnit.MILLISECONDS);
        }
        running = true;
        log.info("SALE saga recovery started (every {} ms, reply timeout {}, max {} retries)",
                recoveryMillis, properties.getReplyTimeout(), properties.getMaxRetries());
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private Runnable guarded(String name, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                log.error("Saga {} task failed; it will run again on the next tick", name, e);
            }
        };
    }
}
