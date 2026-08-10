package com.crm.messaging.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Outbox instrumentation (ADR-017 §12), on the existing Micrometer/Prometheus pipeline
 * every service already exposes at {@code /actuator/prometheus}.
 *
 * <p>The two gauges answer different questions and neither substitutes for the other:
 * <b>backlog</b> ({@code crm_outbox_backlog_messages}) says how much is waiting, and
 * <b>oldest age</b> ({@code crm_outbox_oldest_unpublished_age_seconds}) says whether
 * anything is moving. A relay that is stuck on one poison message keeps a small, flat
 * backlog while the age climbs without limit — the alert has to be on the age.
 *
 * <p>Both gauges query on scrape. That is a per-scrape {@code count}/{@code min} on an
 * indexed partial predicate against a table that retention keeps small (§10) — cheap
 * enough that caching it would only add a staleness question nobody wants to answer
 * during an incident.
 */
public class OutboxMetrics {

    public static final String BACKLOG = "crm.outbox.backlog.messages";
    public static final String OLDEST_AGE = "crm.outbox.oldest.unpublished.age.seconds";
    public static final String PUBLISHED = "crm.outbox.published";
    public static final String PUBLISH_FAILURES = "crm.outbox.publish.failures";
    public static final String RETENTION_DELETED = "crm.outbox.retention.deleted";

    private final Counter published;
    private final Counter publishFailures;
    private final Counter retentionDeleted;

    public OutboxMetrics(MeterRegistry registry, OutboxMessageRepository repository, Clock clock) {
        io.micrometer.core.instrument.Gauge
                .builder(BACKLOG, repository, OutboxMessageRepository::countByPublishedAtIsNull)
                .description("Outbox rows written but not yet accepted by the broker")
                .register(registry);
        io.micrometer.core.instrument.Gauge
                .builder(OLDEST_AGE, repository, repo -> oldestAgeSeconds(repo, clock))
                .description("Age of the oldest unpublished outbox row; 0 when the outbox is drained")
                .baseUnit("seconds")
                .register(registry);

        this.published = Counter.builder(PUBLISHED)
                .description("Outbox messages accepted by the broker")
                .register(registry);
        this.publishFailures = Counter.builder(PUBLISH_FAILURES)
                .description("Outbox publish attempts rejected or unreachable; the row stays unpublished")
                .register(registry);
        this.retentionDeleted = Counter.builder(RETENTION_DELETED)
                .description("Published outbox rows removed by retention")
                .register(registry);
    }

    private static double oldestAgeSeconds(OutboxMessageRepository repository, Clock clock) {
        return repository.findOldestUnpublishedOccurredAt()
                .map(oldest -> (double) Duration.between(oldest, Instant.now(clock)).toSeconds())
                .orElse(0.0);
    }

    public void published() {
        published.increment();
    }

    public void publishFailed() {
        publishFailures.increment();
    }

    public void retentionDeleted(int rows) {
        retentionDeleted.increment(rows);
    }
}
