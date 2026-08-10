package com.crm.messaging.outbox;

import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The relay's status updates, in their own committed transactions.
 *
 * <p>A separate bean, not private methods on {@link OutboxRelay}, for the reason this
 * codebase has already been bitten by once ({@code OrderPersistence}'s javadoc):
 * Spring's {@code @Transactional} is proxy-based, so {@code this.markPublished(...)}
 * would bypass the proxy entirely and the annotation would silently do nothing. Here
 * that would mean a published message whose row was never marked published — republished
 * on every poll, forever.
 *
 * <p>{@code REQUIRES_NEW} because the mark must commit on its own: the relay holds no
 * surrounding transaction (deliberately — see {@link OutboxRelay#drainOnce()}), and the
 * mark must survive independently of whatever the next row in the batch does.
 */
public class OutboxStatusWriter {

    private final OutboxMessageRepository repository;
    private final Clock clock;

    public OutboxStatusWriter(OutboxMessageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(String messageId) {
        repository.findById(messageId).ifPresent(row -> {
            row.setPublishedAt(Instant.now(clock));
            row.setPublishAttempts(row.getPublishAttempts() + 1);
            row.setLastError(null);
            repository.save(row);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String messageId, String error) {
        repository.findById(messageId).ifPresent(row -> {
            row.setPublishAttempts(row.getPublishAttempts() + 1);
            row.setLastError(error == null || error.length() <= 1000
                    ? error
                    : error.substring(0, 1000));
            repository.save(row);
        });
    }
}
