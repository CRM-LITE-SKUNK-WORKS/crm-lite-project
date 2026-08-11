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
    // SALE saga — commands (imperative, exactly one intended handler) and the
    // outcome events they produce (ADR-018). Every one of them carries the sagaId,
    // which for the live SALE flow IS the KR-12 order number.
    //
    // Each step has ONE outcome event carrying a SUCCEEDED/FAILED result rather than
    // a separate success type and failure type. Both are facts about the same step,
    // the orchestrator's next move is decided by the same state guard either way, and
    // one destination per step keeps the binding table (and the recovery job's notion
    // of "what am I waiting for") a single row per step instead of two.
    // ---------------------------------------------------------------------------

    /** order-service → account-service: is this billing account sellable into? */
    public static final String CHECK_SALE_ACCOUNT = "crm.account.check-sale-account";
    public static final int CHECK_SALE_ACCOUNT_V = 1;

    /** account-service → order-service: the outcome of the above, with the authoritative customerNumber. */
    public static final String SALE_ACCOUNT_CHECKED = "crm.account.sale-account-checked";
    public static final int SALE_ACCOUNT_CHECKED_V = 1;

    /** order-service → product-service: create this sale's products as PNDG. */
    public static final String PREPARE_SALE_PRODUCTS = "crm.product.prepare-sale-products";
    public static final int PREPARE_SALE_PRODUCTS_V = 1;

    /** product-service → order-service: the products exist (PNDG) with ids and amounts, or did not. */
    public static final String SALE_PRODUCTS_PREPARED = "crm.product.sale-products-prepared";
    public static final int SALE_PRODUCTS_PREPARED_V = 1;

    /** order-service → account-service: link this sale's products to the billing account. */
    public static final String LINK_SALE_PRODUCTS = "crm.account.link-sale-products";
    public static final int LINK_SALE_PRODUCTS_V = 1;

    /** account-service → order-service: the involvement rows exist, or the link failed. */
    public static final String SALE_PRODUCTS_LINKED = "crm.account.sale-products-linked";
    public static final int SALE_PRODUCTS_LINKED_V = 1;

    /** order-service → product-service: promote this sale's PNDG products to ACTV. */
    public static final String ACTIVATE_SALE_PRODUCTS = "crm.product.activate-sale-products";
    public static final int ACTIVATE_SALE_PRODUCTS_V = 1;

    /** product-service → order-service: the products are ACTV, or activation failed. */
    public static final String SALE_PRODUCTS_ACTIVATED = "crm.product.sale-products-activated";
    public static final int SALE_PRODUCTS_ACTIVATED_V = 1;

    /** order-service → product-service: passivate the PNDG products owned by THIS saga. */
    public static final String COMPENSATE_SALE_PRODUCTS = "crm.product.compensate-sale-products";
    public static final int COMPENSATE_SALE_PRODUCTS_V = 1;

    /** product-service → order-service: the saga's products were passivated, or could not be. */
    public static final String SALE_PRODUCTS_COMPENSATED = "crm.product.sale-products-compensated";
    public static final int SALE_PRODUCTS_COMPENSATED_V = 1;

    /** order-service → account-service: soft-remove the involvement rows owned by THIS saga. */
    public static final String COMPENSATE_SALE_INVOLVEMENTS = "crm.account.compensate-sale-involvements";
    public static final int COMPENSATE_SALE_INVOLVEMENTS_V = 1;

    /** account-service → order-service: the saga's involvement rows were removed, or could not be. */
    public static final String SALE_INVOLVEMENTS_COMPENSATED = "crm.account.sale-involvements-compensated";
    public static final int SALE_INVOLVEMENTS_COMPENSATED_V = 1;

    /**
     * order-service → anyone: the sale reached core consistency (ADR-018 §7).
     *
     * <p>The ONE terminal fact reactions subscribe to, and the only message in this
     * catalog that is choreography rather than orchestration: it is published after the
     * saga is COMPLETED, so a consumer that fails cannot fail the sale.
     */
    public static final String SALE_COMPLETED = "crm.sale.sale-completed";
    public static final int SALE_COMPLETED_V = 1;

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
            case CHECK_SALE_ACCOUNT -> CHECK_SALE_ACCOUNT_V;
            case SALE_ACCOUNT_CHECKED -> SALE_ACCOUNT_CHECKED_V;
            case PREPARE_SALE_PRODUCTS -> PREPARE_SALE_PRODUCTS_V;
            case SALE_PRODUCTS_PREPARED -> SALE_PRODUCTS_PREPARED_V;
            case LINK_SALE_PRODUCTS -> LINK_SALE_PRODUCTS_V;
            case SALE_PRODUCTS_LINKED -> SALE_PRODUCTS_LINKED_V;
            case ACTIVATE_SALE_PRODUCTS -> ACTIVATE_SALE_PRODUCTS_V;
            case SALE_PRODUCTS_ACTIVATED -> SALE_PRODUCTS_ACTIVATED_V;
            case COMPENSATE_SALE_PRODUCTS -> COMPENSATE_SALE_PRODUCTS_V;
            case SALE_PRODUCTS_COMPENSATED -> SALE_PRODUCTS_COMPENSATED_V;
            case COMPENSATE_SALE_INVOLVEMENTS -> COMPENSATE_SALE_INVOLVEMENTS_V;
            case SALE_INVOLVEMENTS_COMPENSATED -> SALE_INVOLVEMENTS_COMPENSATED_V;
            case SALE_COMPLETED -> SALE_COMPLETED_V;
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
