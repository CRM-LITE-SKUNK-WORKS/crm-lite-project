# order-service API — Product Sale and Orders (FR-SALE-01..02)

Last updated: 2026-08-05. Source requirements: FR/AC **v8-2 (03.08.2026)**
§2.7 (SALE-02-01/SALE-01 validation wording clarified from v8-1; no behavioral
change — see `docs/requirements/document-delta.md`). Architecture: **ADR-016**
(boundary, KR-12 order number, sale orchestration),
with **ADR-013 §3.6/§7/§8** (the account precondition read and the involvement
command), **ADR-015 §5/§6** (the product write slice and where basket validation
lives), **ADR-002** (shared catalogs), **ADR-009/010** (zero-trust, service-to-service
auth).

Port 8087 (internal only — **never host-published**, ADR-009), database `order_db`,
gateway route `/api/orders/**` (TokenRelay + cookie stripping). Zero-trust JWT
resource server via `crm-security-starter`: every request needs a valid Keycloak
token with role `crm-user`; browser cookies never reach this service. Swagger:
unified gateway UI (`http://localhost:8080/swagger-ui.html`, dropdown entry
`order-service`, ADR-012).

> **KR-12 awaits analyst sign-off.** The Order Number format is a *project-proposed*
> rule (ADR-016 §4), recorded at the same level as the invented offer prices. It is
> labelled KR-12 (project-proposed) everywhere, never as analyst-issued.

## Endpoints (complete list — deliberately nothing else)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/orders` | Submit Order — the whole sale, one atomic command (AC-SALE-01-15) |
| GET | `/api/orders/{orderNumber}` | Order detail by its KR-12 public number |

**What is absent, and why:**

- **No order-cancel endpoint.** KR-7 leaves cancellation out of phase and no AC moves
  an order out of `MIDLWARE`. `CANCELLED` is reached only by the orchestration's own
  compensation (ADR-016 §6).
- **No basket endpoints, no basket table.** The basket is frontend/session state; the
  backend learns about a sale exactly once, at Submit. That is what makes
  **AC-SALE-01-16** ("an abandoned sale must never be processed later") true by
  construction rather than by a cleanup job — there is nothing to abandon. LBL-PREVIOUS
  (AC-SALE-01-13/14) is likewise a pure frontend concern.
- **No order list.** No FR asks for one, and inventing a list contract means inventing
  sorting, filtering and pagination rules an analyst has not written.

## Idempotency (ADR-016 idempotency addendum, 2026-08-06)

`POST /api/orders` requires an `Idempotency-Key` header — a client-generated UUID,
one per logical submit attempt (the Angular client mints it with `crypto.randomUUID()`
in `OrderSubmitStore` and reuses it for a retry of the SAME attempt, never for a new
one). Enforcement:

- **Missing or not a UUID** → `400 MSG-IDEMPOTENCY-KEY-REQUIRED`.
- **Same key + the same normalized request body** → the ORIGINAL response is replayed
  verbatim (same status, same body, response header `Idempotency-Replayed: true`) —
  the orchestration does not run again. This covers both an intentional duplicate call
  and a client retry after it never saw the first response (a timeout, a dropped
  connection): the server cannot and does not try to tell the two apart.
- **Same key + a DIFFERENT normalized body** → `409 MSG-IDEMPOTENCY-KEY-CONFLICT`,
  nothing processed.
- **Same key, a concurrent request for it still running** → `409
  MSG-IDEMPOTENCY-KEY-IN-PROGRESS`. Two concurrent requests for the same key can never
  both create an order: the FIRST one to reach it wins a UNIQUE-constraint INSERT on
  `idempotency_key.idempotency_key` — the database, not an in-memory check, is the
  final concurrency guard.
- A terminal outcome (success **or** a handled failure — 400/404/409/503) is recorded
  and replayed alike; the same "fail closed, log, move on" philosophy the rest of this
  ADR already applies (§5.5) is applied here to the idempotency ledger itself, rather
  than inventing a separate retry policy for it.

Enforced by a servlet filter (`IdempotencyKeyFilter`) ahead of the controller, so it
observes the SAME response `GlobalExceptionHandler` (or the 201 path) actually
produced — a replay is never a hand-rolled approximation of the original answer.

The same key is also forwarded to product-service as the **stable operation
identifier** (`ProductCreateRequest.saleOperationId`) that makes `POST /api/products`
replay-safe, and to the sale-scoped `POST /api/products/compensate` — see
`docs/api/product-service.md` and ADR-015's idempotency addendum. order-service does
not interpret the key otherwise; it is opaque, forwarded verbatim.

