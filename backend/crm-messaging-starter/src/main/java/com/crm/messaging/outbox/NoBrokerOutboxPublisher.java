package com.crm.messaging.outbox;

import com.crm.messaging.contract.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The publisher in use while there is no broker (ADR-017 §11) — the pre-cutover default.
 *
 * <p>It <b>throws</b> rather than silently discarding. A no-op publisher would let the
 * relay mark every row published and quietly drop every message, and the backlog gauge
 * would read a healthy zero the whole time. Throwing means the row stays unpublished, the
 * backlog is visible, and the failure counter says exactly what is wrong.
 *
 * <p>In practice nothing calls it before cutover: the relay is disabled by default too,
 * so this bean exists to make the wiring complete and to fail loudly if someone enables
 * the relay without enabling the broker adapter.
 */
public class NoBrokerOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoBrokerOutboxPublisher.class);

    @Override
    public void publish(String destination, EventEnvelope envelope) {
        log.error("Outbox relay is enabled but no broker adapter is active: message {} for {} "
                + "cannot be published. Enable crm.messaging.broker.enabled and configure a binder, "
                + "or turn the relay off.", envelope.messageId(), destination);
        throw new IllegalStateException("No broker adapter is active; message " + envelope.messageId()
                + " for destination " + destination + " was not published");
    }
}
