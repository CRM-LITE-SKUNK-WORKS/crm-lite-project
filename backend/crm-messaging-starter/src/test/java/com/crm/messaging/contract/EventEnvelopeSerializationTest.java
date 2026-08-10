package com.crm.messaging.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The wire format is a contract, so it is tested as one: exact field names, a round trip
 * that preserves every value, and tolerance of fields this build has never seen.
 *
 * <p>These run with no broker, no Spring context and no database — which is the point of
 * the envelope being a plain record (ADR-017 §5).
 */
class EventEnvelopeSerializationTest {

    private final EnvelopeCodec codec = new EnvelopeCodec();

    record SamplePayload(String orderNumber, List<Long> offerIds) {
    }

    private EventEnvelope sample() {
        return new EventEnvelope(
                "11111111-2222-3333-4444-555555555555",
                MessageTypes.ORDER_SUBMITTED,
                MessageTypes.ORDER_SUBMITTED_V,
                MessageTypes.AGGREGATE_ORDER,
                "1261000010",
                "saga-abc",
                "corr-xyz",
                "cause-123",
                Instant.parse("2026-08-07T10:15:30Z"),
                codec.encodePayload(new SamplePayload("1000000018", List.of(1L, 2L))));
    }

    @Test
    @DisplayName("every envelope field survives a round trip unchanged")
    void roundTripsWithoutLoss() {
        EventEnvelope decoded = codec.decode(codec.encode(sample()));

        assertThat(decoded).isEqualTo(sample());
    }

    @Test
    @DisplayName("the JSON field names are the contract, not an implementation detail")
    void usesTheDocumentedFieldNames() {
        String json = codec.encodeToString(sample());

        // Named individually rather than compared to a golden blob: a failure then says
        // WHICH field name changed, and a renamed field is a breaking change for every
        // consumer already deployed.
        assertThat(json)
                .contains("\"messageId\":")
                .contains("\"messageType\":")
                .contains("\"schemaVersion\":")
                .contains("\"aggregateType\":")
                .contains("\"aggregateId\":")
                .contains("\"sagaId\":")
                .contains("\"correlationId\":")
                .contains("\"causationId\":")
                .contains("\"occurredAt\":")
                .contains("\"payload\":");
    }

    @Test
    @DisplayName("occurredAt is ISO-8601 UTC, not an epoch number")
    void serializesTimestampAsIso8601() {
        assertThat(codec.encodeToString(sample())).contains("\"2026-08-07T10:15:30Z\"");
    }

    @Test
    @DisplayName("an unknown envelope field from a newer producer is ignored, not fatal")
    void toleratesUnknownEnvelopeFields() {
        String fromNewerProducer = """
                {"messageId":"m-1","messageType":"crm.order.order-submitted","schemaVersion":1,
                 "aggregateType":"order","aggregateId":"1000000018","sagaId":"s-1",
                 "correlationId":"c-1","causationId":null,"occurredAt":"2026-08-07T10:15:30Z",
                 "payload":"{}","tenantId":"added-in-a-later-release"}
                """;

        EventEnvelope decoded = codec.decode(fromNewerProducer);

        assertThat(decoded.messageId()).isEqualTo("m-1");
        assertThat(decoded.sagaId()).isEqualTo("s-1");
    }

    @Test
    @DisplayName("the payload is decoded into a CONSUMER-owned type, not a shared one")
    void decodesPayloadIntoConsumerType() {
        SamplePayload payload = codec.decodePayload(sample(), SamplePayload.class);

        assertThat(payload.orderNumber()).isEqualTo("1000000018");
        assertThat(payload.offerIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("partitionKey is the sagaId, so one sale's messages stay ordered")
    void partitionKeyPrefersSagaId() {
        assertThat(sample().partitionKey()).isEqualTo("saga-abc");
    }

    @Test
    @DisplayName("partitionKey falls back to the aggregateId when there is no saga")
    void partitionKeyFallsBackToAggregateId() {
        EventEnvelope noSaga = new EventEnvelope("m-2", MessageTypes.PRODUCTS_LINKED, 1,
                MessageTypes.AGGREGATE_ACCOUNT, "1261000010", null, null, null,
                Instant.parse("2026-08-07T10:15:30Z"), "{}");

        assertThat(noSaga.partitionKey()).isEqualTo("1261000010");
    }

    @Test
    @DisplayName("an envelope missing a required identity field cannot be constructed")
    void rejectsIncompleteEnvelopes() {
        assertThatThrownBy(() -> new EventEnvelope(null, MessageTypes.ORDER_SUBMITTED, 1, "order",
                "1", null, null, null, Instant.now(), "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageId");

        assertThatThrownBy(() -> new EventEnvelope("m", MessageTypes.ORDER_SUBMITTED, 0, "order",
                "1", null, null, null, Instant.now(), "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }
}
