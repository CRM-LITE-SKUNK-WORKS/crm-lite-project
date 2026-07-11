# lookup-service API (shared GNL_ST / GNL_TP catalogs)

Owner of the cross-service catalogs (ADR-002). Port **8083**, database `lookup_db`.
Read-only in this phase; soft-deleted catalog rows are never returned.
Through the gateway: `http://localhost:8080/api/lookups/...`

| Method | Path | Description |
|---|---|---|
| GET | `/api/lookups/statuses` | All statuses; optional `?domain=GENERAL` |
| GET | `/api/lookups/statuses/{shortCode}` | One status by code (404 + `MSG-LOOKUP-NOT-FOUND` if unknown) |
| GET | `/api/lookups/types` | All types; optional `?domain=GENDER` |
| GET | `/api/lookups/types/{shortCode}` | One type by code |

Response shapes:

```json
{"id": 1, "shortCode": "ACTV", "name": "Active", "statusDomain": "GENERAL"}
{"id": 1, "shortCode": "MALE", "name": "Male",   "typeDomain": "GENDER"}
```

## Contract guarantees (binding for all consumers)

- `id` values are **immutable**: they are enumerated in the Final entity workbook and
  seeded explicitly by this service's Flyway. Renumbering is a breaking change and is
  prohibited; additions are forward-only migrations with new explicit IDs.
- `shortCode` is unique per catalog.
- Consumers may persist `id` as an external reference (no FK) and may cache entries;
  a bounded TTL cache is recommended (customer-service uses 15 min / 256 entries).
- Consumers must validate the **domain** (e.g. gender must be `GENDER`) — a code
  existing is not enough.

## Examples

```bash
curl http://localhost:8083/api/lookups/statuses/ACTV
curl "http://localhost:8083/api/lookups/types?domain=GENDER"
curl http://localhost:8080/api/lookups/types/INDV     # via gateway
```
