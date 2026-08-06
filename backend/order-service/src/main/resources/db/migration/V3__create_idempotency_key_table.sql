-- Idempotency foundation for POST /api/orders (KR-7 idempotency addendum, ADR-016
-- addendum 2026-08-06). NOT a workbook table -- a project addition, exactly like
-- order_number_seq (V1).
--
-- One row per client-supplied Idempotency-Key. The row is inserted FIRST, before any
-- order-domain write, in its own short transaction (IdempotencyPersistence) -- the
-- UNIQUE constraint on idempotency_key is the FINAL concurrency guard: two concurrent
-- requests for the same key race on this INSERT, and only one can ever win it. The
-- loser is told so (409), never silently allowed to run the orchestration twice.
--
-- response_status/response_body are a snapshot of the ACTUAL response the filter sent
-- for this key's first request -- success or a handled failure alike (ADR-016 fails
-- closed on every path already; replaying a failure verbatim is cheaper and safer
-- than re-running a three-service orchestration a second time for the same answer).
-- order_number is filled only when the request actually produced one (HTTP 201);
-- NULL otherwise -- a request that failed before step 1 completed never had a number
-- to record, and one that failed after does not lose its trail (the order row itself,
-- CANCELLED, is order_db's own record of it -- ADR-016 §4.6).
CREATE TABLE idempotency_key (
    id                BIGSERIAL     PRIMARY KEY,
    idempotency_key   VARCHAR(64)   NOT NULL UNIQUE,
    request_hash      VARCHAR(64)   NOT NULL,    -- SHA-256 hex of the normalized request body
    status            VARCHAR(20)   NOT NULL,    -- IN_PROGRESS | COMPLETED
    order_number      VARCHAR(10),               -- filled only on a 201 response
    response_status   INTEGER,                   -- NULL while IN_PROGRESS
    response_body     TEXT,                      -- NULL while IN_PROGRESS
    created_date      TIMESTAMPTZ   NOT NULL,
    updated_date      TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ   NOT NULL      -- retention horizon; gates replay eligibility only
);

CREATE INDEX idx_idempotency_key_expires ON idempotency_key (expires_at);
