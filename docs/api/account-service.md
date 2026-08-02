# account-service API — Billing Accounts (FR-ACCT-01..04, KR-11)

Last updated: 2026-08-01 (added the internal
`POST /api/accounts/{accountNumber}/product-involvements` **write** command and
`customerNumber` to the representation, for the FR-SALE-01 sale flow — **ADR-013
§3.6/§8**; the FR-ACCT-01..04 contract itself is unchanged). Prior: 2026-07-29
(the `product-ids` read endpoint + Flyway `V3__seed_activation_involvement.sql`
for the product-service slice — now formalized as **ADR-013 §7**).
Architecture: **ADR-013** (boundary/contract), **ADR-014** (Account Number
generation), ADR-010 addendum (outbound auth), **ADR-016** (the order-service
caller).
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
| GET | `/api/accounts/{accountNumber}/product-ids` | **Internal (2026-07-29):** involved product ids for product-service (ADR-013 §7 read side) |
| POST | `/api/accounts/{accountNumber}/product-involvements` | **Internal (2026-08-01):** link products to the account — the commit point of a sale (ADR-013 §8 write side) |

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
- **This is the read endpoint.** The write counterpart is below.

### `POST /api/accounts/{accountNumber}/product-involvements` (service-to-service)

Added 2026-08-01 (**ADR-013 §8**) — this discharges the §5.2 TODO that said future
order/product services would populate the projection "through an account-service
command/API, never by writing `account_db` directly". order-service invokes it at
the **commit point** of a sale (ADR-016 §5.1 step 4).

```jsonc
// request
{"productIds": [30, 31, 32]}

// 201 response — the account's FULL resulting id set, product_id ascending
{"accountNumber": "1261000176", "productIds": [30, 31, 32]}
```

- **Bulk, single-shot:** one call per sale, never N calls. All rows are written in
  one local `account_db` transaction, so a partial link set is never observable.
- **Idempotent per (account, product):** an already-linked, non-deleted pair is
  left untouched — not duplicated, not rejected. Repeated ids inside one body
  collapse to one row too. The caller is a compensating orchestrator that may
  retry, and duplicate rows would corrupt the AC-ACCT-04-03 guard's meaning.
  Returning the resulting *state* (rather than an insert count) is what makes
  this observable: a retry answers identically.
- **Target must be an Active 224.** Unknown numbers and the K-8 223 → `404
  MSG-ACCT-NOT-FOUND`; a **Passive** 224 → **`409 MSG-ACCT-NOT-ACTIVE`**. Note the
  asymmetry with the read side: a Passive account stays *readable*
  (AC-ACCT-04-02) but must never acquire new products. This is the server-side
  enforcement of **AC-SALE-02-01**, which the FR states only as a disabled UI
  action.
- **Rows written:** `short_code = 'ACCT_PROD'` (the workbook's per-table constant,
  verified against the V1 default and every seed row — **not** a campaign or offer
  code), `status_id = ACTV` resolved through lookup-service. Catalog unreachable
  and uncached → **503 `MSG-SERVICE-UNAVAILABLE`**, nothing persisted (ADR-002 §7).
- **`productId` is an opaque external reference.** account-service deliberately
  does **not** call product-service to verify it: that would invert the dependency
  direction (product-service already depends on account-service) and create a call
  cycle. The caller owns that guarantee; this service owns only the projection
  (ADR-013 §8.5).
- **There is no involvement *delete* command** (ADR-013 §8.6): nothing in FR-SALE
  or KR-7 removes a product from an account, and ADR-016's compensations never
  need to undo this step.
- Empty/missing `productIds`, or a non-positive id → `400 MSG-VALIDATION-ERROR`.
  Unlike POST/PUT `/api/accounts`, unknown properties are **not** answered with
  `MSG-ACCT-IMMUTABLE-FIELD`: that key means "you tried to set a field the user
  may not set", which is a form concern, not an internal command's.
