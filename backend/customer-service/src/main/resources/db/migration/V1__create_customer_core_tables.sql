-- customer_db baseline aligned with CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx.
--
-- SHARED CATALOG RULE (ADR-002): status_id, gender_id and party_type_id columns are
-- EXTERNAL references to the central GNL_ST / GNL_TP catalogs owned by lookup-service.
-- This database deliberately contains NO gnl_st/gnl_tp tables, NO local copies of
-- their rows, and NO cross-database foreign keys. The IDs are contract-immutable
-- (seeded explicitly by lookup-service) and are validated through the lookup API
-- before every write.
--
-- SOFT DELETE INVARIANT (ADR-002/003): active row  <=> status_id = ACTV(1) AND
-- deleted_date IS NULL. All "active" queries filter on both, entirely locally.

CREATE TABLE role (
    id           BIGINT       PRIMARY KEY,
    role_name    VARCHAR(100) NOT NULL,
    status_id    BIGINT       NOT NULL,
    created_date TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(64)  NOT NULL,
    updated_date TIMESTAMPTZ,
    updated_by   VARCHAR(64),
    deleted_date TIMESTAMPTZ,
    deleted_by   VARCHAR(64)
);

CREATE TABLE city (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    status_id    BIGINT       NOT NULL,
    created_date TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(64)  NOT NULL,
    updated_date TIMESTAMPTZ,
    updated_by   VARCHAR(64),
    deleted_date TIMESTAMPTZ,
    deleted_by   VARCHAR(64)
);

CREATE TABLE district (
    id           BIGSERIAL    PRIMARY KEY,
    city_id      BIGINT       NOT NULL REFERENCES city (id),
    name         VARCHAR(100) NOT NULL,
    status_id    BIGINT       NOT NULL,
    created_date TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(64)  NOT NULL,
    updated_date TIMESTAMPTZ,
    updated_by   VARCHAR(64),
    deleted_date TIMESTAMPTZ,
    deleted_by   VARCHAR(64)
);

CREATE TABLE party (
    id            BIGSERIAL   PRIMARY KEY,
    party_type_id BIGINT      NOT NULL, -- external GNL_TP reference (INDV); no FK by design
    status_id     BIGINT      NOT NULL,
    created_date  TIMESTAMPTZ NOT NULL,
    created_by    VARCHAR(64) NOT NULL,
    updated_date  TIMESTAMPTZ,
    updated_by    VARCHAR(64),
    deleted_date  TIMESTAMPTZ,
    deleted_by    VARCHAR(64)
);

CREATE TABLE ind (
    id             BIGSERIAL   PRIMARY KEY,
    party_id       BIGINT      NOT NULL UNIQUE REFERENCES party (id),
    first_name     VARCHAR(50) NOT NULL,
    middle_name    VARCHAR(50),
    last_name      VARCHAR(50) NOT NULL,
    father_name    VARCHAR(50),
    mother_name    VARCHAR(50),
    birth_date     DATE        NOT NULL,
    gender_id      BIGINT      NOT NULL, -- external GNL_TP reference (MALE/FEMALE); no FK by design
    -- ADR-003: globally and permanently unique across ALL rows, including
    -- soft-deleted ones. A passive customer does not release its Nationality ID.
    nationality_id VARCHAR(11) NOT NULL UNIQUE,
    status_id      BIGINT      NOT NULL,
    created_date   TIMESTAMPTZ NOT NULL,
    created_by     VARCHAR(64) NOT NULL,
    updated_date   TIMESTAMPTZ,
    updated_by     VARCHAR(64),
    deleted_date   TIMESTAMPTZ,
    deleted_by     VARCHAR(64)
);

CREATE TABLE party_role (
    id           BIGSERIAL   PRIMARY KEY,
    party_id     BIGINT      NOT NULL REFERENCES party (id),
    role_id      BIGINT      NOT NULL REFERENCES role (id),
    status_id    BIGINT      NOT NULL,
    created_date TIMESTAMPTZ NOT NULL,
    created_by   VARCHAR(64) NOT NULL,
    updated_date TIMESTAMPTZ,
    updated_by   VARCHAR(64),
    deleted_date TIMESTAMPTZ,
    deleted_by   VARCHAR(64)
);

-- A party may hold the same role only once among non-deleted rows.
CREATE UNIQUE INDEX ux_party_role_active ON party_role (party_id, role_id) WHERE deleted_date IS NULL;

-- Business customer number (externally visible Customer ID). Starts at 1001 per the
-- seed workbook; concurrency-safe because it is a database sequence.
CREATE SEQUENCE cust_customer_number_seq START WITH 1001;

CREATE TABLE cust (
    id              BIGSERIAL   PRIMARY KEY,
    customer_number BIGINT      NOT NULL UNIQUE,
    party_role_id   BIGINT      NOT NULL UNIQUE REFERENCES party_role (id),
    status_id       BIGINT      NOT NULL,
    created_date    TIMESTAMPTZ NOT NULL,
    created_by      VARCHAR(64) NOT NULL,
    updated_date    TIMESTAMPTZ,
    updated_by      VARCHAR(64),
    deleted_date    TIMESTAMPTZ,
    deleted_by      VARCHAR(64)
);

CREATE TABLE addr (
    id                  BIGSERIAL    PRIMARY KEY,
    party_id            BIGINT       NOT NULL REFERENCES party (id),
    city_id             BIGINT       NOT NULL REFERENCES city (id),
    district_id         BIGINT       NOT NULL REFERENCES district (id),
    street              VARCHAR(200) NOT NULL,
    house_flat_no       VARCHAR(20)  NOT NULL,
    address_description VARCHAR(200) NOT NULL,
    is_primary          BOOLEAN      NOT NULL DEFAULT FALSE,
    status_id           BIGINT       NOT NULL,
    created_date        TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(64)  NOT NULL,
    updated_date        TIMESTAMPTZ,
    updated_by          VARCHAR(64),
    deleted_date        TIMESTAMPTZ,
    deleted_by          VARCHAR(64)
);

-- FR-ADDR-05: at most one active primary address per party, enforced by the database.
CREATE UNIQUE INDEX ux_addr_active_primary ON addr (party_id) WHERE is_primary AND deleted_date IS NULL;

CREATE TABLE cntc_medium (
    id           BIGSERIAL    PRIMARY KEY,
    party_id     BIGINT       NOT NULL UNIQUE REFERENCES party (id), -- one contact row per party in this phase
    email        VARCHAR(254) NOT NULL,
    home_phone   VARCHAR(11),
    mobile_phone VARCHAR(11)  NOT NULL,
    fax          VARCHAR(11),
    status_id    BIGINT       NOT NULL,
    created_date TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(64)  NOT NULL,
    updated_date TIMESTAMPTZ,
    updated_by   VARCHAR(64),
    deleted_date TIMESTAMPTZ,
    deleted_by   VARCHAR(64)
);

-- Word-start, case-insensitive name search support (KR-01).
CREATE INDEX idx_ind_names ON ind (lower(first_name), lower(last_name));
-- GSM prefix search support (KR-01).
CREATE INDEX idx_cntc_mobile ON cntc_medium (mobile_phone);
