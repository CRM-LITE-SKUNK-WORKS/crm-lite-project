package com.crm.messaging.adapter.stream;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.outbox.OutboxPublisher;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;

/**
 * The ONLY class in the project that hands a message to a broker (ADR-017 §4).
 *
 * <p>Spring Cloud Stream's functional model, via {@link StreamBridge}: the destination is
 * a runtime value from the Outbox row, so a static {@code Supplier} binding per message
 * type would mean a new binding — and a redeploy — for every new contract.
 *
 * <p>No Kafka type appears here either. {@code StreamBridge} and {@code MessageBuilder}
 * are binder-neutral; swapping the Kafka binder for another one is a dependency and
 * configuration change with no code change in this class, and no code change anywhere
 * above it by construction.
 *
 * <p>The body is the encoded envelope as {@code byte[]}. Bytes, not a POJO, so that
 * neither a content-type negotiation nor a converter registered for the HTTP API can
 * quietly alter the published bytes: what the Outbox stored is exactly what goes out.
 */
public class StreamBridgeOutboxPublisher implements OutboxPublisher {

    private final StreamBridge streamBridge;
    private final EnvelopeCodec codec;

    public StreamBridgeOutboxPublisher(StreamBridge streamBridge, EnvelopeCodec codec) {
        this.streamBridge = streamBridge;
        this.codec = codec;
    }

    @Override
    public void publish(String destination, EventEnvelope envelope) {
        boolean sent = streamBridge.send(destination, MessageBuilder
                .withPayload(codec.encode(envelope))
                .setHeader(MessageHeaders.MESSAGE_ID, envelope.messageId())
                .setHeader(MessageHeaders.MESSAGE_TYPE, envelope.messageType())
                .setHeader(MessageHeaders.SCHEMA_VERSION, String.valueOf(envelope.schemaVersion()))
                .setHeader(MessageHeaders.AGGREGATE_TYPE, envelope.aggregateType())
                .setHeader(MessageHeaders.AGGREGATE_ID, envelope.aggregateId())
                .setHeader(MessageHeaders.SAGA_ID, envelope.sagaId())
                .setHeader(MessageHeaders.CORRELATION_ID, envelope.correlationId())
                .setHeader(MessageHeaders.CAUSATION_ID, envelope.causationId())
                .setHeader(MessageHeaders.OCCURRED_AT, envelope.occurredAt().toString())
                // Per-sale ordering (ADR-017 §7.6): sagaId, else the aggregate id. The
                // binder turns this into the partition assignment; the envelope decides
                // the value, so the ordering rule is a contract decision, not a Kafka one.
                .setHeader(MessageHeaders.PARTITION_KEY, envelope.partitionKey())
                .build());
        if (!sent) {
            // StreamBridge#send returns false when the binder refused the message. The
            // relay's contract is "throw if not accepted", and returning normally here
            // would let the relay mark a lost message as published.
            throw new IllegalStateException("Broker did not accept message " + envelope.messageId()
                    + " for destination " + destination);
        }
    }
}
