# ADR-002: Shared GNL_ST / GNL_TP Catalog Ownership

## Status
Accepted (2026-07-10)

## Context
The final Entity/Seed workbook (`docs/source/data-model/CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx`)
defines two general-purpose catalogs used by every business domain:

- **GNL_ST** — status catalog (`ACTV`, `PASV` in domain `GENERAL`; plus `ORDER` and `PROD` domain values).
- **GNL_TP** — type catalog (`MALE`, `FEMALE` in domain `GENDER`; `INDV`, `ORG` in `PARTY_TYPE`;
  plus `CATALOG_TYPE`, `BSN_INTER_TYPE`, `SERVICE_TYPE` domain values).

Every business table in the workbook references these catalogs through `status_id`,
`gender_id` or `party_type_id` columns. Before this ADR no component owned the catalogs
and no copy existed anywhere in the repository. Duplicating them per service database
would create drift, hidden coupling on coincidentally equal IDs, and no single source
of truth for whether a code exists or which domain it belongs to.

## Decision
1. **Owner:** a new dedicated deployable, **`backend/lookup-service`** (port **8083**,
   database **`lookup_db`**), owns the `gnl_st` and `gnl_tp` tables and their seed data.
   It is the only component whose Flyway migrations create or seed these tables.
2. **Access:** other services consume the catalogs exclusively through its REST API
   (via Eureka / `lb://lookup-service`):
   - `GET /api/lookups/statuses?domain={domain}`
   - `GET /api/lookups/statuses/{shortCode}`
   - `GET /api/lookups/types?domain={domain}`
   - `GET /api/lookups/types/{shortCode}`
3. **customer-service boundary:** all access goes through `com.crm.customer.lookup`
   (`LookupCatalogClient` → HTTP, `LookupCatalogService` → validation + cache).
   Controllers, repositories and entities never call the catalog directly.
4. **What customer_db stores:** business rows store the **central, contract-immutable
   catalog IDs** in `status_id` / `gender_id` / `party_type_id` columns. These are
   *external references*: **no local FK, no cross-database FK** is created for them.
   The Final workbook enumerates the IDs explicitly (GNL_ST: 1=ACTV, 2=PASV, …;
   GNL_TP: 1=MALE, 2=FEMALE, 3=INDV, …), which makes them contract data, not
   database-generated values. lookup-service seeds them with these explicit IDs and
   **must never renumber them** (forward-only additions).
   The ID-based option was chosen over short-code columns because the approved entity
   contract names `*_id` columns on every table; readability is recovered through the
   `LookupContract` constants class and the code-first client API.
5. **Validation:** on every write that needs a catalog value, `LookupCatalogService`
   resolves the semantic short code (e.g. `ACTV`, `MALE`, `INDV`) through the API,
   verifies the value exists, is not deleted, and belongs to the **expected domain**,
   and returns the central ID for persistence. For contract codes the resolved ID is
   additionally asserted against `LookupContract`; a mismatch fails the operation
   (defends against a wrongly re-seeded central catalog).
6. **Caching:** resolved entries are cached in a small bounded in-memory TTL cache
   (default 15 minutes, max 256 entries — the catalog is inherently tiny).
   Catalog values are contract-immutable, so serving cached entries is always safe;
   the TTL exists to pick up *additions* without restart. Invalidation = TTL expiry
   only; there is no cross-service cache eviction (not needed for immutable entries).
7. **Failure behaviour (fail closed):**
   - Catalog unreachable and value not cached → the write fails with **503**
     (`MSG-SERVICE-UNAVAILABLE`). No partial aggregate is persisted.
   - Unknown short code or wrong domain → **400** (`MSG-VALIDATION-ERROR`, field-level detail).
   - Unknown codes are never silently accepted; there is no hardcoded production fallback.
8. **Reads stay local:** active-record filtering uses the locally stored reference plus
   the soft-delete invariant (`status_id = <contract ACTV id> AND deleted_date IS NULL`).
   The ACTV/PASV IDs come from `LookupContract`, so **no query performs a remote call
   per row or per request**. customer-service startup does not hard-fail when
   lookup-service is down; only writes require it.

## Consequences
- One authoritative catalog; future services (account/product/order domains already
  present in the seed workbook) reuse lookup-service instead of duplicating tables.
- customer_db is self-sufficient for reads and lifecycle filtering.
- Central seed migrations live in lookup-service only; customer-service migrations
  are forbidden from creating or seeding `gnl_st`/`gnl_tp` (asserted by an automated test).
- Renumbering central catalog IDs is a breaking contract change and is prohibited.
