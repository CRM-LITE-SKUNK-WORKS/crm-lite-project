# product-service API — Product Viewing + Read-only Catalog (FR-PROD-01..02)

Last updated: 2026-08-05. Source requirements: FR/AC **v8-2 (03.08.2026)**
§2.6 (reviewed — unchanged from v8-1). Boundary rules applied: **ADR-002** (shared catalogs), **ADR-009**
(zero-trust), **ADR-010** (service-to-service auth), **ADR-013 §5**
(`cust_acct_prod_invl` single-writer — this service composes over
account-service's API and NEVER touches `account_db`).

> **ADR owed:** this PR writes no ADR. ADR-015 (product boundary) and a
> "read-side" clause on ADR-013 are **outstanding**, and
> `docs/architecture/service-boundaries.md`'s "analyst/architecture sign-off is
> still missing" warning still applies to the product domain.

Port 8086 (internal only — **never host-published**, ADR-009), database
`product_db`, gateway routes `/api/products/**`, `/api/offers/**`,
`/api/campaigns/**` (TokenRelay + cookie stripping). Zero-trust JWT resource
server via `crm-security-starter`: every request needs a valid Keycloak token
with role `crm-user`; browser cookies never reach this service. Swagger: unified
gateway UI (`http://localhost:8080/swagger-ui.html`, dropdown entry
`product-service`, ADR-012).

**Two slices.** The FR-PROD-01..02 read side (2026-07-29) and the FR-SALE write
side (2026-08-02, ADR-015 §5/§6 — see below). Still absent by design: no basket, no
order, no Kafka or Redis. **Product cancellation remains out of phase**
(KR-7, AC-PROD-01-04: the Action column offers only a view icon) — the `/cancel`
command below is compensation for never-committed rows, not the same thing.

## Endpoints (complete list — deliberately nothing else)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/products?accountNumber={kr11}` | Products of one billing account (FR-PROD-01; **no pagination**) |
| GET | `/api/products/{id}` | Product detail modal (FR-PROD-02) |
| GET | `/api/offers` | Active offer catalog (Offer Selection support for the future §2.7 flow) |
| GET | `/api/campaigns` | Active campaigns with member offers + derived total price |

`accountNumber` is the public 10-digit KR-11 billing-account number.
`{id}` is the public product identifier — the internal `prod.id`. **There is no
product-number generation rule**: KR-11-style business numbers exist only for
accounts.

## FR-PROD-01 composition (why account-service is involved)

`PROD` carries **no customer or account column**; the product ↔ billing-account
link lives only in `account_db.cust_acct_prod_invl`, which is written
exclusively by account-service (ADR-013 §5). So the list is a composition:

```
GET /api/products?accountNumber=1261000010          → product-service
      ↓ lb://account-service  (Eureka, user token propagated — ADR-010;
        never through the gateway, which is the browser edge — ADR-007)
GET /api/accounts/{accountNumber}/product-ids       → account-service
      ↓ [1, 2, 3, 4]
product_db JOIN PROD / PROD_OFR / PROD_SPEC / CMPG
      ↓ [ProductRowResponse]
```

The upstream endpoint returns involvement rows with `deleted_date IS NULL` and
is deliberately **not** filtered by involvement status: AC-PROD-01-03 lists both
active and passive products, and the displayed status comes from
`PROD.status_id`. The involvement's own `status_id` matters only to
account-service's AC-ACCT-04-03 delete guard.

## Representations

**Product row** (`GET /api/products`) — AC-PROD-01-03 columns:

```json
[
  {
    "productId": 1,
    "productName": "ADSL 8MB",
    "campaignName": "ADSL Hosgeldin Kampanyasi",
    "campaignId": "CMP-ADSL-01",
    "productStatus": "Active"
  },
  {
    "productId": 3,
    "productName": "ADSL Activation",
    "campaignName": null,
    "campaignId": null,
    "productStatus": "Active"
  }
]
```

- `campaignId` is the **public campaign code** (`cmpg.campaign_code`), never the
  internal `cmpg.id`.
- A product bought outside a campaign carries `null` campaign fields — the `"-"`
  rendering in AC-PROD-01-03 is the frontend's job.
- No `Action` field: that column is a pure UI concern (AC-PROD-01-04).
- No pagination envelope — FR-PROD-01 defines no pagination rule; the full list
  is returned.

**Product detail** (`GET /api/products/{id}`) — AC-PROD-02-01 fields:

```json
{
  "productOfferName": "ADSL Data Modem Offer",
  "productOfferId": 2,
  "productSpecId": 2,
  "campaign": "ADSL Hosgeldin Kampanyasi",
  "serviceAddress": {
    "addressId": 1,
    "street": "Bagdat Cad.",
    "houseFlatNumber": "12/4",
    "districtName": "Kadikoy",
    "cityName": "Istanbul"
  }
}
```

`prod.service_address_id` is only filled on the **main** product of an
installation, so a child product displays **its parent's** service address (the
parent chain is walked upwards). The address itself is an FK-less external
reference into `customer_db`, resolved through customer-service's internal
`GET /api/addresses/{addressId}` endpoint (user token propagated). A
soft-deleted/vanished address leaves `serviceAddress: null` — the modal still
renders; customer-service being **unreachable** is a 503 (see below).

