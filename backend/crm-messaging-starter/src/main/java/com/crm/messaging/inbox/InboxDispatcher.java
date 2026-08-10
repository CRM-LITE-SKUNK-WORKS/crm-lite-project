package com.crm.messaging.inbox;

import com.crm.messaging.contract.Destinations;
import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.contract.SchemaVersionNotSupportedException;
import com.crm.messaging.outbox.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns raw message bytes into a handled business change (ADR-017 §8.3).
 *
 * <p>This is the seam between "a broker delivered some bytes" and "a plain Java handler
 * ran inside a transaction". It takes {@code byte[]} and returns an {@link Outcome} —
 * no acknowledgement object, no broker offset, no consumer record. Which means the
 * adapter above it is trivial, and every consumer behaviour worth testing (duplicate,
 * failure, unsupported version, restart) is testable by calling this method with a byte
 * array.
 *
 * <p><b>Two failure modes, treated as opposites on purpose:</b>
 * <ul>
 *   <li>a handler that <i>threw</i> is assumed transient — the transaction rolled back and
 *       nothing was applied, so the exception is <b>rethrown</b>. That is the only way to
 *       tell the transport not to acknowledge: returning normally would ack a message that
 *       was never processed, which is the one outcome the Inbox cannot repair;</li>
 *   <li>a message this build cannot <i>parse or version</i> is terminal — every
 *       redelivery would fail identically, so retrying it only blocks the messages behind
 *       it. It is sent to the dead-letter destination and the call returns normally, which
 *       acks it.</li>
 * </ul>
 */
public class InboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InboxDispatcher.class);

    public enum Outcome {
        /** The handler ran and committed. */
        PROCESSED,
        /** A redelivery the inbox absorbed; nothing happened. */
        DUPLICATE,
        /** Terminal — sent to the dead-letter destination, do not redeliver. */
        DEAD_LETTERED
    }

    private final InboxGuard guard;
    private final EnvelopeCodec codec;
    private final InboxMetrics metrics;
    private final OutboxPublisher publisher;

    public InboxDispatcher(InboxGuard guard, EnvelopeCodec codec, InboxMetrics metrics,
                           OutboxPublisher publisher) {
        this.guard = guard;
        this.codec = codec;
        this.metrics = metrics;
        this.publisher = publisher;
    }

    /**
     * @param sourceDestination the logical destination the bytes arrived on — used to name
     *                          the dead-letter destination, never to decide behaviour
     * @throws RuntimeException the handler's own exception, rethrown unchanged so the
     *                          transport does not acknowledge a message that was not applied
     */
    public Outcome dispatch(byte[] body, String sourceDestination, String consumerGroup,
                            MessageHandler handler) {
        EventEnvelope envelope;
        try {
            envelope = codec.decode(body);
        } catch (RuntimeException e) {
            // Undecodable: there is not even a messageId to deduplicate on, so this can
            // only be a wire-format mistake. Retrying it forever would stall the group.
            metrics.deadLettered(null, "undecodable");
            deadLetterRaw(sourceDestination, body, e);
            return Outcome.DEAD_LETTERED;
        }

        try {
            boolean handled = guard.claimAndHandle(envelope, consumerGroup, handler);
            if (handled) {
                metrics.processed(envelope.messageType());
                return Outcome.PROCESSED;
            }
            return Outcome.DUPLICATE;
        } catch (SchemaVersionNotSupportedException e) {
            metrics.deadLettered(envelope.messageType(), "schema-version");
            log.error("Dead-lettering {} ({}): {}", envelope.messageId(), envelope.messageType(),
                    e.getMessage());
            sendToDeadLetter(sourceDestination, envelope);
            return Outcome.DEAD_LETTERED;
        } catch (RuntimeException e) {
            metrics.consumerFailure(envelope.messageType());
            log.warn("Consumer failed on {} ({}) for group {}; leaving it for redelivery",
                    envelope.messageId(), envelope.messageType(), consumerGroup, e);
            // Rethrown, not swallowed: the claim rolled back with the handler, so the
            // message is genuinely unprocessed and must NOT be acknowledged. Swallowing it
            // here would ack a message nobody applied.
            throw e;
        }
    }

    private void sendToDeadLetter(String sourceDestination, EventEnvelope envelope) {
        try {
            publisher.publish(Destinations.deadLetter(sourceDestination), envelope);
        } catch (RuntimeException e) {
            // The DLQ itself is unreachable. Logged at ERROR and swallowed: rethrowing
            // would turn a terminal message back into an infinite redelivery loop, which
            // is the one outcome dead-lettering exists to prevent. The metric has already
            // been incremented, so the count stays honest.
            log.error("Could not dead-letter message {} from {}", envelope.messageId(),
                    sourceDestination, e);
        }
    }

    private void deadLetterRaw(String sourceDestination, byte[] body, RuntimeException cause) {
        log.error("Undecodable message on {} ({} bytes) dead-lettered", sourceDestination,
                body == null ? 0 : body.length, cause);
    }
}
