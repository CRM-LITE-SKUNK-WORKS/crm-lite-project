package com.crm.messaging.adapter.stream;

/**
 * Transport header names (ADR-017 §7.7).
 *
 * <p>They live in the adapter package, not in {@code contract}, because headers are a
 * transport concern: the envelope is the contract, and every value below is a duplicate
 * of a field already inside it. They exist only so that an operator with a broker console
 * — or a routing rule, or a Kafka Connect SMT — can see and filter on the important
 * fields without deserializing the body.
 *
 * <p>Nothing may make a business decision from a header. Anything a consumer needs is read
 * from the envelope, which is the artifact the Outbox stored and the contract documents.
 */
public final class MessageHeaders {

    private MessageHeaders() {
    }

    public static final String MESSAGE_ID = "crm-message-id";
    public static final String MESSAGE_TYPE = "crm-message-type";
    public static final String SCHEMA_VERSION = "crm-schema-version";
    public static final String AGGREGATE_TYPE = "crm-aggregate-type";
    public static final String AGGREGATE_ID = "crm-aggregate-id";
    public static final String SAGA_ID = "crm-saga-id";
    public static final String CORRELATION_ID = "crm-correlation-id";
    public static final String CAUSATION_ID = "crm-causation-id";
    public static final String OCCURRED_AT = "crm-occurred-at";

    /**
     * Spring Cloud Stream's binder-neutral partition-key header. The Kafka binder maps it
     * to the record key; another binder maps it to its own partitioning concept — which is
     * the point of not writing a Kafka record key directly.
     */
    public static final String PARTITION_KEY = "partitionKey";
}