- Reached **directly via Eureka** with the user's token propagated (ADR-010),
  never through the gateway — same rationale as the read endpoint.

## Representation (the only response shape)

```json
{
  "accountNumber": "1261000010",
  "customerNumber": 1001,
  "accountName": "1261000010",
  "accountTypeCode": "224",
  "accountTypeName": "Billing Account",
  "billingAddressId": 1,
  "accountStatus": "Active"
}
```

`customerNumber` was added 2026-08-01 (**ADR-013 §3.6**): the account owner's
**public business customer number** — the very value the list endpoint already
takes as `customerId` — never an internal id. order-service needs it to record
`cust_ord.customer_number` while knowing only the account it is selling into, and
this endpoint is its natural precondition call (it also yields the 224/Active
checks AC-SALE-02-01 needs). Purely **additive**: no field was removed or renamed.

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
| 404 | `MSG-ACCT-NOT-FOUND` | unknown accountNumber (or a K-8 223's number) — detail, update, delete, `product-ids` **and `product-involvements`** |
| 404 | `MSG-CUST-NOT-FOUND` | create for an unknown/passive customer |
| 409 | `MSG-ACCT-HAS-PRODUCTS` | delete blocked by active involvement (AC-ACCT-04-03) |
| 409 | `MSG-ACCT-NOT-ACTIVE` | update or re-delete of a Passive account — **or an involvement write against one** (AC-SALE-02-01) |
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

# INVOLVEMENT WRITE on an Active 224 -> 201, resulting ids ascending
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts/1261000176/product-involvements" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"productIds": [31, 30, 32]}
JSON

# REPEAT the same call -> 201 with the SAME list (idempotent, no duplicate rows)
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts/1261000176/product-involvements" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"productIds": [30, 31, 32]}
JSON

# The read side now agrees -> 200 [30, 31, 32]
curl -sS -H "$C" \
  "http://localhost:8080/api/accounts/1261000176/product-ids" \
  -w "\nHTTP Status: %{http_code}\n"

# The account can no longer be passivated -> 409 MSG-ACCT-HAS-PRODUCTS
curl -sS -X DELETE -H "$C" -H "$X" \
  "http://localhost:8080/api/accounts/1261000176" \
  -w "\nHTTP Status: %{http_code}\n"

# INVOLVEMENT WRITE on the Passive account -> 409 MSG-ACCT-NOT-ACTIVE (AC-SALE-02-01)
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts/1261000036/product-involvements" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"productIds": [40]}
JSON

# INVOLVEMENT WRITE with an empty list -> 400 MSG-VALIDATION-ERROR
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/accounts/1261000176/product-involvements" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"productIds": []}
JSON

# ANONYMOUS -> 401 MSG-AUTH-UNAUTHORIZED
curl -sS -H "Accept: application/json" \
  "http://localhost:8080/api/accounts?customerId=1001" \
  -w "\nHTTP Status: %{http_code}\n"
```

## Deliberate limitations / future work (never silently faked)

- `cust_acct_prod_invl` is now populated by **real sales** through the §8 write
  command (order-service, FR-SALE-01), no longer by seed/test data alone. It
  remains **single-writer**: no other service writes `account_db` (ADR-013 §5).
  Consequence worth knowing: an account that has just been sold into can no
  longer be passivated (409 `MSG-ACCT-HAS-PRODUCTS`) — AC-ACCT-04-03 working as
  specified, but reachable in real use for the first time.
- **No involvement removal exists** — not through this API, not through any
  other. Product cancellation is out of phase (KR-7).
- customer-service's `accountNumber` search (KR-02), its customer-delete
  active-product guard and billing-account passivation, and the address
  `MSG-ADDR-IN-USE` in-use check remain customer-service TODOs — converting them
  to real calls against this service is a **separate follow-up PR** (this sprint
  does not modify customer-service).