**Offer** (`GET /api/offers`):

```json
[{"offerId": 1, "offerName": "ADSL 8MB Offer", "serviceType": "INTERNET", "price": 299.00}]
```

`serviceType` is **derived through the spec** (`PROD_SPEC.service_type_id` →
central GNL_TP `10=INTERNET`, `11=RESOURCE`, `12=ACTIVATION`): `PROD_OFR` has no
service-type column of its own. `price` is a seed fixture pending analyst
approval (see *Recorded deviations*).

**Campaign** (`GET /api/campaigns`):

```json
[
  {
    "campaignId": "CMP-ADSL-01",
    "campaignName": "ADSL Hosgeldin Kampanyasi",
    "description": "ADSL 8MB + modem birlikte",
    "activationEndDate": "2026-12-31T23:59:59Z",
    "offers": [
      {"offerId": 1, "offerName": "ADSL 8MB Offer", "serviceType": "INTERNET", "price": 299.00, "main": true},
      {"offerId": 2, "offerName": "ADSL Data Modem Offer", "serviceType": "RESOURCE", "price": 149.00, "main": false},
      {"offerId": 3, "offerName": "ADSL Activation Offer", "serviceType": "ACTIVATION", "price": 49.00, "main": false}
    ],
    "totalPrice": 497.00
  }
]
```

`totalPrice` is **derived** (sum of member offer prices); `CMPG` deliberately has
no price column. `campaignId` is again the public campaign code.

Never present in any response: internal keys other than the public product/offer/
spec ids (`cmpg.id`, join-table ids, `status_id` values), an `Action` field, or
any customer/account identifier — this service stores none.

## Status / message-key matrix

| HTTP | Key | When |
|---|---|---|
| 200 | — | list (possibly `[]`), detail, offers, campaigns |
| 400 | `MSG-VALIDATION-ERROR` | missing `accountNumber`, non-numeric product id |
| 401 / 403 | `MSG-AUTH-UNAUTHORIZED` / `MSG-AUTH-FORBIDDEN` | starter contract (403 `MSG-AUTH-CSRF-REJECTED` at the gateway) |
| 404 | `MSG-ACCT-NOT-FOUND` | unknown `accountNumber` — or a K-8 223's number, indistinguishable by design (ADR-013 §4.5) |
| 404 | `MSG-PROD-NOT-FOUND` | unknown product id (**project addition**, see below) |
| 503 | `MSG-SERVICE-UNAVAILABLE` | account-service or customer-service unreachable (fail closed — no partial or fabricated answer) |

Error body: the established `{timestamp, status, error, messageKey, message,
path, validationErrors}` shape.

**Message-key notes**

- `MSG-PROD-NONE` is **frontend-only**: an account with no products is a
  `200 []`, and the frontend renders the AC-PROD-01-02 info text. The backend
  never produces this key.
- `MSG-PROD-NOT-FOUND` (404) is a **documented project addition** — it is not in
  the analyst catalog, which never names an unknown-product outcome because
  FR-PROD-02 is reached from a row the user just saw. EN/TR suggestion:
  *"Product not found."* / *"Ürün bulunamadı."*

## Recorded deviations from the entity workbook

All four are also recorded in `docs/requirements/document-delta.md`; the workbook
itself is never edited.

1. **Offer prices are invented fixtures.** `PROD_OFR.product_offer_total_price`
   is empty in all three workbook rows, but AC-SALE-01-12 requires per-offer
   amounts and a Total Amount. Seeded: 299.00 / 149.00 / 49.00. **Analyst
   approval pending.** The campaign price is derived (497.00), not stored.
2. **Campaign fixture.** Every workbook `PROD` row has an empty `campaign_id`,
   leaving AC-PROD-01-03's "show campaign when present" branch untestable.
   Products 1 and 2 are linked to campaign 1; product 3 stays campaign-less so
   the `"-"` branch is also covered.
3. **Passive product fixture.** Every workbook product is `ACTV`. Product 4
   (`ADSL 8MB Legacy`, not in the workbook) is passivated with the full
   soft-delete invariant so the Status column's Passive branch is testable.
