-- Shared cross-service catalogs (ADR-002). lookup-service is the ONLY owner of
-- these tables; no other service database may create, seed or FK-reference them.

CREATE TABLE gnl_st (
    id            BIGINT       PRIMARY KEY,
    short_code    VARCHAR(20)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    status_domain VARCHAR(20)  NOT NULL,
    created_date  TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(64)  NOT NULL,
    updated_date  TIMESTAMPTZ,
    updated_by    VARCHAR(64),
    deleted_date  TIMESTAMPTZ,
    deleted_by    VARCHAR(64)
);

CREATE TABLE gnl_tp (
    id           BIGINT       PRIMARY KEY,
    short_code   VARCHAR(20)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    type_domain  VARCHAR(30)  NOT NULL,
    created_date TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(64)  NOT NULL,
    updated_date TIMESTAMPTZ,
    updated_by   VARCHAR(64),
    deleted_date TIMESTAMPTZ,
    deleted_by   VARCHAR(64)
);
