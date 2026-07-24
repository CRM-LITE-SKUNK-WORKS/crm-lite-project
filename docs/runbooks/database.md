# Runbook — Databases

One PostgreSQL 16 instance (compose service `postgres`, port 5432) hosts separate
databases per service:

| Database | Owner service | Schema authority |
|---|---|---|
| `customer_db` | customer-service | its Flyway (V1 tables/indexes, V2 seed) |
| `lookup_db` | lookup-service | its Flyway (V1 gnl_st/gnl_tp, V2 contract seed) |
| `account_db` | account-service | its Flyway (V1 acct_tp/cust_acct/cust_acct_prod_invl/acct_number_seq, V2 KR-11 seed — ADR-013/014) |
| `keycloak_db` | keycloak (infra) | Keycloak-managed |
| `crm_admin` | — | compose default DB only |

`infra/postgres/init/01-create-databases.sql` creates the databases **only on first
volume initialization**. Hibernate validates; it never creates or alters schema.

> **Existing volume predating account-service?** The init script will not re-run;
> create the database once by hand (same situation as `keycloak_db`):
> `podman exec postgres psql -U crmlite -d crm_admin -c 'CREATE DATABASE account_db;'`
> then (re)start account-service so Flyway applies V1/V2.

## DBeaver connection

- Host `localhost`, Port `5432`, Username `crmlite`, Password `crmlite`
- Database: `customer_db` (add further connections for `lookup_db` / `account_db`)

## Verification queries

```sql
-- customer_db: expected tables (and ONLY these + flyway_schema_history)
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1;
-- role, city, district, party, ind, party_role, cust, addr, cntc_medium, flyway_schema_history

-- MUST return zero rows: no local shared-catalog tables (ADR-002)
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_name IN ('gnl_st', 'gnl_tp');

-- Flyway state
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;

-- Soft-delete invariant sample: active customers only
SELECT c.customer_number, i.first_name, i.last_name
FROM cust c
JOIN party_role pr ON pr.id = c.party_role_id
JOIN ind i ON i.party_id = pr.party_id
WHERE c.status_id = 1 AND c.deleted_date IS NULL;

-- lookup_db: contract IDs (never renumber!)
SELECT id, short_code, status_domain FROM gnl_st ORDER BY id;
SELECT id, short_code, type_domain  FROM gnl_tp ORDER BY id;

-- account_db: expected tables (and ONLY these + flyway_schema_history; ADR-013)
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1;
-- acct_number_seq, acct_tp, cust_acct, cust_acct_prod_invl, flyway_schema_history

-- account_db MUST have no shared-catalog tables either (ADR-002)
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_name IN ('gnl_st', 'gnl_tp');

-- KR-11 sequence invariant: next_value = the NEXT number to issue (seed leaves 100004)
SELECT segment, seq_year, next_value FROM acct_number_seq ORDER BY segment, seq_year;

-- Accounts: 223 is the hidden K-8 row; the three 224s are the listed billing accounts
SELECT ca.account_number, ca.account_name, at.account_type_code, ca.customer_number,
       ca.status_id, ca.deleted_date
FROM cust_acct ca JOIN acct_tp at ON at.id = ca.account_type_id
ORDER BY ca.account_number;
```

## Resetting local data

> ⚠️ **DESTRUCTIVE** — `down -v` deletes the Postgres volume: all local customers,
> both databases, everything. Only for local development.

```bash
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d postgres
#   podman compose -f infra/docker-compose.yml down -v && podman compose -f infra/docker-compose.yml up -d postgres
```

Then restart lookup-service and customer-service; each re-applies its migrations to
the fresh databases.

When is a reset required?
- You pulled a change that **edited** (not appended) a not-yet-shared migration —
  e.g. the 2026-07-10 baseline replacement of customer-service V1/V2. Flyway checksum
  validation fails against an old volume until you reset.
- After migrations are merged/shared, they are **never edited** (CLAUDE.md rule);
  new changes arrive as V3+ and need no reset.

## Migration rules recap

- customer-service migrations must never create/seed `gnl_st`/`gnl_tp` or add
  cross-database FKs (ADR-002; enforced by `schemaContainsNoLocalCatalogTables` test).
- lookup-service owns its catalog seeds; additions are forward-only with explicit IDs.
