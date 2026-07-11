# customer_db Schema Reference

Owner: customer-service Flyway (`backend/customer-service/src/main/resources/db/migration`).
Hibernate runs `ddl-auto: validate` — Flyway is the only schema authority.

- `V1__create_customer_core_tables.sql` — tables, sequence, indexes
- `V2__seed_customer_core_data.sql` — workbook seed (customers 1001, 1002 active; **1003 soft-deleted on purpose** as a fixture for ADR-003 and only-active-search rules)

## Deliberate absences (ADR-002)

- **No `gnl_st` / `gnl_tp` tables and no seeds for them** — they live only in `lookup_db`.
- **No cross-database foreign keys.** `status_id`, `gender_id`, `party_type_id` are plain
  BIGINT columns holding central contract IDs, validated through the lookup API at write time.

## Key constraints

| Constraint | Where | Meaning |
|---|---|---|
| `ind.nationality_id` UNIQUE | all rows | ADR-003: permanent global uniqueness; soft delete never releases the value; racing inserts surface as 409 |
| `ind.party_id` UNIQUE FK | ind→party | 1-1 Party↔Individual |
| `ux_party_role_active (party_id, role_id) WHERE deleted_date IS NULL` | party_role | no duplicate active role |
| `cust.customer_number` UNIQUE + `cust_customer_number_seq` (START 1001) | cust | concurrency-safe business ID, never reused |
| `cust.party_role_id` UNIQUE FK | cust→party_role | 1-1 |
| `ux_addr_active_primary (party_id) WHERE is_primary AND deleted_date IS NULL` | addr | at most one active primary address per party |
| `cntc_medium.party_id` UNIQUE FK | cntc_medium→party | one contact row per party this phase |
| `idx_ind_names (lower(first_name), lower(last_name))` | ind | word-start name search support |
| `idx_cntc_mobile (mobile_phone)` | cntc_medium | GSM prefix search support |

## Changing the schema

V1/V2 form the baseline of the first shared release of this service. Once these
migrations are merged/pushed, **never edit them** — add forward-only `V3+` migrations
(CLAUDE.md rule). If you pulled a V1/V2 edit made before the first share, reset your
local volume (see `docs/runbooks/database.md`).
