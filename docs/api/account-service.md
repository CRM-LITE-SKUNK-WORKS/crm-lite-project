# account-service API — Billing Accounts (FR-ACCT-01..04, KR-11)

Last updated: 2026-07-29 (added the internal
`GET /api/accounts/{accountNumber}/product-ids` read endpoint + Flyway
`V3__seed_activation_involvement.sql` for the product-service slice; the
FR-ACCT-01..04 contract itself is unchanged). Architecture: **ADR-013**
(boundary/contract), **ADR-014** (Account Number generation), ADR-010 addendum
(outbound auth).
Source requirements: FR/AC **v8-1 Final (23.07.2026)** — supersedes v8; see
`docs/requirements/document-delta.md`.

Port 8085 (internal only — **never host-published**, ADR-009), database
`account_db`, gateway route `/api/accounts/**` (TokenRelay + cookie stripping).
Zero-trust JWT resource server via `crm-security-starter`: every request needs a
valid Keycloak token with role `crm-user`; browser cookies never reach this
service. Swagger: unified gateway UI (`http://localhost:8080/swagger-ui.html`,
dropdown entry `account-service`, ADR-012).

## Endpoints (complete list — deliberately nothing else)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/accounts?customerId={customerNumber}` | Billing-account list for one customer (**224 only**, Active + Passive, no pagination) |
| POST | `/api/accounts` | Create a Billing Account (type forced to 224; K-8 lazy 223 side effect) |
| GET | `/api/accounts/{accountNumber}` | Single account (populates the update screen; Passive readable) |
| PUT | `/api/accounts/{accountNumber}` | Update `accountName` + `addressId` ONLY |
| DELETE | `/api/accounts/{accountNumber}` | Soft passivation (never physical) |
| GET | `/api/accounts/{accountNumber}/product-ids` | **Internal (2026-07-29):** involved product ids for product-service (ADR-013 §5 read side) |

`customerId` is always the **public business customer number** (`cust.customer_number`,
e.g. 1001) — never an internal id. `accountNumber` is the 10-digit KR-11 number.

### `GET /api/accounts/{accountNumber}/product-ids` (service-to-service)

Added 2026-07-29 for product-service's FR-PROD-01 composition. This is the
**single public reading point** of the `cust_acct_prod_invl` projection: no other
service reads or writes `account_db` (ADR-013 §5).

```json
[1, 2, 3, 4]
```

- Returns involvement rows with `deleted_date IS NULL`, ascending by `product_id`.
- **Deliberately NOT filtered by involvement `status_id`:** AC-PROD-01-03 lists
  both active and passive products, and the displayed status comes from the
  *product*. The ACTV-only filter remains exclusive to the AC-ACCT-04-03 delete
  guard — that logic is untouched.
- Visibility follows the same rule as the detail endpoint: **224 only**, so an
  unknown number *and* a K-8 223's number both answer `404 MSG-ACCT-NOT-FOUND`
  (ADR-013 §4.5). A **Passive** 224 stays readable (AC-ACCT-04-02).
- An account with no involvements is `200 []`, never 404.
- Reached **directly via Eureka** with the user's token propagated (ADR-010),
  never through the gateway. It is nonetheless matched by the existing
  `/api/accounts/**` gateway route — acceptable: it exposes only ids and requires
  the same `crm-user` JWT as every other account endpoint.
- **This is a read endpoint only.** Populating/maintaining the projection remains
  an unimplemented, documented TODO (a future account-service command/API or a
  consumed event — never a direct `account_db` write).

## Representation (the only response shape)

```json
{
  "accountNumber": "1261000010",
  "accountName": "1261000010",
  "accountTypeCode": "224",
  "accountTypeName": "Billing Account",
  "billingAddressId": 1,
  "accountStatus": "Active"
}
```

Never present: internal ids (`cust_acct.id`, `acct_tp.id`), an `Action` field
(UI concern), or any 223 Customer Account (K-8 — see below).

## Semantics

- **List (FR-ACCT-01):** requires `customerId` (missing/non-numeric → 400).
  Returns a plain JSON array: only type-224 accounts, **both Active and Passive**
  (AC-ACCT-01-03), sorted **Active first, then Passive; accountNumber ascending
  inside each group** (AC-ACCT-01-04). No accounts / unknown customer → `200 []`.
  The read path is fully local (no cross-service calls).
