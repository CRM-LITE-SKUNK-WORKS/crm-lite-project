-- Product-creation idempotency (ADR-015 idempotency addendum, 2026-08-06). Project
-- additions, not workbook tables/columns -- exactly the same class of deviation as
-- PROD.transaction_id staying unused (V1) or the two-phase PNDG protocol itself.
--
-- sale_operation is the replay ledger for POST /api/products: order-service forwards
-- its own Idempotency-Key as saleOperationId (ADR-016 idempotency addendum), and a
-- second POST carrying an id already present here returns the FIRST call's response
-- unchanged instead of creating a second set of PNDG products. The UNIQUE constraint
-- on sale_operation_id is the concurrency guard, not an in-memory check (requirement:
-- "use a database unique constraint, not only an in-memory existence check").
CREATE TABLE sale_operation (
    id                 BIGSERIAL     PRIMARY KEY,
    sale_operation_id  VARCHAR(64)   NOT NULL UNIQUE,
    response_snapshot  TEXT,                          -- NULL until the creation that reserved it completes
    main_product_id    BIGINT,
    created_date       TIMESTAMPTZ   NOT NULL,
    updated_date       TIMESTAMPTZ
);

-- prod.sale_operation_id tags every product with the operation that created it -- the
-- ownership key the sale-scoped compensation endpoint (POST /api/products/compensate)
-- checks before touching a row, so it can never passivate a product that belongs to a
-- DIFFERENT sale operation. Nullable: seed fixtures and any product created before
-- this migration have no operation to record.
ALTER TABLE prod ADD COLUMN sale_operation_id VARCHAR(64);
CREATE INDEX idx_prod_sale_operation ON prod (sale_operation_id);
