-- order_db baseline (ADR-016 §2). Physical names are lowercase; logical workbook
-- names are BSN_INTER / CUST_ORD / CUST_ORD_ITEM.
--
-- ADR-002: there is NO local gnl_st / gnl_tp table and no cross-database FK.
-- status_id and bsn_inter_type_id store the CENTRAL catalog contract IDs as
-- external references (GNL_ST 1=ACTV, 4=MIDLWARE, 5=CANCELLED; GNL_TP 7=NEWSALE).
-- An integration test asserts over information_schema that no gnl_* table exists.
--
-- ADR-016 §2.3: the workbook's cust_ord.customer_id and bsn_inter.customer_account_id
-- become customer_number and customer_account_number — internal ids never leave the
-- service that owns them, so the public business number (customer number, KR-11
-- account number) is the only stable cross-service reference. FK-less by design,
-- exactly as account-service treats customer_number/address_id (ADR-013 §2.3/2.4).
--
-- ADR-016 §5.2: cust_ord_item.product_id and both amount columns are NULLable. The
-- workbook's CUST_ORD_ITEM carries product_id, but the product does not exist yet
-- when the order header is written (the sale's step 1 is a purely local transaction,
-- step 2 creates the products). They are filled by a second local transaction.
--
-- SOFT DELETE INVARIANT: nothing here is ever physically deleted. A compensated sale
-- becomes CANCELLED (5) and keeps its number forever (ADR-016 §4.6).

-- Business interaction: the sale "event" a customer order belongs to.
-- bsn_inter_type_id exists as a column but only NEWSALE(7) is ever written this
-- phase — TRANSFER(8)/CANCEL(9) have no FR (ADR-016 §6/§8.2). Keeping the column
-- schema-faithful means a future transfer flow needs no migration.
CREATE TABLE bsn_inter (
    id                      BIGSERIAL    PRIMARY KEY,
    customer_account_number VARCHAR(10)  NOT NULL,   -- external ref: KR-11 account number; no FK by design
    bsn_inter_type_id       BIGINT       NOT NULL,   -- external ref: central GNL_TP
    description             VARCHAR(250),
    status_id               BIGINT       NOT NULL,   -- external ref: central GNL_ST
    created_date            TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(64)  NOT NULL,
    updated_date            TIMESTAMPTZ,
    updated_by              VARCHAR(64),
    deleted_date            TIMESTAMPTZ,
    deleted_by              VARCHAR(64)
);

-- Order header. order_number is the KR-12 public identifier: VARCHAR(10), Luhn
-- check digit, immutable, never reused — never a numeric column type, for the same
-- reasons ADR-014 §1 gives for account numbers (arithmetic on an identifier, and a
-- future leading 0 segment).
CREATE TABLE cust_ord (
    id              BIGSERIAL      PRIMARY KEY,
    order_number    VARCHAR(10)    NOT NULL UNIQUE,
    customer_number BIGINT         NOT NULL,          -- external ref: public business number; no FK
    bsn_inter_id    BIGINT         NOT NULL REFERENCES bsn_inter (id),   -- local FK
    -- Project addition, NOT in the workbook (ADR-016 §2.4): AC-SALE-01-12 shows a
    -- Total Amount, and reading the catalog price back later would silently rewrite
    -- the history of a past order whenever a price changes. Provisional values —
    -- the prices feeding it await analyst approval (document-delta P1/P5).
    total_amount    NUMERIC(12, 2),
    status_id       BIGINT         NOT NULL,          -- external ref: central GNL_ST (ORDER domain)
    created_date    TIMESTAMPTZ    NOT NULL,
    created_by      VARCHAR(64)    NOT NULL,
    updated_date    TIMESTAMPTZ,
    updated_by      VARCHAR(64),
    deleted_date    TIMESTAMPTZ,
    deleted_by      VARCHAR(64)
);

CREATE INDEX idx_cust_ord_customer ON cust_ord (customer_number);
CREATE INDEX idx_cust_ord_bsn_inter ON cust_ord (bsn_inter_id);

-- Order line: which offer was bought and which product instance it became.
CREATE TABLE cust_ord_item (
    id               BIGSERIAL     PRIMARY KEY,
    cust_ord_id      BIGINT        NOT NULL REFERENCES cust_ord (id),   -- local FK
    product_offer_id BIGINT        NOT NULL,        -- external ref: product_db prod_ofr; no FK by design
    product_id       BIGINT,                        -- external ref: product_db prod; NULL until step 3 (ADR-016 §5.2)
    amount           NUMERIC(12, 2),                -- price snapshot; see cust_ord.total_amount
    status_id        BIGINT        NOT NULL,        -- external ref: central GNL_ST
    created_date     TIMESTAMPTZ   NOT NULL,
    created_by       VARCHAR(64)   NOT NULL,
    updated_date     TIMESTAMPTZ,
    updated_by       VARCHAR(64),
    deleted_date     TIMESTAMPTZ,
    deleted_by       VARCHAR(64)
);

CREATE INDEX idx_cust_ord_item_order ON cust_ord_item (cust_ord_id);

-- KR-12 sequence state (ADR-016 §4.4; project addition — the workbook has no
-- sequence table, exactly as ADR-014 §3 recorded for accounts). INVARIANT:
-- next_value is the NEXT value that will be issued for that (segment, year).
-- Allocation is a single race-safe INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING.
--
-- A PostgreSQL native SEQUENCE per (segment, year) was rejected for the same reason
-- as in ADR-014: sequences cannot be created on demand transactionally without
-- DDL-in-transaction complexity, and their non-transactional semantics create gaps
-- the rule does not need.
CREATE TABLE order_number_seq (
    segment    SMALLINT NOT NULL,
    seq_year   INTEGER  NOT NULL,   -- full year (e.g. 2026); YY is derived for display
    next_value INTEGER  NOT NULL CHECK (next_value >= 100000),
    PRIMARY KEY (segment, seq_year)
);
