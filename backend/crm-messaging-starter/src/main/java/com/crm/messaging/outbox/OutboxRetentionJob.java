package com.crm.messaging.outbox;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes Outbox rows that have already been published and are older than the retention
 * horizon (ADR-017 §10).
 *
 * <p><b>The predicate is the safety property.</b> {@code published_at IS NOT NULL AND
 * published_at < cutoff} — an unpublished row is never eligible no matter how old it is,
 * because "old" is precisely the symptom of a message that still needs to go out. A
 * time-only cleanup would quietly delete the backlog it was supposed to alert on.
 *
 * <p>It runs on a schedule in the application rather than as a database job so that it
 * needs no privilege the service does not already have, and shows up in the same metrics
 * and structured logs as everything else.
 */
public class OutboxRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

    private final OutboxMessageRepository repository;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;
    private final Clock clock;

    public OutboxRetentionJob(OutboxMessageRepository repository, OutboxProperties properties,
                              OutboxMetrics metrics, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** @return rows deleted, so a test can assert the horizon instead of inspecting the table. */
    @Transactional
    public int purge() {
        if (!properties.getRetention().isEnabled()) {
            return 0;
        }
        Instant cutoff = Instant.now(clock).minus(properties.getRetention().getKeepPublishedFor());
        int deleted = repository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            metrics.retentionDeleted(deleted);
            log.info("Outbox retention removed {} published rows older than {}", deleted, cutoff);
        }
        return deleted;
    }
}
