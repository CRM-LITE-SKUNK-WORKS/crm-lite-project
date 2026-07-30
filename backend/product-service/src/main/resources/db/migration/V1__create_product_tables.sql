-- product_db baseline aligned with CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx
-- (FR v8-1 Final, 23.07.2026 — FR-PROD-01..02, read-only Phase A slice).
--
-- SHARED CATALOG RULE (ADR-002): status_id columns are EXTERNAL references to the
-- central GNL_ST catalog owned by lookup-service (1=ACTV, 2=PASV, GENERAL domain),
-- and prod_spec.service_type_id / prod_catal.catalog_type_id are EXTERNAL
-- references to the central GNL_TP catalog (SERVICE_TYPE 10=INTERNET, 11=RESOURCE,
-- 12=ACTIVATION; CATALOG_TYPE 5=INDVCAT, 6=CORPCAT). This database deliberately
-- contains NO gnl_st/gnl_tp tables, NO local copies of their rows, and NO
-- cross-database foreign keys.
--
-- EXTERNAL BUSINESS REFERENCES: prod.service_address_id references a
-- customer-service address (customer_db addr.id, the id its public address API
-- exposes) — no FK by design. There is deliberately NO customer or account column
-- anywhere in this schema: the product ↔ billing-account link lives ONLY in
-- account_db.cust_acct_prod_invl, which is written exclusively by account-service
-- (ADR-013 §5) — product-service composes over its API and never touches account_db.
--
-- LOCAL FOREIGN KEYS: parent_prod_id / parent_offer_id are local self-references;
-- product_spec_id / product_offer_id / campaign_id / product_catalog_id /
-- product_spec_char_id / product_id reference local tables — all carry normal FKs.
--
-- COLUMNS THE FR IS SILENT ABOUT (kept schema-faithful, nullable, unused by the
-- Phase A read APIs): prod_spec.is_dev (workbook values 0/1, meaning undefined by
-- any FR/AC) and prod.transaction_id (empty in every workbook row; presumably a
-- future order/business-interaction reference — the order domain does not exist yet).
--
-- SOFT DELETE INVARIANT: active row <=> status_id = ACTV(1) AND deleted_date IS NULL.
-- Catalog reads (offers/campaigns) filter on it; the FR-PROD-01 product list does
-- NOT — passive products stay visible with Status "Passive" (AC-PROD-01-03).

CREATE TABLE prod_spec (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(250),
    service_type_id BIGINT       NOT NULL,   -- external ref: central GNL_TP SERVICE_TYPE; no FK by design
    is_dev          SMALLINT,                -- workbook column; FR silent on meaning, kept nullable
    status_id       BIGINT       NOT NULL,
    created_date    TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(64)  NOT NULL,
    updated_date    TIMESTAMPTZ,
    updated_by      VARCHAR(64),
    deleted_date    TIMESTAMPTZ,
    deleted_by      VARCHAR(64)
);

CREATE TABLE prod_ofr (
    id                        BIGSERIAL     PRIMARY KEY,
    product_spec_id           BIGINT        NOT NULL REFERENCES prod_spec (id),  -- local FK
    name                      VARCHAR(100)  NOT NULL,
    description               VARCHAR(250),
    parent_offer_id           BIGINT        REFERENCES prod_ofr (id),            -- local self-FK
    product_offer_total_price NUMERIC(12,2),
    status_id                 BIGINT        NOT NULL,
    created_date              TIMESTAMPTZ   NOT NULL,
    created_by                VARCHAR(64)   NOT NULL,
    updated_date              TIMESTAMPTZ,
    updated_by                VARCHAR(64),
    deleted_date              TIMESTAMPTZ,
    deleted_by                VARCHAR(64)
);

CREATE TABLE cmpg (
    id                  BIGSERIAL    PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    -- The PUBLIC campaign identifier (e.g. CMP-ADSL-01): API responses expose this,
    -- never the internal id.
    campaign_code       VARCHAR(20)  NOT NULL UNIQUE,
    description         VARCHAR(250),
    activation_end_date TIMESTAMPTZ,
    status_id           BIGINT       NOT NULL,
    created_date        TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(64)  NOT NULL,
    updated_date        TIMESTAMPTZ,
    updated_by          VARCHAR(64),
    deleted_date        TIMESTAMPTZ,
    deleted_by          VARCHAR(64)
);

-- Campaign membership. The campaign has NO price column of its own: the campaign
-- price is DERIVED as the sum of its member offers' prices (recorded deviation
-- decision — see V2 header and docs/requirements/document-delta.md).
CREATE TABLE cmpg_prod_ofr (
    id               BIGSERIAL   PRIMARY KEY,
    campaign_id      BIGINT      NOT NULL REFERENCES cmpg (id),      -- local FK
    product_offer_id BIGINT      NOT NULL REFERENCES prod_ofr (id),  -- local FK
    is_main          BOOLEAN     NOT NULL DEFAULT FALSE,
    short_code       VARCHAR(20) NOT NULL DEFAULT 'CMPG_OFR',
    status_id        BIGINT      NOT NULL,
    created_date     TIMESTAMPTZ NOT NULL,
    created_by       VARCHAR(64) NOT NULL,
    updated_date     TIMESTAMPTZ,
    updated_by       VARCHAR(64),
    deleted_date     TIMESTAMPTZ,
    deleted_by       VARCHAR(64)
);

