# CRM Lite Postman Collection

Importable API collection for the whole currently implemented backend
(gateway, lookup catalog, cities/districts, customers, addresses, contact medium,
MERNIS stub, negative scenarios). Collection schema: **Postman v2.1**.

| File | Purpose |
|---|---|
| `CRM-Lite.postman_collection.json` | The collection (folders 00–08) |
| `CRM-Lite.local.postman_environment.json` | Local environment: base URLs + dynamic variables. **Contains no secrets** |

## How to import

1. Postman → **Import** → drop both JSON files (or File → Import → select them).
2. In the environment selector (top right), choose **“CRM Lite — Local”**.
3. Nothing else to configure — all URLs point at localhost.

## Startup order (services must be up first)

1. PostgreSQL (`docker compose -f infra/docker-compose.yml up -d postgres`)
2. config-server (8888) → 3. discovery-server (8761) → 4. lookup-service (8083)
→ 5. mernis-stub (8084) → 6. api-gateway (8080) → 7. customer-service (8082)

Details: `docs/runbooks/local-development.md`. Folder **00 - Health** verifies the
whole stack top-to-bottom.

## Request execution order

Folders are numbered in the intended order. Within these folders order matters:

- **04 - Customers - Detail and Lifecycle**: detail → **create** (stores
  `createdCustomerNumber`) → update → **delete** → verify-404. Before running
  *create*, set the `nationalityId` environment variable to a **fresh, unused
  11-digit value** (not `99999999999`, not a seed value like `12345678901` /
  `23456789012` / `34567890123`, not one you created before — ADR-003 reserves used
  ids **forever**, even after delete).
- **05 - Addresses**: list (stores `addressId`) → create (stores `createdAddressId`)
  → PUT update → set primary → primary-delete guard (409) → set primary back →
  delete created address.
- Other folders are order-independent.

Business requests go through `{{gatewayBaseUrl}}` (port 8080). Direct service URLs
(`configBaseUrl`, `discoveryBaseUrl`, `customerBaseUrl`, `lookupBaseUrl`,
`mernisBaseUrl`) appear only in requests explicitly labelled internal/debug —
mernis-stub in particular has **no gateway route** by design.

## How dynamic variables are populated

Test scripts write values back into the environment:

| Variable | Set by | Used by |
|---|---|---|
| `createdCustomerNumber` | *04 / Atomic customer create* (from the 201 response) | update, delete, verify-404 in folder 04 |
| `addressId` | *05 / List addresses* (first row of seed customer 1001) | set-primary-back in folder 05 |
| `createdAddressId` | *05 / Create address* (from the 201 response) | PUT update, set primary, delete in folder 05 |

Static defaults you may edit: `customerNumber` (1001), `cityId` (1), `districtId`
(1), `nationalityId` (see the freshness rule above).

Each request carries a basic status-code test; negative requests also assert the
expected `messageKey` (e.g. `MSG-CUST-DUP-NATID`, `MSG-CUST-NATID-VERIFICATION-FAILED`).

## Destructive requests (change local data)

- *04 / Customer soft delete* — passivates the created customer; its `nationalityId`
  stays reserved permanently (ADR-003).
- *05 / Delete created address* — soft-deletes the address created in the same folder.
- Every successful *create* also permanently consumes its `nationalityId`.

Everything else is read-only or updates seed data in place (contact medium, address
PUT — re-runnable).

## How to reset database state

> ⚠️ Destroys ALL local data in both databases.

```bash
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d postgres
# then restart lookup-service and customer-service (Flyway re-seeds)
```

See `docs/runbooks/database.md`.

## Why Turkish JSON is safe in Postman

Request bodies contain Turkish characters (Gözde, Çağlar, Tunalı…) on purpose —
VR-NAME must accept them. Postman sends bodies over the wire as UTF-8 HTTP payloads
directly (with `Content-Type: application/json; charset=utf-8` set on each request),
never through a Windows command-line/argv encoding layer — so unlike `curl -d '...'`
in Git Bash (see PROJECTBRAIN §5.15), multi-byte characters cannot be mangled.

## Failure-mode tests that need manual steps

Folder 08 covers everything reproducible with the stack up. The two outage behaviours
require stopping a service first (see `docs/api/mernis-stub.md` §7):

- stop **lookup-service** → create returns 503 `MSG-SERVICE-UNAVAILABLE`
- stop **mernis-stub** → create returns 503 `MSG-MERNIS-UNAVAILABLE`

No credentials or future Keycloak secrets belong in this collection or environment.