New table `idempotency_key` (Flyway V3) — see the Database section below.

## The sale orchestration (why three services are involved)

The flow writes to **three databases** — `order_db`, `product_db`, `account_db` —
with **no distributed transaction and no message broker**. The principle is: *one
commit point, everything before it discardable by construction, everything after it
non-destructive.*

```
POST /api/orders                                            → order-service
  0. GET /api/accounts/{n}          → account-service   exists? 224? Active? owner?
  1. order_db: BSN_INTER + CUST_ORD + CUST_ORD_ITEM     one local transaction, MIDLWARE
  2. POST /api/products             → product-service   products created PNDG
  3. order_db: fill product_id + amounts                second local transaction
  4. POST /api/products/confirm     → product-service   PNDG → ACTV
  5. POST /api/accounts/{n}/product-involvements        ← THE COMMIT POINT
                                    → account-service
```

Every step before 5 is compensated on failure: the products (still PNDG, or ACTV but
linked to no account — invisible to FR-PROD-01 either way) are discarded and the order
becomes `CANCELLED`. Nothing follows step 5, so nothing ever needs to undo it — which
is exactly why ADR-013 §8.6 declines to create an involvement-delete command.

All three calls go **directly via Eureka with the user's token propagated**
(ADR-010), never through the gateway, which is the browser edge (ADR-007).

**Transport retries are disabled** on all outbound clients (ADR-016 §5.3b):
httpclient5 re-executes a request that answered 503, and `POST /api/products` is not
idempotent — a retry would create a second set of products and orphan the first.

## Representations

**Order** (`POST /api/orders` → 201, and `GET /api/orders/{orderNumber}` → 200):

```json
{
  "orderNumber": "1261000010",
  "orderStatus": "MIDLWARE",
  "accountNumber": "1261000010",
  "customerNumber": 1001,
  "totalAmount": 497.00,
  "items": [
    {"offerId": 1, "productId": 21, "amount": 299.00},
    {"offerId": 2, "productId": 22, "amount": 149.00},
    {"offerId": 3, "productId": 23, "amount": 49.00}
  ]
}
```

- **It carries exactly what `order_db` owns.** The Submit screen also shows offer
  names, the campaign and the Service Address (AC-SALE-01-12), but those are not
  order-domain facts — the client assembled the basket and already holds them.
  Echoing them back would mean storing catalog data this service does not own (a copy
  that drifts the moment an offer is renamed) or calling product-service on every
  order lookup just to decorate a response. See ADR-016 §3.
- `orderStatus` is the catalog **short code**, not a rendered label. AC-SALE-01-15's
  user-facing text — *"Sipariş Alındı, İşleniyor…"* — is a localization catalog entry
  (FR-LANG); this backend returns language-neutral keys everywhere else too.
- `amount` / `totalAmount` are **snapshots** taken when the order was placed
  (ADR-016 §2.4). Reading the catalog price back later would silently rewrite the
  history of a past order whenever a price changes.
- Business numbers only. Internal ids (`cust_ord.id`, `bsn_inter.id`,
  `cust_ord_item.id`) never leave the service.

**Request** (`POST /api/orders`):

```json
{
  "accountNumber": "1261000010",
  "serviceAddressId": 1,
  "campaignId": "CMP-ADSL-01",
  "items": [
    {"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"}]},
    {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]},
    {"offerId": 3, "characteristics": []}
  ]
}
```

- `accountNumber` is the KR-11 billing account the sale started from
  (AC-SALE-01-01); `campaignId` is the **public** campaign code, never an internal id,
  and is optional (a basket assembled offer by offer belongs to no campaign);
  `serviceAddressId` is the address chosen for the main product (AC-SALE-01-11).
- **There is deliberately no `customerNumber` field.** product-service checks that
  `serviceAddressId` belongs to the customer (ADR-015 §5.9), and the number it checks
  against is read from the **billing account** at step 0 — never accepted from the
  client. A caller able to supply it would just claim the customer that owns the
  address it wanted, and the check would validate nothing.
- `characteristics` is empty for an offer that has none (AC-SALE-01-21). Values are
  forwarded to product-service **verbatim** — order-service writes no validation of
  its own (see below).

## Semantics

- **Submit (FR-SALE-01):** one atomic command carrying the whole basket. Returns 201
  with the order. The order is created with status `MIDLWARE` and **no AC moves it out
  of that status** — it is a complete order, not an in-flight one (ADR-016 §6).
- **Only an Active billing account may be sold into (FR-SALE-02 / AC-SALE-02-01).**
  Enforced server-side, twice: at step 0 and again by account-service at step 5. The
  FR states it only as a disabled UI action, and a disabled button is not a check.
