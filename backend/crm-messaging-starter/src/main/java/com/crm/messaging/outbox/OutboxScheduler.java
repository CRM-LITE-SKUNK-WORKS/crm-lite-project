package com.crm.messaging.outbox;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs the relay and the retention purge on their own single-threaded scheduler.
 *
 * <p>A {@link SmartLifecycle} with a private executor rather than {@code @Scheduled} +
 * {@code @EnableScheduling}: enabling Spring's scheduling from a shared starter would
 * switch it on for every service that depends on this module, changing behaviour well
 * outside this module's concern. This way the messaging module schedules only its own
 * work and stops it cleanly on shutdown.
 *
 * <p>One thread on purpose. The relay's ordering guarantee is per partition key, and a
 * pool would let two threads publish two messages of the same sale out of order — the one
 * ordering property the project actually promises (ADR-017 §7.6).
 *
 * <p>Every task body is wrapped: an escaping exception would silently cancel the
 * {@code scheduleWithFixedDelay} task, leaving a process that looks healthy and relays
 * nothing.
 */
public class OutboxScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxRelay relay;
    private final OutboxRetentionJob retentionJob;
    private final OutboxProperties properties;

    private ScheduledExecutorService executor;
    private volatile boolean running;

    public OutboxScheduler(OutboxRelay relay, OutboxRetentionJob retentionJob, OutboxProperties properties) {
        this.relay = relay;
        this.retentionJob = retentionJob;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        boolean relayEnabled = properties.getRelay().isEnabled();
        boolean retentionEnabled = properties.getRetention().isEnabled();
        if (!relayEnabled && !retentionEnabled) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "crm-outbox");
            thread.setDaemon(true);
            return thread;
        });
        if (relayEnabled) {
            long millis = Math.max(100, properties.getRelay().getPollInterval().toMillis());
            executor.scheduleWithFixedDelay(guarded("relay", relay::drainOnce), millis, millis,
                    TimeUnit.MILLISECONDS);
            log.info("Outbox relay started (poll every {} ms, batch {})", millis,
                    properties.getRelay().getBatchSize());
        }
        if (retentionEnabled) {
            long millis = Math.max(1000, properties.getRetention().getRunInterval().toMillis());
            executor.scheduleWithFixedDelay(guarded("retention", retentionJob::purge), millis, millis,
                    TimeUnit.MILLISECONDS);
        }
        running = true;
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
                log.error("Outbox {} task failed; it will run again on the next tick", name, e);
            }
        };
    }
}
