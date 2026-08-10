package com.crm.messaging.outbox;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code crm.messaging.outbox.*} (ADR-017 §11).
 *
 * <p>Two independent switches, deliberately not one. {@code enabled} decides whether the
 * business transaction writes outbox rows at all; {@code relay.enabled} decides whether
 * anything sends them. Before the SALE cutover both are off in shipped configuration, and
 * they are separable because the intermediate state — record but do not publish — is a
 * real, useful step: it proves the atomicity in a live environment while still touching
 * no broker.
 */
@ConfigurationProperties(prefix = "crm.messaging.outbox")
@Getter
@Setter
public class OutboxProperties {

    /** Write outbox rows inside business transactions. Default OFF until cutover. */
    private boolean enabled = false;

    private final Relay relay = new Relay();
    private final Retention retention = new Retention();

    @Getter
    @Setter
    public static class Relay {

        /**
         * Run the in-process repository relay.
         *
         * <p>Mutually exclusive with the Debezium Outbox Event Router (ADR-017 §9): with
         * Debezium running, Kafka Connect reads the same rows from the WAL, and two
         * relays would publish every message twice. The Compose {@code eventing} profile
         * documents which one is active in that stack.
         */
        private boolean enabled = false;

        /** Poll interval when the relay is running. */
        private Duration pollInterval = Duration.ofSeconds(2);

        /** Rows drained per poll — bounded so one poll cannot hold a connection for a huge batch. */
        private int batchSize = 100;
    }

    @Getter
    @Setter
    public static class Retention {

        private boolean enabled = true;

        /** How long a PUBLISHED row is kept before deletion. Unpublished rows are never deleted. */
        private Duration keepPublishedFor = Duration.ofDays(7);

        private Duration runInterval = Duration.ofHours(1);
    }
}
