package com.crm.order.saga;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code crm.order.saga.*} — the SALE saga's recovery and draft-hygiene knobs
 * (ADR-018 §8).
 *
 * <p>Every value here is broker-independent by construction: none of it is a Kafka
 * setting, a consumer property or a retry-topic ladder. The redelivery of a message
 * whose handler threw belongs to the transport plus the Inbox (ADR-017 §8.3); what
 * this class configures is the different problem the transport cannot see — a command
 * that was published successfully and simply never answered.
 */
@ConfigurationProperties(prefix = "crm.order.saga")
@Getter
@Setter
public class SaleSagaProperties {

    /** Run the recovery/cleanup scheduler at all. */
    private boolean recoveryEnabled = true;

    /** How often the recovery tick runs. */
    private Duration recoveryInterval = Duration.ofSeconds(30);

    /** How long to wait for a reply before the outstanding command may be reissued. */
    private Duration replyTimeout = Duration.ofSeconds(30);

    /**
     * Reissues per step before the saga escalates to MANUAL_INTERVENTION.
     *
     * <p>Bounded, not infinite: a command that has gone unanswered five times is not
     * going to be answered by a sixth copy, and an unbounded reissue loop turns one
     * broken sale into permanent load on the two services it keeps commanding.
     */
    private int maxRetries = 5;

    /** Multiplier applied to {@link #replyTimeout} per retry (exponential backoff). */
    private double retryBackoffMultiplier = 2.0;

    /** Upper bound on the backoff, so a long-lived saga still retries at a useful rate. */
    private Duration maxRetryDelay = Duration.ofMinutes(10);

    /** Sagas idle longer than this are counted by the stuck gauge. */
    private Duration stuckAfter = Duration.ofMinutes(5);

    /** Rows examined per recovery tick — bounded so one tick cannot load an unbounded backlog. */
    private int recoveryBatchSize = 50;

    private final Draft draft = new Draft();

    @Getter
    @Setter
    public static class Draft {

        /**
         * Cancel WAIT drafts nobody submitted or abandoned.
         *
         * <p>It exists because the browser is allowed to vanish: a user who closes the
         * tab never calls the abandon endpoint, and the draft would otherwise sit in
         * WAIT forever. It CANCELS and never submits — a draft that expired is the one
         * thing that must certainly not be fulfilled (AC-SALE-01-16).
         */
        private boolean cleanupEnabled = true;

        /** Age after which an untouched WAIT draft is cancelled. */
        private Duration staleAfter = Duration.ofHours(2);

        /** Drafts cancelled per tick. */
        private int cleanupBatchSize = 100;
    }
}
