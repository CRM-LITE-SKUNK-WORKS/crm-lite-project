package com.crm.messaging.contract;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializes and deserializes {@link EventEnvelope}s (ADR-017 §5.4).
 *
 * <p>It owns its OWN {@link ObjectMapper} rather than reusing the application's web
 * mapper. The two have opposite requirements: the web mapper is tuned for the HTTP API
 * (and customer-service, for instance, installs a string-trimming deserializer for
 * form input) while the wire format must stay byte-stable across services and releases.
 * A change made for an HTTP concern silently changing the shape of published events is
 * the exact coupling this avoids.
 *
 * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES} is OFF for the ENVELOPE: that is what makes a
 * producer able to add an envelope field without every consumer failing on the next
 * message. Payload strictness is the consumer's own decision, made where the
 * consumer-owned DTO is decoded.
 */
public class EnvelopeCodec {

    private final ObjectMapper mapper;

    public EnvelopeCodec() {
        this(JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());
    }

    public EnvelopeCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** The envelope as UTF-8 JSON bytes — the payload column of the Outbox and the wire body. */
    public byte[] encode(EventEnvelope envelope) {
        return mapper.writeValueAsBytes(envelope);
    }

    public String encodeToString(EventEnvelope envelope) {
        return mapper.writeValueAsString(envelope);
    }

    public EventEnvelope decode(byte[] bytes) {
        return mapper.readValue(bytes, EventEnvelope.class);
    }

    public EventEnvelope decode(String json) {
        return mapper.readValue(json, EventEnvelope.class);
    }

    /** Serialize a producer-side payload object to the JSON text the envelope carries. */
    public String encodePayload(Object payload) {
        return mapper.writeValueAsString(payload);
    }

    /**
     * Decode the payload into a <b>consumer-owned</b> DTO.
     *
     * <p>The {@code Class} argument is the consumer's own type on purpose (ADR-017 §6):
     * there is no shared payload jar to import, so a consumer declares exactly the
     * fields it uses and is structurally immune to producer fields it does not.
     *
     * @throws SchemaVersionNotSupportedException if this build cannot read that version —
     *         checked BEFORE parsing, because a version we do not understand is not a
     *         payload we should be guessing at.
     */
    public <T> T decodePayload(EventEnvelope envelope, Class<T> payloadType) {
        if (!MessageTypes.isSupported(envelope.messageType(), envelope.schemaVersion())) {
            throw new SchemaVersionNotSupportedException(envelope.messageType(), envelope.schemaVersion());
        }
        return mapper.readValue(envelope.payload(), payloadType);
    }

    /** Convenience for adapters that receive raw bytes off the wire. */
    public EventEnvelope decode(byte[] bytes, java.nio.charset.Charset charset) {
        return decode(new String(bytes, charset == null ? StandardCharsets.UTF_8 : charset));
    }
}