- **Basket and characteristic validation live in product-service** (ADR-015 §6).
  "Is this offer active?" and "which service type does it derive from?" are
  `PROD_OFR`/`PROD_SPEC` facts; duplicating them here would mean replicating the
  catalog or adding a round trip, and order-service would still have to trust the
  answer. It therefore forwards the basket unchanged and **relays the upstream's own
  message key** — `MSG-SALE-NO-INTERNET`, `MSG-VAL-CHAR-REQUIRED` and the rest — with
  the upstream's status. Collapsing them into a generic 400 would break §2.7's whole
  point: the user is told *which* rule they broke.
- **Detail:** returns the order for a known KR-12 number, including a `CANCELLED` one
  — that is a real record of something that was attempted, and hiding it would make a
  compensated sale indistinguishable from one that never happened.

## Status / message-key matrix

| HTTP | Key | When |
|---|---|---|
| 200 / 201 | — | detail / submit |
| 400 | `MSG-VALIDATION-ERROR` | malformed body, empty basket, non-10-digit `accountNumber` — **or a `serviceAddressId` that is not one of the customer's active addresses** (AC-SALE-01-11, relayed from product-service) |
| 400 | `MSG-SALE-OFFER-INACTIVE`, `MSG-SALE-NO-INTERNET`, `MSG-SALE-NO-RESOURCE`, `MSG-SALE-NO-ACTIVATION`, `MSG-SALE-MULTI-INTERNET`, `MSG-SALE-MULTI-RESOURCE`, `MSG-SALE-MULTI-ACTIVATION`, `MSG-SALE-DUP-OFFER` | basket composition (AC-SALE-01-05/08) — **produced by product-service, relayed unchanged** |
| 400 | `MSG-VAL-CHAR-REQUIRED`, `MSG-VAL-CHAR-FORMAT` | characteristics (AC-SALE-01-18/19) — likewise relayed |
| 401 / 403 | `MSG-AUTH-UNAUTHORIZED` / `MSG-AUTH-FORBIDDEN` | starter contract (403 `MSG-AUTH-CSRF-REJECTED` at the gateway) |
| 404 | `MSG-ACCT-NOT-FOUND` | unknown `accountNumber` — or a K-8 223's number, indistinguishable by design (ADR-013 §4.5) |
| 404 | `MSG-ORDER-NOT-FOUND` | unknown order number (**project addition**) |
| 409 | `MSG-ACCT-NOT-ACTIVE` | the billing account is Passive (AC-SALE-02-01) |
| 409 | `MSG-ORDER-DUP-NUMBER` | order-number uniqueness race (DB fallback — never 500) |
| 409 | `MSG-ORDER-NUMBER-CAPACITY-EXCEEDED` | KR-12 sequence exhausted for segment+year |
| 409 | `MSG-IDEMPOTENCY-KEY-CONFLICT` | same `Idempotency-Key`, a different request body (**project addition**) |
| 409 | `MSG-IDEMPOTENCY-KEY-IN-PROGRESS` | same key, a concurrent request for it is still running (**project addition**) |
| 503 | `MSG-SERVICE-UNAVAILABLE` | lookup, product, account **or customer** service unreachable — fail closed; the order is `CANCELLED` and nothing the customer can see was created |

`MSG-IDEMPOTENCY-KEY-REQUIRED` (400) is produced when the header is missing or not a
UUID — see the Idempotency section above.

Error body: the established `{timestamp, status, error, messageKey, message, path,
validationErrors}` shape.

**Message-key notes**

- `MSG-SALE-ORDER-CONFIRM` is **frontend-only** (the AC-SALE-01-15 modal); the backend
  never produces it.
- `MSG-ORDER-NOT-FOUND`, `MSG-ORDER-DUP-NUMBER` and
  `MSG-ORDER-NUMBER-CAPACITY-EXCEEDED` are **documented project additions** — the
  analyst catalog names no order outcomes, because §2.7 never describes an order being
  looked up or refused. EN/TR suggestions: *"Order not found." / "Sipariş bulunamadı."*

## KR-12 Order Number (project-proposed — ADR-016 §4)

`[T][YY][SSSSSS][C]` — deliberately the exact shape KR-11 defines for account numbers:
segment `1` (NEWSALE this phase) + last two year digits + per-segment/per-year sequence
from `100000` + **Luhn** check digit, stored as `VARCHAR(10) UNIQUE`, immutable, never
reused — including after a compensated (CANCELLED) sale. Seed order: `1261000002`;
`order_number_seq (1, 2026) = 100001`, so the first new 2026 order is `1261000010`.

