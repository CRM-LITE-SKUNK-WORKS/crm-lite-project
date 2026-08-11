package com.crm.order.saga;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * SALE saga instrumentation (ADR-018 §9), on the Micrometer/Prometheus pipeline the
 * observability work already merged — no new exporter, no new scrape target.
 *
 * <p><b>What is NOT here, because it already exists.</b> Duplicate messages
 * ({@code crm_inbox_duplicates_total}), consumer failures and dead-letter activity
 * ({@code crm_inbox_dead_letter_total}) are Inbox properties and are counted by
 * {@code InboxMetrics} for every consumer in every service. Re-counting them per saga
 * would produce two numbers for one event that disagree the first time a message is
 * dead-lettered before the saga ever sees it.
 *
 * <p>The state gauges query on scrape against an indexed column. Same trade-off
 * {@code OutboxMetrics} takes and for the same reason: a cached value would introduce a
 * staleness question precisely when someone is staring at the dashboard asking why a
 * sale is stuck.
 */
public class SaleSagaMetrics {

    public static final String STATE = "crm.saga.state";
    public static final String COMPLETED = "crm.saga.completed";
    public static final String FAILED = "crm.saga.failed";
    public static final String COMPENSATION_ATTEMPTS = "crm.saga.compensation.attempts";
    public static final String COMPENSATION_FAILURES = "crm.saga.compensation.failures";
    public static final String STUCK = "crm.saga.stuck";
    public static final String UNEXPECTED = "crm.saga.unexpected.messages";
    public static final String RETRIES = "crm.saga.command.reissues";
    public static final String DURATION = "crm.sale.async.duration";

    private static final Set<SaleSagaState> TERMINAL =
            EnumSet.of(SaleSagaState.COMPLETED, SaleSagaState.FAILED, SaleSagaState.MANUAL_INTERVENTION);

    private final MeterRegistry registry;
    private final Clock clock;
    private final Counter completed;
    private final Counter failed;
    private final Counter compensationAttempts;
    private final Counter compensationFailures;
    private final Counter reissues;
    private final Timer duration;

    public SaleSagaMetrics(MeterRegistry registry, SaleSagaRepository repository,
                           SaleSagaProperties properties, Clock clock) {
        this.registry = registry;
        this.clock = clock;

        // One gauge per state rather than one gauge with a state label set at write
        // time: a state nobody is in must still report 0. A label that only appears
        // when a saga enters COMPENSATING_PRODUCTS means the graph is empty both when
        // nothing is compensating and when the metric was never wired up.
        for (SaleSagaState state : SaleSagaState.values()) {
            Gauge.builder(STATE, repository, repo -> repo.countByCurrentState(state))
                    .description("SALE sagas currently in this internal state")
                    .tag("state", state.name())
                    .register(registry);
        }

        Gauge.builder(STUCK, repository,
                        repo -> repo.countStuck(TERMINAL, Instant.now(clock).minus(properties.getStuckAfter())))
                .description("Non-terminal SALE sagas that have not changed state recently")
                .register(registry);

        this.completed = Counter.builder(COMPLETED)
                .description("SALE sagas that reached core consistency")
                .register(registry);
        this.failed = Counter.builder(FAILED)
                .description("SALE sagas that ended without completing the sale")
                .register(registry);
        this.compensationAttempts = Counter.builder(COMPENSATION_ATTEMPTS)
                .description("Compensation commands issued by SALE sagas")
                .register(registry);
        this.compensationFailures = Counter.builder(COMPENSATION_FAILURES)
                .description("Compensation commands that came back failed; the saga needs an operator")
                .register(registry);
        this.reissues = Counter.builder(RETRIES)
                .description("Saga commands reissued because no reply arrived in time")
                .register(registry);
        this.duration = Timer.builder(DURATION)
                .description("Submit-to-terminal duration of an asynchronous sale")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void completed(Instant startedAt) {
        completed.increment();
        record(startedAt);
    }

    public void failed(SaleSagaState terminalState, Instant startedAt) {
        failed.increment();
        Counter.builder(FAILED + ".by.state")
                .description("Failed SALE sagas by the internal state they ended in")
                .tag("state", terminalState.name())
                .register(registry)
                .increment();
        record(startedAt);
    }

    public void compensationAttempted() {
        compensationAttempts.increment();
    }

    public void compensationFailed() {
        compensationFailures.increment();
    }

    public void commandReissued(SaleSagaState state) {
        reissues.increment();
        Counter.builder(RETRIES + ".by.state")
                .tag("state", state.name())
                .register(registry)
                .increment();
    }

    /**
     * A reply that cannot apply to the saga's current state (ADR-018 §8): a stale
     * redelivery, or a step answering for a phase the saga already left.
     *
     * <p>Counted rather than ignored, and counted separately from Inbox duplicates: a
     * duplicate is the transport working as designed, whereas a persistent nonzero rate
     * here means two messages disagree about where a sale is — which is the signature of
     * a genuine ordering or contract defect, not of at-least-once delivery.
     */
    public void unexpectedMessage(String messageType, SaleSagaState state) {
        Counter.builder(UNEXPECTED)
                .description("Replies that did not match the saga's current state and did not advance it")
                .tag("messageType", messageType == null ? "unknown" : messageType)
                .tag("state", state == null ? "none" : state.name())
                .register(registry)
                .increment();
    }

    private void record(Instant startedAt) {
        if (startedAt != null) {
            duration.record(Duration.between(startedAt, Instant.now(clock)));
        }
    }
}
