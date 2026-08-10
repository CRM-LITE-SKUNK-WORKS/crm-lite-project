package com.crm.messaging.contract;

/**
 * The message-type catalog and each type's CURRENT schema version (ADR-017 §5.2).
 *
 * <p>Only names and version numbers live here — no payload classes. The authoritative,
 * language-neutral definition of each payload is the JSON Schema under
 * {@code docs/contracts/events/}; this class exists so that Java producers and
 * consumers cannot disagree with each other about the NAME through a typo, which is
 * the failure mode a string literal in three services actually produces.
 *
 * <p>Adding a field with a default is a compatible change and keeps the version.
 * Removing a field, renaming one, or narrowing a type is incompatible: bump the version
 * here AND publish to a new {@code .v<n>} destination (Destinations), because a consumer
 * that has not been redeployed is still reading the old one.
 */
public final class MessageTypes {

    private MessageTypes() {
    }

    // ---------------------------------------------------------------------------
    // Events — facts that already happened. Past tense, never imperative.
    // ---------------------------------------------------------------------------

    /** order-service: the KR-12 order header was committed locally (order_db). */
    public static final String ORDER_SUBMITTED = "crm.order.order-submitted";
    public static final int ORDER_SUBMITTED_V = 1;

    /** product-service: the requested products exist (PNDG) and carry their ids/amounts. */
    public static final String PRODUCTS_CREATED = "crm.product.products-created";
    public static final int PRODUCTS_CREATED_V = 1;

    /** account-service: the products are linked to the billing account — the sale's commit point. */
    public static final String PRODUCTS_LINKED = "crm.account.products-linked";
    public static final int PRODUCTS_LINKED_V = 1;

    // ---------------------------------------------------------------------------
    // Aggregate type names (envelope aggregateType + Debezium routing field, §9).
    // ---------------------------------------------------------------------------

    public static final String AGGREGATE_ORDER = "order";
    public static final String AGGREGATE_PRODUCT = "product";
    public static final String AGGREGATE_ACCOUNT = "account";

    /**
     * The lowest schema version a consumer of {@code messageType} still understands.
     *
     * <p>Everything is at 1 today, so this is currently a constant — it is a method
     * rather than a second constant per type because the FIRST incompatible bump is
     * exactly when a per-type answer becomes necessary, and a method is the shape that
     * survives that change without every call site moving.
     */
    public static int minimumSupportedVersion(String messageType) {
        require(messageType);
        return 1;
    }

    /** The version this build PRODUCES for {@code messageType}. */
    public static int currentVersion(String messageType) {
        require(messageType);
        return switch (messageType) {
            case ORDER_SUBMITTED -> ORDER_SUBMITTED_V;
            case PRODUCTS_CREATED -> PRODUCTS_CREATED_V;
            case PRODUCTS_LINKED -> PRODUCTS_LINKED_V;
            default -> throw new IllegalArgumentException("Unknown message type: " + messageType);
        };
    }

    /**
     * Whether a consumer of this build can decode {@code schemaVersion} of
     * {@code messageType}. A version ABOVE what this build produces is rejected rather
     * than optimistically parsed: forward-guessing is how a consumer silently drops the
     * field that a producer added precisely because it mattered.
     */
    public static boolean isSupported(String messageType, int schemaVersion) {
        return schemaVersion >= minimumSupportedVersion(messageType)
                && schemaVersion <= currentVersion(messageType);
    }

    private static void require(String messageType) {
        if (messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException("messageType is required");
        }
    }
}