- **Create (FR-ACCT-02):** body `{"customerId", "accountName", "addressId"}` —
  all required; the account type is **not client-selectable**. Validates the
  customer exists and is active (customer-service detail endpoint) and that
  `addressId` appears in the customer's **active** address list
  (`GET /api/customers/{n}/addresses`); resolves `ACTV` through lookup-service
  (fail closed). Assigns the KR-11 number and returns **201**.
  **K-8 (analyst-approved):** if the customer has no 223 Customer Account yet,
  one is created automatically in the **same ACID transaction**, before the 224 —
  real KR-11 number from the same sequence, fixed name `"Customer Account"`,
  the customer's primary address. It never appears in any account-service
  response; its number answers 404 on the single-account endpoints.
- **Update (FR-ACCT-03):** mutable fields are **exactly** `accountName` and
  `addressId` (re-validated). `accountNumber`/account type are immutable; any
  extra submitted property → **400 `MSG-ACCT-IMMUTABLE-FIELD`** (rejected, not
  ignored). Passive account → **409 `MSG-ACCT-NOT-ACTIVE`**.
- **Delete (FR-ACCT-04):** soft passivation (`status_id → PASV` + deleted/updated
  audit metadata). The row **stays list-visible as Passive** (v8-1 wording — NOT
  removed from the list) and its number is never reused. Active product
  involvement (local `cust_acct_prod_invl` projection) → **409
  `MSG-ACCT-HAS-PRODUCTS`**. Already-Passive → **409 `MSG-ACCT-NOT-ACTIVE`**.
  Success → **204 No Content**; the frontend shows `MSG-ACCT-DELETED` after the
  204 (`MSG-ACCT-DELETE-CONFIRM` is frontend-only; the backend produces neither).

## Status / message-key matrix

