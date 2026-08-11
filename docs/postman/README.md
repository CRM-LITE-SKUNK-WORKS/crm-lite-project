# CRM Lite Postman Collection

Importable API collection for the whole currently implemented backend
(gateway, lookup catalog, cities/districts, customers, addresses, contact medium,
MERNIS stub, negative scenarios). Collection schema: **Postman v2.1**.

| File | Purpose |
|---|---|
| `CRM-Lite.postman_collection.json` | The collection (folders 00–12) |
| `CRM-Lite.local.postman_environment.json` | Local environment: base URLs + dynamic variables. **Contains no secrets and no tokens** |

## Authentication (READ FIRST — ADR-006..008)

Since the auth milestone, **every business request through the gateway requires a
logged-in session**; anonymous calls return **401 `MSG-AUTH-UNAUTHORIZED`** and
unsafe methods additionally require the CSRF header. The gateway is a
cookie-session BFF — it does **not** accept `Authorization: Bearer`, and **Direct
Access Grant / ROPC / password grant is disabled by design**: never obtain a
token with a username+password call, not even "just for testing".

Login flow for Postman use (Authorization Code + PKCE, via a real browser):

1. Start the stack incl. Keycloak (`docs/runbooks/local-development.md`).
2. In a browser open `http://localhost:8080/api/session/me` → Keycloak login →
   sign in as `ayilmaz` / `crm-dev` (local-only dev fixture) → JSON session summary.
3. DevTools → Application/Storage → Cookies → `http://localhost:8080`: copy
   **`JSESSIONID`** and **`XSRF-TOKEN`**.
4. Postman → **Cookies** (under the Send button) → domain `localhost` → add both
   cookies (port 8080).
5. Set the **`xsrfToken`** environment variable to the `XSRF-TOKEN` value —
   write requests (folders 04/05/06 create/update/delete, 09 logout) send it as
   `X-XSRF-TOKEN`.

Folder **09 - Authentication** contains the session probe, the PKCE entry-point
assertion and the CSRF-protected logout. The full browser contract is in
`docs/api/authentication.md`. Sessions idle out after 30 minutes (KR-9) — if
requests start failing with 401, repeat the login.

**Never store** real passwords, access tokens, refresh tokens or client secrets
in the collection/environment. The `xsrfToken` variable is not a secret (it is a
CSRF correlation value readable by the browser by design).

## How to import

1. Postman → **Import** → drop both JSON files (or File → Import → select them).
2. In the environment selector (top right), choose **“CRM Lite — Local”**.
3. Nothing else to configure — all URLs point at localhost.

## Startup order (services must be up first)

1. PostgreSQL + Keycloak (`podman compose -f infra/docker-compose.yml up -d postgres keycloak`)
2. config-server (8888) → 3. discovery-server (8761) → 4. lookup-service (8083)
→ 5. mernis-stub (8084) → 6. api-gateway (8080) → 7. customer-service (8082)
→ 8. account-service (8085) → 9. product-service (8086)

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
- **10 - Billing Accounts (FR-ACCT)**: run top-to-bottom — list/detail reads →
  **create** (stores `createdAccountNumber`) → negative creates → update →
  immutable-field 400 → delete-guard 409 (seed `1261000010` has active product
  involvements) → **delete created** (passivation, 204) → Passive detail/re-delete/
  update checks. Unlike folders 04/05/06, every write request in folder 10 carries
  the `X-XSRF-TOKEN: {{xsrfToken}}` header **pre-set** — you only need the
  `xsrfToken` environment variable filled (step B.3 above). Note: delete =
  **passivation** — the account stays in the list as Passive (AC-ACCT-04-02, v8-2); the hidden
  K-8 223 (`1261000002`) intentionally answers 404.
