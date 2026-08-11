-- Sale ownership on the involvement projection (ADR-018 §7).
--
-- WHY: the asynchronous SALE saga promotes products to ACTV only AFTER the involvement
-- rows exist, so an activation that fails terminally has an involvement to undo. Undoing
-- it safely requires knowing WHICH sale wrote which row -- without that, a compensation
-- handed a product id could passivate an involvement created by a different sale, or by
-- the V3 activation seed, and the AC-ACCT-04-03 delete guard would start answering
-- questions about rows nobody meant to touch.
--
-- WHAT IT IS NOT: a user-facing unlink feature. There is no endpoint that removes an
-- involvement by product id, and this column adds none. It is an ownership GUARD for one
-- internal, saga-scoped compensation command, and the compensation matches on it
-- exclusively -- a row with a NULL value can never be compensated by any saga.
--
-- NULLABLE, and every existing row keeps NULL:
--   * the V3 activation seed and every involvement written by the synchronous ADR-016 §5
--     orchestration predate the saga and belong to no sale operation;
--   * a backfill would have to invent an owner for them, and an invented owner is exactly
--     what would make the guard unsafe.
-- NULL therefore means "not owned by any saga", which is the honest and the SAFE default.

ALTER TABLE cust_acct_prod_invl
    ADD COLUMN sale_operation_id VARCHAR(64);

COMMENT ON COLUMN cust_acct_prod_invl.sale_operation_id IS
    'The SALE saga that created this row (= the KR-12 order number). NULL for rows written '
    'by the seed or by the synchronous orchestration. Ownership guard for saga-scoped '
    'compensation only -- never a user-facing unlink (ADR-018 §7).';

-- The compensation's only query: "the rows THIS sale created for THIS account". Partial,
-- because rows with no owner are never selected by it and would be dead weight in the
-- index.
CREATE INDEX idx_invl_sale_operation ON cust_acct_prod_invl (sale_operation_id)
    WHERE sale_operation_id IS NOT NULL;