4. **Activation-product involvement** (and the passive product's involvement)
   are seeded by **account-service's `V3__seed_activation_involvement.sql`**,
   because those rows belong to `account_db` (ADR-013 §5). product-service's own
   seed never touches that table. Accounts `1261000028` and `1261000036` stay
   product-less on purpose: they are the `MSG-PROD-NONE` fixtures.

Columns the FR is silent about are kept schema-faithful and nullable, unused by
these APIs: `PROD_SPEC.is_dev` (workbook values 0/1, meaning undefined by any
FR/AC) and `PROD.transaction_id` (empty in every workbook row; presumably a
future order/business-interaction reference — the order domain does not exist
yet).

## Database (`product_db`, Flyway V1/V2)

Ten tables, faithful to the workbook: `prod_spec`, `prod_ofr`, `cmpg`,
`cmpg_prod_ofr`, `prod`, `prod_spec_char`, `prod_spec_char_use`,
`prod_char_val`, `prod_catal`, `prod_catal_prod_ofr`.

- **No local `gnl_st`/`gnl_tp` table, no local catalog seed, no cross-database
  FK** (ADR-002). `status_id`, `prod_spec.service_type_id` and
  `prod_catal.catalog_type_id` store central catalog contract IDs as external
  references.
- **No customer or account column anywhere** — asserted by an integration test
  over `information_schema.columns`.
- `prod.service_address_id` is an FK-less external reference to a
  customer-service address; `parent_prod_id` / `parent_offer_id` are local
  self-FKs; all other relations carry normal local FKs.
- The characteristic model (`prod_spec_char`, `prod_spec_char_use`,
  `prod_char_val` with its single `val` string column and
  `data_type ∈ {NUMBER, BOOLEAN, TEXT, DATE}`) is created and seeded but has **no
  Phase A endpoint** — it is consumed by the §2.7 Product Configuration screens.
- **No lookup HTTP client** (unlike customer/account-service): Phase A performs
  no write that would need a live catalog resolve, so `isActive()` filtering uses
  the local `LookupContract` constants. A future write slice must introduce the
  full `LookupCatalogClient` boundary before persisting any status.

## curl test sequence (gateway, browser session required)

> Login first in a browser (`http://localhost:8080/api/session/me`,
> `ayilmaz`/`crm-dev`), then export the session cookie. All product endpoints are
> GETs, so no CSRF header is needed.

```bash
C="Cookie: JSESSIONID=$JSESSIONID; XSRF-TOKEN=$XSRF"

# LIST for the seeded billing account -> 200, four rows (last one Passive)
curl -sS -H "$C" \
  "http://localhost:8080/api/products?accountNumber=1261000010" \
  -w "\nHTTP Status: %{http_code}\n"

# LIST for a product-less account -> 200 [] (frontend shows MSG-PROD-NONE)
curl -sS -H "$C" \
  "http://localhost:8080/api/products?accountNumber=1261000028" \
  -w "\nHTTP Status: %{http_code}\n"

# LIST without accountNumber -> 400 MSG-VALIDATION-ERROR
curl -sS -H "$C" \
  "http://localhost:8080/api/products" \
  -w "\nHTTP Status: %{http_code}\n"

# LIST for an unknown account (or the K-8 223) -> 404 MSG-ACCT-NOT-FOUND
curl -sS -H "$C" \
  "http://localhost:8080/api/products?accountNumber=1261000002" \
  -w "\nHTTP Status: %{http_code}\n"

# DETAIL of a CHILD product -> 200; Service Address is the PARENT's
curl -sS -H "$C" \
  "http://localhost:8080/api/products/2" \
  -w "\nHTTP Status: %{http_code}\n"

# DETAIL of a campaign-less product -> 200 with "campaign": null
curl -sS -H "$C" \
  "http://localhost:8080/api/products/3" \
  -w "\nHTTP Status: %{http_code}\n"

# DETAIL of an unknown product -> 404 MSG-PROD-NOT-FOUND
curl -sS -H "$C" \
  "http://localhost:8080/api/products/999" \
  -w "\nHTTP Status: %{http_code}\n"

# OFFERS -> 200, serviceType derived through the spec
curl -sS -H "$C" \
  "http://localhost:8080/api/offers" \
  -w "\nHTTP Status: %{http_code}\n"

# CAMPAIGNS -> 200, public code as campaignId, derived totalPrice 497.00
curl -sS -H "$C" \
  "http://localhost:8080/api/campaigns" \
  -w "\nHTTP Status: %{http_code}\n"

# ANONYMOUS -> 401 MSG-AUTH-UNAUTHORIZED
curl -sS -H "Accept: application/json" \
  "http://localhost:8080/api/products?accountNumber=1261000010" \
  -w "\nHTTP Status: %{http_code}\n"
```

## Companion changes in other services (same PR)

- **account-service:** new `GET /api/accounts/{accountNumber}/product-ids` →
  `200 [Long]` (the single public reading point of the involvement projection,
  ADR-013 §5) + `V3__seed_activation_involvement.sql`. Unknown numbers and the
  K-8 223 answer 404 `MSG-ACCT-NOT-FOUND`; a Passive 224 stays readable
  (AC-ACCT-04-02). Not gateway-routed for service use — product-service calls it
  directly via Eureka.
- **customer-service:** new internal `GET /api/addresses/{addressId}` →
  `AddressResponse` (active only; 404 `MSG-CUST-NOT-FOUND` otherwise). Needed
  because product-service holds only the bare `service_address_id` and the
  public address API is customer-scoped. Deliberately not gateway-routed. This
  is a **read-only addition** — customer-service's documented 501/no-op TODOs
  (accountNumber search, active-product guard, `MSG-ADDR-IN-USE`) are untouched
  and remain a separate follow-up PR.

## FR-SALE write slice (2026-08-02 — ADR-015 §5/§6)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/offers/{offerId}/characteristics` | Product Configuration schema (AC-SALE-01-10/17/20/21) |
| POST | `/api/products` | Create one installation — main + children — as **PNDG**, in one local transaction |
| POST | `/api/products/confirm` | PNDG → ACTV. Idempotent |
| POST | `/api/products/cancel` | Compensation: soft-passivate **PNDG-only** products |

```jsonc
// POST /api/products
{
  "customerNumber": 1001,          // NOT stored — only used to validate the address
  "serviceAddressId": 1,
  "campaignId": "CMP-ADSL-01",     // optional, public code
  "items": [
    {"offerId": 1, "characteristics": [{"characteristicId": 1, "value": "16"}]},
    {"offerId": 2, "characteristics": [{"characteristicId": 3, "value": "AA:BB:CC:DD:EE:FF"}]},
    {"offerId": 3, "characteristics": []}
  ]
}
```

- **Main/child is derived, not declared:** the INTERNET-service offer becomes the
  main product and the only one carrying `service_address_id`; RESOURCE and
  ACTIVATION become its children (AC-SALE-01-09). A caller cannot violate the rule.
- **`serviceAddressId` must be one of `customerNumber`'s active addresses**
  (AC-SALE-01-11, ADR-015 §5.9) — checked through customer-service's
  `GET /api/customers/{n}/addresses`, the same rule account-service applies to
  billing addresses. Existence alone would not do: `prod.service_address_id` is
  rendered in the FR-PROD-02 modal, so an unvalidated id would display **another
  customer's address**. `customerNumber` is not stored (`PROD` has no customer
  column) and order-service takes it from the billing account, never from its
  client.
