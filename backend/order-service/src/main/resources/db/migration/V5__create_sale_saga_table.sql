-- The SALE saga's persisted state (ADR-018 §4). Project addition, not a workbook
-- table -- like order_number_seq (V1), idempotency_key (V3) and the outbox/inbox
-- pair (V4).
--
-- OWNERSHIP: order-service and nothing else. There is no cross-service saga
-- database and no cross-database foreign key (ADR-002/ADR-017 §8): product-service
-- and account-service learn what to do from COMMANDS and answer with EVENTS; they
-- never read this table, and nothing here references their databases.
--
-- WHY A TABLE AND NOT AN IN-MEMORY STATE MACHINE: a sale that is waiting for a reply
-- has to survive a restart of the process that is waiting. An in-memory orchestrator
-- loses every in-flight sale on deploy, and the resulting orders are then stuck in
-- MIDLWARE with nothing in the system that knows what they were waiting for. That is
-- exactly the "which sales are stuck at step 3?" question ADR-017 §1 chose
-- orchestration to be able to answer.

CREATE TABLE sale_saga (
    -- sagaId IS the KR-12 order number (ADR-018 §3). The order now exists before
    -- Submit, so the sale has a stable business identity of its own and no longer
    -- needs to borrow the client's Idempotency-Key as its correlation id -- which
    -- also means an HTTP retry with a NEW key can no longer look like a new saga.
    saga_id             VARCHAR(10)   PRIMARY KEY,
    -- Stored a second time, deliberately: the equality is the whole identity
    -- decision, and a CHECK is the only form of documentation a future migration
    -- cannot silently contradict.
    order_number        VARCHAR(10)   NOT NULL UNIQUE REFERENCES cust_ord (order_number),
    CONSTRAINT ck_sale_saga_id_is_order_number CHECK (saga_id = order_number),

    account_number      VARCHAR(10)   NOT NULL,   -- external ref: KR-11; no FK by design
    -- Filled from the ACCOUNT's own answer (the account-check reply), never from the
    -- browser -- the same rule the synchronous path enforces (ADR-015 §5.9).
    customer_number     BIGINT,

    current_state       VARCHAR(40)   NOT NULL,
    -- Optimistic lock. Two deliveries of two different replies for one saga can
    -- otherwise both read the same row and both advance it; the version makes the
    -- loser roll back and be redelivered, at which point the state guard sees the
    -- world as it actually is.
    version             BIGINT        NOT NULL DEFAULT 0,

    -- The submitted basket, exactly as accepted at Submit. Kept because the saga must
    -- be able to REISSUE the product-preparation command after a restart, and the
    -- characteristic values it carries are not stored anywhere else in order_db
    -- (cust_ord_item holds offer id, product id and amount -- not characteristics).
    request_snapshot    TEXT          NOT NULL,
    -- The ids product-service reported, as a JSON array. The saga needs them to
    -- address the activation and compensation commands; cust_ord_item carries the
    -- same ids, but reading them back through the order aggregate to build a
    -- compensation command would make compensation depend on the very write that may
    -- have been what failed.
    product_ids         TEXT,

    last_message_id     VARCHAR(64),    -- the reply that last moved this saga (causation)
    last_message_type   VARCHAR(128),
    correlation_id      VARCHAR(64),    -- the X-Correlation-Id of the Submit request

    retry_count         INTEGER       NOT NULL DEFAULT 0,
    -- When the recovery job may reissue the outstanding command. NULL in a terminal
    -- state -- a terminal saga is never reissued, which is what keeps "the same order
    -- is never fulfilled twice" true after a restart.
    next_retry_at       TIMESTAMPTZ,

    -- Operator-facing code and the SAFE, client-facing message key. Two columns
    -- because they are for two different audiences: failure_code may name the step
    -- that failed, failure_message_key is what the status endpoint returns and must
    -- never be an infrastructure exception string (ADR-018 §8).
    failure_code        VARCHAR(64),
    failure_message_key VARCHAR(64),

    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    completed_at        TIMESTAMPTZ
);

-- "How many sagas are in each state" is a metric read on every scrape (ADR-018 §9),
-- and "which sagas are stuck" is the recovery job's query.
CREATE INDEX idx_sale_saga_state ON sale_saga (current_state);

-- The recovery job's queue: only a saga that is actually waiting for a reply has a
-- due time, so the index is partial and stays small however many completed sales
-- accumulate.
CREATE INDEX idx_sale_saga_due ON sale_saga (next_retry_at)
    WHERE next_retry_at IS NOT NULL;