> ⚠️ **Order and account numbers share one value space** — the seeded order number
> `1261000002` is also account `1261000002`'s. Accepted (ADR-016 §8.1): different
> databases, different services, no shared namespace, and KR-02 disambiguates by
> giving the customer search two separate fields. The residual cost is human: a bare
> 10-digit number out of context cannot be classified by eye. Recorded as an analyst
> note.

The generator is **duplicated** from account-service rather than shared: this project
has no shared business-logic library, and creating one for two classes would couple
two services' release cycles. The copies are pinned by identical unit-test vectors
(`OrderNumberFormatTest` asserts the same values as `AccountNumberFormatTest`).

## Recorded deviations from the entity workbook

The workbook itself is never edited; all of these are also in
`docs/requirements/document-delta.md`.

1. **`order_number` 5001 regenerated to KR-12** `1261000002` — the workbook's legacy
   value satisfies no format rule. Exactly how ADR-014 §8 regenerated the `CUST_ACCT`
   seed.
2. **`cust_ord.customer_id` → `customer_number`** and **`bsn_inter.customer_account_id`
   → `customer_account_number`**. Internal ids never leave the service that owns them,
   so they are not observable here; the public business number is the only stable
   cross-service reference (the ADR-013 §2.3 reasoning).
3. **Amount snapshot columns added** (`cust_ord.total_amount`,
   `cust_ord_item.amount`) — the workbook has neither, but AC-SALE-01-12 requires a
   Total Amount and per-offer amounts. **Same pending-approval status as the prices
   that feed them** (document-delta P1/P5, open conflict #10).
4. **`order_number_seq` added** — a project table required by KR-12, exactly as
   ADR-014 §3 recorded `acct_number_seq` for accounts.
5. **`cust_ord_item.product_id` is nullable.** The workbook carries the column, but the
   product does not exist when the header is written; it is filled by a second local
   transaction (ADR-016 §5.2). The intermediate state is never observable.
6. **`BSN_INTER_TYPE` TRANSFER(8)/CANCEL(9) are never written**, and GNL_ST `WAIT`(3)
   is never used. No FR/AC covers them (ADR-016 §6/§8.2) — flagged for analysts, not
   built.

## Database (`order_db`, Flyway V1–V3)

Five tables: `bsn_inter`, `cust_ord`, `cust_ord_item`, `order_number_seq`,
`idempotency_key` (V3, ADR-016 idempotency addendum — project bookkeeping, not a
workbook table; holds the key, the normalized request hash, status, the order number
once known, a response snapshot + HTTP status, created/updated timestamps and a
retention `expires_at`).

- **No local `gnl_st`/`gnl_tp` table, no local catalog seed, no cross-database FK**
  (ADR-002) — asserted by an integration test over `information_schema`. `status_id`
  and `bsn_inter_type_id` store central catalog contract IDs as external references.
- **No customer, account or product table.** `customer_number`,
  `customer_account_number`, `product_offer_id` and `product_id` are all FK-less
  external references.
- Nothing is ever physically deleted: a compensated sale becomes `CANCELLED` and keeps
  its number forever.

## curl test sequence (gateway, browser session required)

> Login first in a browser (`http://localhost:8080/api/session/me`,
> `ayilmaz`/`crm-dev`), then export the two cookies; mutating requests also need the
> CSRF header.

```bash
C="Cookie: JSESSIONID=$JSESSIONID; XSRF-TOKEN=$XSRF"
X="X-XSRF-TOKEN: $XSRF"

# DETAIL of the seeded order -> 200, MIDLWARE, three items, total 497.00
curl -sS -H "$C" \
  "http://localhost:8080/api/orders/1261000002" \
  -w "\nHTTP Status: %{http_code}\n"

# SUBMIT a complete ADSL sale -> 201, order number 1261000010
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  "http://localhost:8080/api/orders" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"accountNumber": "1261000010", "serviceAddressId": 1, "campaignId": "CMP-ADSL-01",
 "items": [
   {"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"}]},
   {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]},
   {"offerId": 3, "characteristics": []}
 ]}
JSON

# SUBMIT with no ACTIVATION offer -> 400 MSG-SALE-NO-ACTIVATION (product-service's key)
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/orders" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"accountNumber": "1261000010", "serviceAddressId": 1,
 "items": [
   {"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"}]},
   {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]}
 ]}
JSON

# SUBMIT with a mandatory characteristic left blank -> 400 MSG-VAL-CHAR-REQUIRED
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/orders" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"accountNumber": "1261000010", "serviceAddressId": 1,
 "items": [
   {"offerId": 1, "characteristics": []},
   {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]},
   {"offerId": 3, "characteristics": []}
 ]}
JSON

# SUBMIT against a PASSIVE billing account -> 409 MSG-ACCT-NOT-ACTIVE (AC-SALE-02-01)
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/orders" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"accountNumber": "1261000036", "serviceAddressId": 1,
 "items": [
   {"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"}]},
   {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]},
   {"offerId": 3, "characteristics": []}
 ]}
JSON

# SUBMIT with an address belonging to ANOTHER customer -> 400 MSG-VALIDATION-ERROR
# (address 3 is customer 1002's; account 1261000010 belongs to customer 1001)
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/orders" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"accountNumber": "1261000010", "serviceAddressId": 3,
 "items": [
   {"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"}]},
   {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]},
   {"offerId": 3, "characteristics": []}
 ]}
JSON

# SUBMIT against an unknown account (or the K-8 223) -> 404 MSG-ACCT-NOT-FOUND
curl -sS -X POST -H "$C" -H "$X" -H "Content-Type: application/json" \
  "http://localhost:8080/api/orders" \
  --data-binary @- -w "\nHTTP Status: %{http_code}\n" <<'JSON'
{"accountNumber": "1261000002", "serviceAddressId": 1,
 "items": [{"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"}]},
           {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]},
           {"offerId": 3, "characteristics": []}]}
JSON

# The sold-into account can no longer be passivated -> 409 MSG-ACCT-HAS-PRODUCTS
curl -sS -X DELETE -H "$C" -H "$X" \
  "http://localhost:8080/api/accounts/1261000010" \
  -w "\nHTTP Status: %{http_code}\n"

# The new products are now visible on the account (FR-PROD-01)
curl -sS -H "$C" \
  "http://localhost:8080/api/products?accountNumber=1261000010" \
  -w "\nHTTP Status: %{http_code}\n"

# DETAIL of an unknown order -> 404 MSG-ORDER-NOT-FOUND
curl -sS -H "$C" \
  "http://localhost:8080/api/orders/9999999999" \
  -w "\nHTTP Status: %{http_code}\n"

# ANONYMOUS -> 401 MSG-AUTH-UNAUTHORIZED
curl -sS -H "Accept: application/json" \
  "http://localhost:8080/api/orders/1261000002" \
  -w "\nHTTP Status: %{http_code}\n"
```

## Companion changes in other services (same PR)

- **account-service:** `POST /api/accounts/{accountNumber}/product-involvements`
  (ADR-013 §8) — the commit point; bulk, idempotent per (account, product), Active-224
  only. Plus `customerNumber` added to the account representation (ADR-013 §3.6), which
  is how order-service records `cust_ord.customer_number` while knowing only an account
  number. Details: `docs/api/account-service.md`.
- **product-service:** the FR-SALE write slice (ADR-015 §5) — `POST /api/products`
  (PNDG), `/confirm`, `/cancel`, plus `GET /api/offers/{id}/characteristics`. The full
  `LookupCatalogClient` boundary was built first (ADR-015 §4.1). PNDG products are
  excluded from FR-PROD-01/02. Details: `docs/api/product-service.md`.

## Deliberate limitations / future work (never silently faked)

- **KR-12 awaits analyst sign-off**, as do the prices its `total_amount` snapshots
  (document-delta P1/P5).
- ~~**KR-02 `orderNumber` search stays 501** in customer-service~~ — **done
  2026-08-05**, and against exactly the endpoint this document predicted:
  customer-service calls `GET /api/orders/{orderNumber}` and matches only when
  `orderStatus` is `"MIDLWARE"`, so a `CANCELLED` (compensated) order never
  surfaces its customer as a search hit. **Nothing changed here** — the response
  already carried `orderNumber`, `orderStatus` and `customerNumber`. The
  deliberate absence of an order LIST endpoint is unaffected: the search resolves
  one number at a time, it does not enumerate orders. See ADR-005 §Addendum
  2026-08-05.
- **No stuck-PNDG sweeper.** If the confirm step's compensation itself fails, products
  are left identifiable by a single query (`status_id = PNDG`) — recorded as an
  operational follow-up (ADR-015 §8.4), not built speculatively.
- **Transfer and service-address-change flows are not built** (ADR-016 §8.2): the mock
  UI offers them as account-row actions but no FR/AC covers them. Analyst question.
- **Frontend is out of scope** — Offer Selection, Product Configuration and Submit
  Order screens are a separate follow-up (ADR-016 §9).
