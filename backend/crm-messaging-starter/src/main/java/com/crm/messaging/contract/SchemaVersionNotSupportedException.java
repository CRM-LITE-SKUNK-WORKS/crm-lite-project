package com.crm.messaging.contract;

/**
 * A message arrived whose {@code schemaVersion} this build does not understand
 * (ADR-017 §5.3).
 *
 * <p>Deliberately NOT retryable: redelivering the same bytes to the same build will
 * fail identically forever, so a retry loop would just be a slower way of reaching the
 * dead-letter destination while blocking the partition behind it. The consumer routes
 * this straight to the DLQ and increments the dead-letter metric, which is the signal an
 * operator can actually act on (a producer shipped ahead of a consumer).
 */
public class SchemaVersionNotSupportedException extends RuntimeException {

    private final String messageType;
    private final int schemaVersion;

    public SchemaVersionNotSupportedException(String messageType, int schemaVersion) {
        super("Unsupported schemaVersion " + schemaVersion + " for message type " + messageType
                + " (this build supports " + MessageTypes.minimumSupportedVersion(messageType)
                + ".." + MessageTypes.currentVersion(messageType) + ")");
        this.messageType = messageType;
        this.schemaVersion = schemaVersion;
    }

    public String getMessageType() {
        return messageType;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }
}
