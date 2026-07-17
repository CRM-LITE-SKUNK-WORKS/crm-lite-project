# lookup-service API (shared GNL_ST / GNL_TP catalogs)

Owner of the cross-service catalogs (ADR-002). Port **8083**, database `lookup_db`.
Read-only in this phase; soft-deleted catalog rows are never returned.
Through the gateway: `http://localhost:8080/api/lookups/...`

| Method | Path | Description |
|---|---|---|
| GET | `/api/lookups/statuses` | All statuses; optional `?domain=GENERAL` |
| GET | `/api/lookups/statuses/{shortCode}` | One status by code (404 + `MSG-LOOKUP-NOT-FOUND` if unknown) |
| GET | `/api/lookups/types` | All types; optional `?domain=GENDER` |
| GET | `/api/lookups/types/{shortCode}` | One type by code (404 + `MSG-LOOKUP-NOT-FOUND` if unknown) |

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

## curl catalog (each request prints its HTTP status)

```bash
# health
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8083/actuator/health

# all statuses
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/lookups/statuses

# statuses filtered by domain
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/lookups/statuses?domain=GENERAL"
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/lookups/statuses?domain=ORDER"

# one status by code
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/lookups/statuses/ACTV

# all types
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/lookups/types

# types filtered by domain
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/lookups/types?domain=GENDER"
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/lookups/types?domain=PARTY_TYPE"

# one type by code
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/lookups/types/INDV

# unknown lookup code -> 404 MSG-LOOKUP-NOT-FOUND
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/api/lookups/statuses/NOPE

# direct (bypassing the gateway — internal/debug)
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8083/api/lookups/statuses/ACTV
```