CREATE INDEX idx_cmpg_prod_ofr_campaign ON cmpg_prod_ofr (campaign_id);

-- Product instances. NOTE: no customer_number / account_number column — the
-- account link is account_db.cust_acct_prod_invl only (ADR-013 §5, see header).
CREATE TABLE prod (
    id                 BIGSERIAL    PRIMARY KEY,
    parent_prod_id     BIGINT       REFERENCES prod (id),             -- local self-FK
    product_offer_id   BIGINT       NOT NULL REFERENCES prod_ofr (id),
    product_spec_id    BIGINT       NOT NULL REFERENCES prod_spec (id),
    name               VARCHAR(100) NOT NULL,
    description        VARCHAR(250),
    transaction_id     BIGINT,                                        -- workbook column; FR silent, kept nullable
    campaign_id        BIGINT       REFERENCES cmpg (id),             -- local FK; NULL = bought outside a campaign
    service_start_date DATE,
    -- Only filled on the MAIN product of an installation; child products display
    -- their parent's service address (FR-PROD-02 presentation rule).
    service_address_id BIGINT,                                        -- external ref: customer-service address id; no FK by design
    status_id          BIGINT       NOT NULL,
    created_date       TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(64)  NOT NULL,
    updated_date       TIMESTAMPTZ,
    updated_by         VARCHAR(64),
    deleted_date       TIMESTAMPTZ,
    deleted_by         VARCHAR(64)
);

CREATE INDEX idx_prod_offer    ON prod (product_offer_id);
CREATE INDEX idx_prod_parent   ON prod (parent_prod_id);
CREATE INDEX idx_prod_campaign ON prod (campaign_id);

-- Characteristic model (schema + seed only in Phase A; consumed by the FR-SALE
-- §2.7 configuration screens later — no Phase A endpoint reads these).
CREATE TABLE prod_spec_char (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    data_type    VARCHAR(10)  NOT NULL CHECK (data_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'DATE')),
    description  VARCHAR(250),
    status_id    BIGINT       NOT NULL,
    created_date TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(64)  NOT NULL,
    updated_date TIMESTAMPTZ,
    updated_by   VARCHAR(64),
    deleted_date TIMESTAMPTZ,
    deleted_by   VARCHAR(64)
);

CREATE TABLE prod_spec_char_use (
    id                  BIGSERIAL   PRIMARY KEY,
    product_spec_id     BIGINT      NOT NULL REFERENCES prod_spec (id),       -- local FK
    product_spec_char_id BIGINT     NOT NULL REFERENCES prod_spec_char (id),  -- local FK
    is_mandatory        BOOLEAN     NOT NULL DEFAULT FALSE,
    status_id           BIGINT      NOT NULL,
    created_date        TIMESTAMPTZ NOT NULL,
    created_by          VARCHAR(64) NOT NULL,
    updated_date        TIMESTAMPTZ,
    updated_by          VARCHAR(64),
    deleted_date        TIMESTAMPTZ,
    deleted_by          VARCHAR(64)
);

-- Characteristic values of a product instance: val is a SINGLE string column for
-- every data_type (workbook model) — typing/format rules live in the application
-- layer when §2.7 arrives.
CREATE TABLE prod_char_val (
    id                   BIGSERIAL    PRIMARY KEY,
    product_id           BIGINT       NOT NULL REFERENCES prod (id),           -- local FK
    product_spec_char_id BIGINT       NOT NULL REFERENCES prod_spec_char (id), -- local FK
    val                  VARCHAR(250) NOT NULL,
    status_id            BIGINT       NOT NULL,
    created_date         TIMESTAMPTZ  NOT NULL,
    created_by           VARCHAR(64)  NOT NULL,
    updated_date         TIMESTAMPTZ,
    updated_by           VARCHAR(64),
    deleted_date         TIMESTAMPTZ,
    deleted_by           VARCHAR(64)
);

CREATE TABLE prod_catal (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(250),
    catalog_type_id BIGINT       NOT NULL,   -- external ref: central GNL_TP CATALOG_TYPE; no FK by design
    short_code      VARCHAR(20)  NOT NULL,
    status_id       BIGINT       NOT NULL,
    created_date    TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(64)  NOT NULL,
    updated_date    TIMESTAMPTZ,
    updated_by      VARCHAR(64),
    deleted_date    TIMESTAMPTZ,
    deleted_by      VARCHAR(64)
);

CREATE TABLE prod_catal_prod_ofr (
    id                 BIGSERIAL   PRIMARY KEY,
    product_catalog_id BIGINT      NOT NULL REFERENCES prod_catal (id),  -- local FK
    product_offer_id   BIGINT      NOT NULL REFERENCES prod_ofr (id),    -- local FK
    short_code         VARCHAR(20) NOT NULL DEFAULT 'CATAL_OFR',
    status_id          BIGINT      NOT NULL,
    created_date       TIMESTAMPTZ NOT NULL,
    created_by         VARCHAR(64) NOT NULL,
    updated_date       TIMESTAMPTZ,
    updated_by         VARCHAR(64),
    deleted_date       TIMESTAMPTZ,
    deleted_by         VARCHAR(64)
);

CREATE INDEX idx_prod_catal_prod_ofr_catalog ON prod_catal_prod_ofr (product_catalog_id);