- **11 - Products and Catalog (FR-PROD)**: order-independent **reads only** (no
  CSRF header needed anywhere), but run it **before** folder 10's delete step if
  you want the seeded involvements intact — folder 10 passivates
  `{{createdAccountNumber}}`, not the seeded `1261000010`, so in practice the two
  folders don't collide. Products of `1261000010` are the seeded ADSL
  installation (4 rows: two in campaign `CMP-ADSL-01`, one campaign-less, one
  **Passive** fixture); `1261000028` is the empty-state fixture (`200 []` — the
  `MSG-PROD-NONE` text is frontend-only); the hidden K-8 223 answers 404 here
  too. The last two requests exercise account-service's internal
  `/product-ids` read endpoint (ADR-013 §5 read side), which product-service
  normally calls directly via Eureka rather than through the gateway.
- **12 - Asynchronous SALE (FR-SALE, ADR-018)**: **run top-to-bottom, order matters**
  — each request stores what the next one needs (`draftOrderNumber`,
  `submitOrderNumber`, `abandonOrderNumber`).

  **Prerequisite:** the three sale services must be running with
  `SPRING_PROFILES_ACTIVE=async-sale` and the Compose `eventing` profile up
  (`docker compose --profile eventing up -d`). Without them the *Submit draft*
  request answers **503 `MSG-SERVICE-UNAVAILABLE`** — that is the intended
  fail-closed behaviour (ADR-018 §10), not a broken collection: accepting a 202
  for a sale nothing can process would be the actual bug.

  The *Poll status until terminal* request **re-runs itself** (`setNextRequest`)
  while `processingStatus` is `PROCESSING`, bounded at 20 attempts so a genuinely
  stuck saga fails the run instead of looping. Expected end state: `orderStatus`
  `MIDLWARE` **+** `processingStatus` `COMPLETED` — a completed sale stays MIDLWARE,
  because no accepted requirement defines another terminal ORDER status.

  The deprecated synchronous `POST /api/orders` is deliberately **not** in the
  collection: one sale must never enter both routes, and the folder's
  *Submit the SAME order again with a NEW key* request is what proves the guard.
- Other folders are order-independent.

Business requests go through `{{gatewayBaseUrl}}` (port 8080) and need the
session cookies (see **Authentication** above). Direct service URLs
(`configBaseUrl`, `discoveryBaseUrl`, `customerBaseUrl`, `lookupBaseUrl`,
`mernisBaseUrl`) appear only in requests explicitly labelled internal/debug —
mernis-stub in particular has **no gateway route** by design. Since the auth
milestone the direct customer/lookup URLs answer **401 without a valid Keycloak
JWT** (zero trust, ADR-009) — expect that when running them without a token; in
the compose profile those ports aren't even published to the host.

## How dynamic variables are populated

Test scripts write values back into the environment:

| Variable | Set by | Used by |
|---|---|---|
| `createdCustomerNumber` | *04 / Atomic customer create* (from the 201 response) | update, delete, verify-404 in folder 04 |
| `addressId` | *05 / List addresses* (first row of seed customer 1001) | set-primary-back in folder 05 |
| `createdAddressId` | *05 / Create address* (from the 201 response) | PUT update, set primary, delete in folder 05 |
| `createdAccountNumber` | *10 / Create billing account* (from the 201 response) | update, delete and Passive-policy checks in folder 10 |
| `draftIdemKey` / `submitIdemKey` | *12* pre-request scripts (`{{$guid}}`) | the two duplicate-key replay checks — deliberately **two different** keys, because one `Idempotency-Key` identifies one HTTP command, not one sale (ADR-018 §3) |
| `draftOrderNumber` | *12 / Create draft* (from the 201 response) | submit, status, and the not-a-draft check in folder 12 |
| `submitOrderNumber` | *12 / Submit draft* (from the 202 response) | status polling and order detail in folder 12 |
| `abandonOrderNumber` | *12 / Cancel before submit: create a second draft* | abandon, abandoned-status and abandoned-submit checks in folder 12 |

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

No credentials, tokens or Keycloak secrets belong in this collection or
environment (`crm-bff` is a public client — there is no client secret at all).
