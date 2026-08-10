package com.crm.messaging.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Schema-version acceptance and rejection (ADR-017 §5.3).
 *
 * <p>The asymmetry is deliberate and is what these tests pin down: a version this build
 * KNOWS is accepted, and a version from the future is refused rather than optimistically
 * parsed. Parsing a v2 payload with v1 rules would produce a DTO that silently lost the
 * field the producer bumped the version for — a wrong answer is worse than a loud one.
 */
class SchemaVersionCompatibilityTest {

    private final EnvelopeCodec codec = new EnvelopeCodec();

    record Payload(String orderNumber) {
    }

    private EventEnvelope envelopeAtVersion(int schemaVersion) {
        return new EventEnvelope("m-1", MessageTypes.ORDER_SUBMITTED, schemaVersion,
                MessageTypes.AGGREGATE_ORDER, "1000000018", "saga-1", "corr-1", null,
                Instant.parse("2026-08-07T10:15:30Z"),
                codec.encodePayload(new Payload("1000000018")));
    }

    @Test
    @DisplayName("the version this build produces is supported")
    void acceptsCurrentVersion() {
        assertThat(MessageTypes.isSupported(MessageTypes.ORDER_SUBMITTED,
                MessageTypes.currentVersion(MessageTypes.ORDER_SUBMITTED))).isTrue();

        assertThat(codec.decodePayload(envelopeAtVersion(MessageTypes.ORDER_SUBMITTED_V), Payload.class)
                .orderNumber()).isEqualTo("1000000018");
    }

    @Test
    @DisplayName("a version from a newer producer is rejected, not guessed at")
    void rejectsVersionAboveWhatThisBuildProduces() {
        int fromTheFuture = MessageTypes.currentVersion(MessageTypes.ORDER_SUBMITTED) + 1;

        assertThat(MessageTypes.isSupported(MessageTypes.ORDER_SUBMITTED, fromTheFuture)).isFalse();
        assertThatThrownBy(() -> codec.decodePayload(envelopeAtVersion(fromTheFuture), Payload.class))
                .isInstanceOf(SchemaVersionNotSupportedException.class)
                .hasMessageContaining("Unsupported schemaVersion " + fromTheFuture)
                .hasMessageContaining(MessageTypes.ORDER_SUBMITTED);
    }

    @Test
    @DisplayName("a version below the supported floor is rejected too")
    void rejectsVersionBelowTheFloor() {
        assertThat(MessageTypes.isSupported(MessageTypes.ORDER_SUBMITTED, 0)).isFalse();
    }

    @Test
    @DisplayName("the rejection carries the type and version an operator needs")
    void rejectionIsDiagnosable() {
        SchemaVersionNotSupportedException e =
                new SchemaVersionNotSupportedException(MessageTypes.PRODUCTS_LINKED, 9);

        assertThat(e.getMessageType()).isEqualTo(MessageTypes.PRODUCTS_LINKED);
        assertThat(e.getSchemaVersion()).isEqualTo(9);
        // The supported range is in the message: "9 is unsupported" alone does not tell an
        // operator whether the producer or the consumer is the one to redeploy.
        assertThat(e.getMessage()).contains("this build supports 1..1");
    }

    @Test
    @DisplayName("an unknown message type is an error, not a default version")
    void unknownMessageTypeIsRejected() {
        assertThatThrownBy(() -> MessageTypes.currentVersion("crm.something.never-declared"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown message type");
    }
}
