package com.crm.messaging.contract;

/**
 * The naming conventions for destinations, consumer groups and retry/dead-letter
 * channels (ADR-017 §7).
 *
 * <p>These are <b>logical</b> names. They contain no broker vocabulary — a "destination"
 * is a topic under the Kafka binder and would be an exchange/queue under another one.
 * That is why this class lives in {@code contract} and not in the adapter package: the
 * naming policy is a project decision, the transport is an implementation detail.
 *
 * <p>The shape is deliberately boring and mechanical, because names that require
 * judgement get invented twice:
 *
 * <pre>
 *   command   crm.&lt;domain&gt;.cmd.&lt;action&gt;.v&lt;n&gt;      crm.product.cmd.create-products.v1
 *   event     crm.&lt;domain&gt;.evt.&lt;fact&gt;.v&lt;n&gt;         crm.order.evt.order-submitted.v1
 *   retry     &lt;destination&gt;.retry.&lt;attempt&gt;         crm.order.evt.order-submitted.v1.retry.1
 *   DLQ       &lt;destination&gt;.dlq                     crm.order.evt.order-submitted.v1.dlq
 *   group     &lt;consumer-service&gt;.&lt;destination&gt;      product-service.crm.order.evt.order-submitted.v1
 * </pre>
 *
 * <p><b>The version is in the destination name, not only in the envelope.</b> Both exist
 * and they answer different questions: the envelope's {@code schemaVersion} lets a single
 * consumer accept a compatible range, while a new destination version is how an
 * INCOMPATIBLE change ships without breaking consumers still reading the old one
 * (ADR-017 §5.3).
 */
public final class Destinations {

    private Destinations() {
    }

    public static final String PREFIX = "crm";
    private static final String COMMAND_MARKER = "cmd";
    private static final String EVENT_MARKER = "evt";
    public static final String RETRY_MARKER = "retry";
    public static final String DLQ_SUFFIX = "dlq";

    /** Aggregate/domain names used in destinations. Kept as constants so a typo is a compile error. */
    public static final String DOMAIN_ORDER = "order";
    public static final String DOMAIN_PRODUCT = "product";
    public static final String DOMAIN_ACCOUNT = "account";
    public static final String DOMAIN_SALE = "sale";

    /** {@code crm.<domain>.cmd.<action>.v<n>} — a command has exactly one intended handler. */
    public static String command(String domain, String action, int version) {
        return join(domain, COMMAND_MARKER, action, version);
    }

    /** {@code crm.<domain>.evt.<fact>.v<n>} — an event is a fact, any number of readers. */
    public static String event(String domain, String fact, int version) {
        return join(domain, EVENT_MARKER, fact, version);
    }

    /**
     * {@code <destination>.retry.<attempt>} — a distinct destination per attempt rather
     * than one shared retry channel, so a slow-poison message on attempt 3 cannot delay
     * every message still on attempt 1.
     */
    public static String retry(String destination, int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("retry attempt must be >= 1, was " + attempt);
        }
        return destination + "." + RETRY_MARKER + "." + attempt;
    }

    /** {@code <destination>.dlq} — terminal. Nothing consumes it automatically; it is an operator surface. */
    public static String deadLetter(String destination) {
        return destination + "." + DLQ_SUFFIX;
    }

    /**
     * {@code <consumer-service>.<destination>} — the consumer group.
     *
     * <p>The CONSUMING service's name comes first because the group identifies "who is
     * reading", and every instance of one service must share one group or the same
     * message gets processed once per instance. It is also the value written to the
     * Inbox {@code consumer_group} column, which is what lets two different services
     * legitimately process the same messageId while one service never processes it twice
     * (ADR-017 §8.2).
     */
    public static String consumerGroup(String consumerService, String destination) {
        require(consumerService, "consumerService");
        require(destination, "destination");
        return consumerService + "." + destination;
    }

    private static String join(String domain, String marker, String name, int version) {
        require(domain, "domain");
        require(name, "name");
        if (version < 1) {
            throw new IllegalArgumentException("destination version must be >= 1, was " + version);
        }
        return PREFIX + "." + domain + "." + marker + "." + name + ".v" + version;
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
