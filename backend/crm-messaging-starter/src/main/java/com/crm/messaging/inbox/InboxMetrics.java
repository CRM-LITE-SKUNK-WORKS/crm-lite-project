package com.crm.messaging.inbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Consumer-side instrumentation (ADR-017 §12).
 *
 * <p>Tagged by {@code messageType} rather than split into a counter per type: a new
 * contract must not require a code change here, and Prometheus can already aggregate
 * across a label.
 *
 * <p>A nonzero duplicate rate is NORMAL — the transport is at-least-once by design and
 * these are the duplicates being absorbed for free. The number worth alerting on is
 * {@code crm_inbox_dead_letter_total}, which counts messages this service has stopped
 * trying to process at all.
 */
public class InboxMetrics {

    public static final String DUPLICATES = "crm.inbox.duplicates";
    public static final String PROCESSED = "crm.inbox.processed";
    public static final String FAILURES = "crm.inbox.consumer.failures";
    public static final String DEAD_LETTER = "crm.inbox.dead.letter";

    private final MeterRegistry registry;

    public InboxMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** A redelivery that the Inbox absorbed; no business change happened. */
    public void duplicate(String messageType) {
        counter(DUPLICATES, "Redeliveries absorbed by the inbox uniqueness guard", messageType).increment();
    }

    public void processed(String messageType) {
        counter(PROCESSED, "Messages whose handler committed", messageType).increment();
    }

    /** The handler threw; the claim rolled back with it and the message will be retried. */
    public void consumerFailure(String messageType) {
        counter(FAILURES, "Consumer handler failures; the message stays unprocessed", messageType).increment();
    }

    /** Terminal: routed to the dead-letter destination and not retried. */
    public void deadLettered(String messageType, String reason) {
        Counter.builder(DEAD_LETTER)
                .description("Messages routed to the dead-letter destination")
                .tag("messageType", messageType == null ? "unknown" : messageType)
                .tag("reason", reason == null ? "unknown" : reason)
                .register(registry)
                .increment();
    }

    private Counter counter(String name, String description, String messageType) {
        return Counter.builder(name)
                .description(description)
                .tag("messageType", messageType == null ? "unknown" : messageType)
                .register(registry);
    }
}
