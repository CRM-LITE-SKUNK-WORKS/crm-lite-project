package com.crm.messaging.outbox;

import com.crm.messaging.contract.EventEnvelope;

/**
 * The port the relay sends through (ADR-017 §4).
 *
 * <p>Two parameters, both plain: a logical destination string and an envelope. No
 * {@code Message}, no headers map, no {@code ProducerRecord}, no callback, no
 * {@code CompletableFuture} of a broker type. Everything a broker needs — serializers,
 * the partition key derivation, headers, acks, the topic name — lives behind this
 * interface in {@code com.crm.messaging.adapter.stream}, which is the single package the
 * architecture guard tests allow to see Spring Cloud Stream and Kafka.
 *
 * <p>This is also why "publisher/broker temporarily unavailable" is testable without a
 * broker: a test implementation that throws is a two-line class.
 */
public interface OutboxPublisher {

    /**
     * Send synchronously; return normally only once the broker has ACCEPTED the message.
     *
     * <p>Synchronous on purpose. The relay marks the row published immediately after this
     * returns, so an asynchronous "handed to a buffer" result would let the row be marked
     * published for a message the broker never got — reintroducing exactly the loss the
     * Outbox exists to prevent.
     *
     * @throws RuntimeException if the message was not accepted; the relay leaves the row
     *                          unpublished and retries on the next poll
     */
    void publish(String destination, EventEnvelope envelope);
}
