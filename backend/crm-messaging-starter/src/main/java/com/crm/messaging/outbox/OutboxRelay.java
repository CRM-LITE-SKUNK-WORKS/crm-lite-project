package com.crm.messaging.outbox;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;

/**
 * The in-process, repository-compatible Outbox relay (ADR-017 §9, alternative b).
 *
 * <p>The project's PRIMARY relay is the Debezium Outbox Event Router in the Compose
 * {@code eventing} profile; this one exists because a relay that needs Kafka Connect
 * running cannot be exercised by a test, and "the broker was down for 30 seconds" is a
 * behaviour that has to be proven rather than asserted in a document. Exactly one of the
 * two runs at a time (ADR-017 §9.3) — running both publishes everything twice.
 *
 * <p><b>At-least-once, never at-most-once.</b> The row is marked published only AFTER the
 * publisher returns normally, so a crash between the send and the mark redelivers the
 * message. That is the deliberate choice: the receiving Inbox makes a duplicate free
 * (§8.2), while a silently dropped message is unrecoverable. Marking first would trade a
 * problem that is solved for one that is not.
 *
 * <p>A failing message does not block the ones behind it in the batch — but it is not
 * skipped either: it stays unpublished, is retried on every subsequent poll, and its age
 * is what the "oldest unpublished" gauge reports.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMessageRepository repository;
    private final OutboxPublisher publisher;
    private final OutboxStatusWriter statusWriter;
    private final EnvelopeCodec codec;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;

    public OutboxRelay(OutboxMessageRepository repository, OutboxPublisher publisher,
                       OutboxStatusWriter statusWriter, EnvelopeCodec codec,
                       OutboxProperties properties, OutboxMetrics metrics) {
        this.repository = repository;
        this.publisher = publisher;
        this.statusWriter = statusWriter;
        this.codec = codec;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * Drain one batch. Returns how many messages were published, so a test can assert
     * progress instead of sleeping and hoping.
     *
     * <p>NOT transactional as a whole: one long transaction spanning a batch of network
     * sends would pin a pooled connection for the duration of the slowest broker call —
     * the same reasoning that keeps {@code OrderServiceImpl#submit} non-transactional
     * (ADR-016 §5). Each row's status update is its own short transaction
     * ({@link OutboxStatusWriter}).
     */
    public int drainOnce() {
        List<OutboxMessage> batch = repository.findUnpublished(
                PageRequest.of(0, Math.max(1, properties.getRelay().getBatchSize())));
        int published = 0;
        for (OutboxMessage row : batch) {
            if (publishOne(row)) {
                published++;
            }
        }
        return published;
    }

    private boolean publishOne(OutboxMessage row) {
        EventEnvelope envelope = codec.decode(row.getEnvelope());
        try {
            publisher.publish(row.getDestination(), envelope);
        } catch (RuntimeException e) {
            // The broker is unreachable or rejected the message. published_at stays NULL,
            // so the next poll simply tries again — nothing is lost, and nothing needs an
            // operator unless the backlog gauge says so.
            statusWriter.markFailed(row.getMessageId(), e.toString());
            metrics.publishFailed();
            log.warn("Outbox publish failed for message {} ({} -> {}), attempt {}: {}",
                    row.getMessageId(), row.getMessageType(), row.getDestination(),
                    row.getPublishAttempts() + 1, e.toString());
            return false;
        }
        statusWriter.markPublished(row.getMessageId());
        metrics.published();
        log.debug("Outbox published {} ({} -> {})", row.getMessageId(), row.getMessageType(),
                row.getDestination());
        return true;
    }
}