| HTTP | Key | When |
|---|---|---|
| 200 / 201 / 204 | — | list, detail, update / create / delete |
| 400 | `MSG-VALIDATION-ERROR` | missing/blank fields, missing or non-numeric `customerId`, addressId not in the customer's active list, malformed body |
| 400 | `MSG-ACCT-IMMUTABLE-FIELD` | immutable/unknown properties submitted on POST/PUT |
| 401 / 403 | `MSG-AUTH-UNAUTHORIZED` / `MSG-AUTH-FORBIDDEN` | starter contract (403 `MSG-AUTH-CSRF-REJECTED` at the gateway) |
| 404 | `MSG-ACCT-NOT-FOUND` | unknown accountNumber (or a K-8 223's number) — detail, update, delete **and `product-ids`** |
| 404 | `MSG-CUST-NOT-FOUND` | create for an unknown/passive customer |
| 409 | `MSG-ACCT-HAS-PRODUCTS` | delete blocked by active involvement (AC-ACCT-04-03) |
| 409 | `MSG-ACCT-NOT-ACTIVE` | update or re-delete of a Passive account |
| 409 | `MSG-ACCT-DUP-NUMBER` | account-number uniqueness race (DB fallback — never 500) |
| 409 | `MSG-ACCT-NUMBER-CAPACITY-EXCEEDED` | KR-11 sequence exhausted for segment+year |
| 503 | `MSG-SERVICE-UNAVAILABLE` | lookup-service or customer-service unreachable during a write (fail closed, nothing persisted) |

Error body: the established `{timestamp, status, error, messageKey, message,
path, validationErrors}` shape. EN/TR texts for the project-added keys:
`docs/architecture/account-service-decisions.md`.

## KR-11 Account Number (ADR-014)

`[T][YY][SSSSSS][C]` — segment `1` (this phase) + last two year digits +
per-segment/per-year sequence from `100000` + **Luhn** check digit, stored as
`VARCHAR(10) UNIQUE`, immutable, never reused. Canonical sample:
payload `126100000` → `1261000002`. Seed accounts (regenerated from the
workbook's legacy values): `1261000002` (223), `1261000010`, `1261000028`,
`1261000036`; `acct_number_seq (1, 2026) = 100004` → the first new 2026 number
is `1261000044`.

## curl test sequence (gateway, browser session required)

> Login first in a browser (`http://localhost:8080/api/session/me`,
> `ayilmaz`/`crm-dev`), then export the two cookies; mutating requests also need
> the CSRF header. In the examples: `$JSESSIONID` and `$XSRF` hold the cookie
> values from DevTools.

```bash
C="Cookie: JSESSIONID=$JSESSIONID; XSRF-TOKEN=$XSRF"
X="X-XSRF-TOKEN: $XSRF"

# LIST (seed customer 1001) -> 200, three 224s ascending, Active first
curl -sS -H "$C" \
  "http://localhost:8080/api/accounts?customerId=1001" \
  -w "\nHTTP Status: %{http_code}\n"

# LIST without customerId -> 400 MSG-VALIDATION-ERROR
curl -sS -H "$C" \
  "http://localhost:8080/api/accounts" \
  -w "\nHTTP Status: %{http_code}\n"

# LIST for a customer with no billing accounts -> 200 []
curl -sS -H "$C" \
  "http://localhost:8080/api/accounts?customerId=1002" \
  -w "\nHTTP Status: %{http_code}\n"

# DETAIL -> 200
curl -sS -H "$C" \
  "http://localhost:8080/api/accounts/1261000010" \
  -w "\nHTTP Status: %{http_code}\n"

# DETAIL of the K-8 223 -> 404 MSG-ACCT-NOT-FOUND (never exposed)
curl -sS -H "$C" \
  "http://localhost:8080/api/accounts/1261000002" \
  -w "\nHTTP Status: %{http_code}\n"

# CREATE (customer 1002, first billing account -> K-8 also creates its 223) -> 201
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"customerId": 1002, "accountName": "Zeynep Billing", "addressId": 3}
JSON

# CREATE with a forbidden field -> 400 MSG-ACCT-IMMUTABLE-FIELD
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"customerId": 1002, "accountName": "Sneaky", "addressId": 3, "accountType": "223"}
JSON

# CREATE with a foreign address -> 400 MSG-VALIDATION-ERROR
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"customerId": 1002, "accountName": "Wrong addr", "addressId": 1}
JSON

# CREATE for an unknown customer -> 404 MSG-CUST-NOT-FOUND
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"customerId": 9999, "accountName": "Ghost", "addressId": 1}
JSON

# UPDATE name+address -> 200
curl -sS -X PUT -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts/1261000028" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"accountName": "Renamed Billing", "addressId": 2}
JSON

# UPDATE with immutable field -> 400 MSG-ACCT-IMMUTABLE-FIELD
curl -sS -X PUT -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts/1261000028" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"accountName": "X", "addressId": 1, "accountNumber": "9999999999"}
JSON

# DELETE the involvement-linked account -> 409 MSG-ACCT-HAS-PRODUCTS (seed guard)
curl -sS -X DELETE -H "$C" -H "$X" \
  "http://localhost:8080/api/accounts/1261000010" \
  -w "\nHTTP Status: %{http_code}\n"

# DELETE a free account -> 204; it STAYS in the list as Passive (v8-1)
curl -sS -X DELETE -H "$C" -H "$X" \
  "http://localhost:8080/api/accounts/1261000036" \
  -w "\nHTTP Status: %{http_code}\n"

# RE-DELETE the now-Passive account -> 409 MSG-ACCT-NOT-ACTIVE
curl -sS -X DELETE -H "$C" -H "$X" \
  "http://localhost:8080/api/accounts/1261000036" \
  -w "\nHTTP Status: %{http_code}\n"

# UPDATE the Passive account -> 409 MSG-ACCT-NOT-ACTIVE
curl -sS -X PUT -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts/1261000036" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"accountName": "Should fail", "addressId": 1}
JSON

# ANONYMOUS -> 401 MSG-AUTH-UNAUTHORIZED
curl -sS -H "Accept: application/json" \
  "http://localhost:8080/api/accounts?customerId=1001" \
  -w "\nHTTP Status: %{http_code}\n"
```

## Deliberate limitations / future work (never silently faked)

- `cust_acct_prod_invl` is populated **only by seed/test data** until
  product-service exists; it is nonetheless the real, queried guard state.
  Future population happens through an account-service command/API or a consumed
  event — other services must never write `account_db` (ADR-013 §5).
- customer-service's `accountNumber` search (KR-02), its customer-delete
  active-product guard and billing-account passivation, and the address
  `MSG-ADDR-IN-USE` in-use check remain customer-service TODOs — converting them
  to real calls against this service is a **separate follow-up PR** (this sprint
  does not modify customer-service).
