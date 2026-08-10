-- Reliable-messaging foundation for account_db (ADR-017 §8). Project addition, not a
-- workbook table -- exactly like account_number_seq (V1).
--
-- These tables are SERVICE-LOCAL by rule. order-service, product-service and
-- account-service each get their own copy in their OWN database; no service ever reads
-- another's outbox/inbox, and there is no cross-database foreign key anywhere (ADR-002).
-- The three copies are structurally identical so one Debezium connector configuration
-- shape works for all of them (ADR-017 §9).
--
-- NOTHING publishes from this table in this branch: crm.messaging.outbox.enabled and
-- crm.messaging.outbox.relay.enabled both ship false (ADR-017 §11), and the involvement
-- command that is the SALE's commit point (ADR-013 §8) still runs synchronously. The
-- tables exist so the cutover is a configuration change.
--
-- cust_acct_prod_invl stays account-service-owned (ADR-013): a future order-service saga
-- step will link products by SENDING A MESSAGE consumed here, never by writing account_db.

-- ---------------------------------------------------------------------------
-- OUTBOX -- messages this service has decided to send.
--
-- Written by the SAME local transaction as the business change that justifies the
-- message (OutboxRecorder is Propagation.MANDATORY, so this is enforced by the
-- container, not by convention). That is what makes "order committed but event lost"
-- and "event published but order rolled back" both unreachable without a distributed
-- transaction and without the broker being up at write time.
-- ---------------------------------------------------------------------------
CREATE TABLE outbox_message (
    -- The envelope's messageId, not a surrogate key: the publisher, the receiving
    -- inbox's uniqueness guard and an operator grepping logs all name the same
    -- message by the same string.
    message_id       VARCHAR(64)   PRIMARY KEY,
    message_type     VARCHAR(128)  NOT NULL,    -- e.g. crm.order.order-submitted
    schema_version   INTEGER       NOT NULL,
    aggregate_type   VARCHAR(64)   NOT NULL,    -- Debezium Outbox Event Router routes on this
    aggregate_id     VARCHAR(64)   NOT NULL,    -- e.g. the KR-12 order number
    destination      VARCHAR(160)  NOT NULL,    -- logical name (Destinations), never a raw topic
    partition_key    VARCHAR(64)   NOT NULL,    -- sagaId, else aggregate_id (ADR-017 §7.6)
    saga_id          VARCHAR(64),               -- the SALE saga reuses the Idempotency-Key
    correlation_id   VARCHAR(64),               -- the SAME X-Correlation-Id already in the MDC
    causation_id     VARCHAR(64),               -- message that caused this one; NULL if a request did
    envelope         TEXT          NOT NULL,    -- the complete EventEnvelope as JSON
    occurred_at      TIMESTAMPTZ   NOT NULL,    -- when the FACT happened, not when it was sent
    published_at     TIMESTAMPTZ,               -- NULL = still owed to the broker
    publish_attempts INTEGER       NOT NULL DEFAULT 0,
    last_error       TEXT
);

-- The relay's queue AND both gauges (backlog count, oldest unpublished age) read exactly
-- this predicate, so the index is partial: published rows are dead weight for every query
-- that matters and retention is about to delete them anyway.
CREATE INDEX idx_outbox_unpublished ON outbox_message (occurred_at)
    WHERE published_at IS NULL;

-- Retention deletes by published_at (ADR-017 §10); unpublished rows are never eligible.
CREATE INDEX idx_outbox_published_at ON outbox_message (published_at)
    WHERE published_at IS NOT NULL;

-- ---------------------------------------------------------------------------
-- INBOX -- messages this service has already processed.
--
-- The claim row is inserted in the SAME transaction as the business change the message
-- causes, so a redelivery either finds the claim (no-op) or finds nothing (genuinely
-- unprocessed). A claim committed separately would swallow every message whose handler
-- failed.
-- ---------------------------------------------------------------------------
CREATE TABLE inbox_message (
    id             BIGSERIAL     PRIMARY KEY,
    message_id     VARCHAR(64)   NOT NULL,
    -- <consumer-service>.<destination>. Part of the key, not just data: two different
    -- services must each process a message once, while ONE service must never process it
    -- twice.
    consumer_group VARCHAR(160)  NOT NULL,
    message_type   VARCHAR(128)  NOT NULL,
    schema_version INTEGER       NOT NULL,
    saga_id        VARCHAR(64),
    correlation_id VARCHAR(64),
    processed_at   TIMESTAMPTZ   NOT NULL
);

-- THE duplicate guard (ADR-017 §8.2). The claim is an INSERT whose failure means
-- duplicate -- not a SELECT-then-INSERT, which leaves a race two concurrent consumers
-- eventually hit. Everything else in the inbox path is an optimization on top of this
-- constraint.
CREATE UNIQUE INDEX uq_inbox_message_id_group ON inbox_message (message_id, consumer_group);
