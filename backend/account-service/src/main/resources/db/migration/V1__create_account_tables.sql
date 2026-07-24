-- account_db baseline aligned with CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx and
-- ADR-013/ADR-014 (FR v8-1 Final, 23.07.2026 — KR-11 + FR-ACCT-01..04).
--
-- SHARED CATALOG RULE (ADR-002): status_id columns are EXTERNAL references to the
-- central GNL_ST catalog owned by lookup-service (1=ACTV, 2=PASV, GENERAL domain).
-- This database deliberately contains NO gnl_st/gnl_tp tables, NO local copies of
-- their rows, and NO cross-database foreign keys.
--
-- EXTERNAL BUSINESS REFERENCES (ADR-013): cust_acct.customer_number is the PUBLIC
-- business customer number (never the internal cust.id — recorded workbook
-- deviation); cust_acct.address_id references a customer-service address by the id
-- its public API exposes; cust_acct_prod_invl.product_id references a future
-- product-service product. None of these carry FKs by design.
--
-- SOFT DELETE INVARIANT: active row <=> status_id = ACTV(1) AND deleted_date IS NULL.
-- FR-ACCT-04: deletion is passivation — there is NO physical delete and no cascade
-- that could destroy history (AC-ACCT-04-03).

-- Local, account-domain-owned type catalog (NOT a shared GNL catalog): contract rows
-- id 1 = code 223 (Customer Account), id 2 = code 224 (Billing Account). IDs are
-- contract-fixed by the seed and must never be renumbered.
CREATE TABLE acct_tp (
    id                BIGINT       PRIMARY KEY,
    account_type_code VARCHAR(8)   NOT NULL UNIQUE,
    name              VARCHAR(100) NOT NULL,
    description       VARCHAR(250),
    short_code        VARCHAR(20)  NOT NULL,
    status_id         BIGINT       NOT NULL,
    created_date      TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(64)  NOT NULL,
    updated_date      TIMESTAMPTZ,
    updated_by        VARCHAR(64),
    deleted_date      TIMESTAMPTZ,
    deleted_by        VARCHAR(64)
);

CREATE TABLE cust_acct (
    id              BIGSERIAL    PRIMARY KEY,
    customer_number BIGINT       NOT NULL,                          -- external ref: public customer number; no FK by design
    account_type_id BIGINT       NOT NULL REFERENCES acct_tp (id),  -- local FK
    -- KR-11 (ADR-014): 10-digit [T][YY][SSSSSS][C] as VARCHAR, NEVER a numeric type.
    -- UNIQUE over ALL rows including soft-deleted ones: a passivated account never
    -- releases its number for reuse.
    account_number  VARCHAR(10)  NOT NULL UNIQUE,
    account_name    VARCHAR(100) NOT NULL,
    description     VARCHAR(250),
    address_id      BIGINT       NOT NULL,                          -- external ref: customer-service address id; no FK by design
    status_id       BIGINT       NOT NULL,
    created_date    TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(64)  NOT NULL,
    updated_date    TIMESTAMPTZ,
    updated_by      VARCHAR(64),
    deleted_date    TIMESTAMPTZ,
    deleted_by      VARCHAR(64)
);

-- List/lookup support: accounts are always read per customer (FR-ACCT-01).
CREATE INDEX idx_cust_acct_customer ON cust_acct (customer_number, account_type_id);

-- K-8 (ADR-013 §4): at most ONE non-deleted 223 Customer Account per customer.
-- account_type_id 1 is the contract-fixed acct_tp row for code 223; the predicate
-- deliberately does not constrain 224 rows (a customer may hold many Billing
-- Accounts).
CREATE UNIQUE INDEX ux_cust_acct_single_223
    ON cust_acct (customer_number)
    WHERE account_type_id = 1 AND deleted_date IS NULL;

-- Account-product involvement projection (ADR-013 §5): owned and written ONLY by
-- account-service; the sole source of truth for the AC-ACCT-04-03 delete guard.
CREATE TABLE cust_acct_prod_invl (
    id                  BIGSERIAL   PRIMARY KEY,
    customer_account_id BIGINT      NOT NULL REFERENCES cust_acct (id),  -- local FK
    product_id          BIGINT      NOT NULL,                            -- external ref: future product-service; no FK by design
    short_code          VARCHAR(20) NOT NULL DEFAULT 'ACCT_PROD',
    status_id           BIGINT      NOT NULL,
    created_date        TIMESTAMPTZ NOT NULL,
    created_by          VARCHAR(64) NOT NULL,
    updated_date        TIMESTAMPTZ,
    updated_by          VARCHAR(64),
    deleted_date        TIMESTAMPTZ,
    deleted_by          VARCHAR(64)
);

CREATE INDEX idx_prod_invl_account ON cust_acct_prod_invl (customer_account_id);

-- KR-11 sequence state (ADR-014; project addition — the workbook has no sequence
-- table). INVARIANT: next_value is the NEXT sequence value that will be issued for
-- that (segment, year). Allocation is a single race-safe
-- INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING statement.
CREATE TABLE acct_number_seq (
    segment    SMALLINT NOT NULL,
    seq_year   INTEGER  NOT NULL,   -- full year (e.g. 2026); YY is derived for display
    next_value INTEGER  NOT NULL CHECK (next_value >= 100000),
    PRIMARY KEY (segment, seq_year)
);