- **Basket composition and characteristic validation live here** (ADR-015 §6):
  `MSG-SALE-*` and `MSG-VAL-CHAR-*` are produced by this service and relayed by
  order-service unchanged.
- **PNDG products are invisible to FR-PROD-01/02** (§5.5): the Status column has two
  values and the mapper renders anything non-ACTV as "Passive", so a leaked PNDG row
  would show a product the customer never bought. Detail answers 404.
- `/cancel` accepts **PNDG only** → otherwise 409 `MSG-PROD-NOT-PENDING`. An endpoint
  able to passivate a committed product would be the KR-7 cancellation that is out of
  phase, arriving by the back door.
- A **full `LookupCatalogClient` boundary was built before the first write**
  (ADR-015 §4.1): no status is persisted without resolving it through the catalog
  owner, fail-closed (ADR-002 §7). Reads still use the local `LookupContract`
  constants, so a catalog outage stops sales without stopping product viewing.

## Deliberate limitations / future work (never silently faked)

- **No product cancellation** (KR-7, AC-PROD-01-04: the Action column offers only a
  view icon). `/cancel` is a compensation command restricted to never-committed PNDG
  rows — not the same thing.
- **No stuck-PNDG sweeper.** If a compensation itself fails, rows stay PNDG:
  invisible to the customer, identifiable by one query (`status_id = 6`), recorded as
  an operational follow-up (ADR-015 §8.4) rather than built speculatively.
- **No catalog CRUD:** no FR requires it, so none was invented.
- **Offer prices await analyst approval** (deviation 1).
- **Campaign activation-window rules** (`activation_end_date` enforcement) are
  not applied to any read: no FR/AC in §2.6 asks for it, and inventing a filter
  would silently hide catalog rows.
