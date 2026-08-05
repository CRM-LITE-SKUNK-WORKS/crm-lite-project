# ADR-013: account-service Boundary, Data Ownership and FR-ACCT API Contract

## Status
Accepted (2026-07-23) — implements the FR/AC v8-1 Final (23.07.2026) ACCT scope:
KR-11 + FR-ACCT-01..04. Companion: ADR-014 (Account Number generation), the
ADR-010 addendum (account-service → customer-service authentication) and the
sprint decision record `docs/architecture/account-service-decisions.md`
(analyst/team answers to the Phase 0 blocking questions; this ADR and ADR-014
are the long-term authorities, the sprint record only documents how the
questions were resolved).

**Amended 2026-08-01 (§3.6, §7, §8 — Proposed):** §5.2 promised that future
product/order services would reach `cust_acct_prod_invl` "exclusively through an
account-service command/API". §7 and §8 discharge that promise: §7 formalizes the
**read** side that shipped undocumented with the product-service slice
(2026-07-29), §8 decides the **write** command required by FR-SALE-01 (§2.7).
§3.6 adds `customerNumber` to the account representation. The FR-ACCT-01..04
contract in §1–§6 is otherwise unchanged. Companions: **ADR-015**
(product-service boundary), **ADR-016** (order-service boundary).

**Reviewed 2026-08-05 (FR/AC v8-2, 03.08.2026):** ACCT scope wording is unchanged
from v8-1 (v8-2's changes are scoped to SALE-01/SALE-02). **No change to this
decision.** See `docs/requirements/document-delta.md`.

## Context
FR v8-1 documents billing accounts (FR-ACCT-01..04) and the KR-11 Account
Number contract. The entity workbook defines `ACCT_TP`, `CUST_ACCT` and
`CUST_ACCT_PROD_INVL`. No account code exists anywhere; PROJECTBRAIN and
`docs/architecture/service-boundaries.md` list account-service as the next
approved Sprint domain, blocked on account-specific ADRs. This is that ADR.

## Decision

### 1. Deployable, database, ports, routing
1. **`backend/account-service`** is a separate deployable owning **`account_db`**,
   package root **`com.crm.account`**, internal port **8085**.
2. **No host-published port in any Compose profile** (ADR-009 unchanged). All
   client traffic enters through the gateway BFF via a new route
   `/api/accounts/**` → `lb://account-service` with `TokenRelay=` +
   `RemoveRequestHeader=Cookie`.
3. account-service is a **zero-trust JWT resource server** via
   `crm-security-starter` (ADR-009): signature/issuer/audience/`crm-user` role
   validated on every request; only `/actuator/health` and `/v3/api-docs/**`
   are anonymous (ADR-012 pattern).

### 2. Data ownership and external references
1. `account_db` owns exactly: `acct_tp`, `cust_acct`, `cust_acct_prod_invl`,
   `acct_number_seq` (the last is a project addition required by KR-11 — the
   workbook has no sequence table; recorded deviation).
2. **No local `gnl_st`/`gnl_tp`, no customer/address tables, no USERS table,
   no cross-database foreign keys** (ADR-002/011). `status_id` stores the
   central GNL_ST contract IDs (1=ACTV, 2=PASV, GENERAL domain), validated
   through lookup-service on writes (fail closed 503
   `MSG-SERVICE-UNAVAILABLE`); reads and lifecycle filters are fully local.
3. **`cust_acct.customer_number` stores the public business customer number**,
   not the internal `cust.id`. Recorded deviation from the workbook (which
   references `customer_id`): internal customer ids never leave
   customer-service's API, so they are not observable by another service; the
   business number is the only stable cross-service customer reference.
4. **`cust_acct.address_id` is an external reference** to a customer-service
   address (`addr.id`, the same identifier its public address API exposes as
   `addressId`). No FK; validated on create/update through the existing
   `GET /api/customers/{customerNumber}/addresses` endpoint — the requested
   id must be present in the returned **active** address list. No new
   customer-service endpoint is added; customer-service source is unchanged.
5. `acct_tp` is a **local, account-domain-owned type catalog** (not a shared
   GNL catalog): contract rows `id 1 = code 223 (Customer Account)`,
   `id 2 = code 224 (Billing Account)`, seeded by account-service's Flyway and
   never renumbered. `cust_acct.account_type_id` carries a normal local FK.

### 3. Public API contract (FR-ACCT-01..04)
Exactly five endpoints; no pagination, no extra filters, no bulk operations:

1. `GET /api/accounts?customerId={customerNumber}` — `customerId` required
   (missing/non-numeric → 400) and is always the public customer number.
   Returns a JSON **array** of **type-224 accounts only** (AC-ACCT-01-02),
   both Active and Passive (AC-ACCT-01-03), sorted Active first then Passive,
   `accountNumber` ascending inside each status group (AC-ACCT-01-04).
   No accounts (or unknown customer) → `200 []`; the read path performs no
   cross-service calls (reads stay local, ADR-002 spirit).
2. `POST /api/accounts` — body `{customerId, accountName, addressId}`; the
   account type is **not client-selectable** (forced 224). Validates the
   customer exists and is active (via customer-service; unknown → 404
   `MSG-CUST-NOT-FOUND`) and the address as in §2.4. Assigns the KR-11 number
   (ADR-014) and returns **201** with the representation. Runs as **one local
   ACID transaction**, including the K-8 side effect (§4).
3. `GET /api/accounts/{accountNumber}` — update-screen support endpoint;
   returns the full representation for Active **and** Passive 224 accounts
   (Passive rows stay visible per AC-ACCT-04-02/03). Unknown number → 404
   `MSG-ACCT-NOT-FOUND`.
4. `PUT /api/accounts/{accountNumber}` — mutable fields are **exactly**
   `accountName` and `addressId` (both required). `accountNumber` and the
   account type are immutable; a request carrying any unrecognized or
   immutable field is **rejected with 400 `MSG-ACCT-IMMUTABLE-FIELD`** —
   never silently ignored (strict deserialization; Spring's default
   ignore-unknown-properties behaviour is deliberately overridden for these
   DTOs). Updating a Passive account → 409 `MSG-ACCT-NOT-ACTIVE`.
5. `DELETE /api/accounts/{accountNumber}` — **soft passivation only**
   (AC-ACCT-04-02): `status_id → PASV` + deleted/updated audit metadata; the
   row remains list-visible as Passive; the number is never reused (KR-11).
   Active product involvement → 409 `MSG-ACCT-HAS-PRODUCTS` (AC-ACCT-04-03).
   Already-Passive account → 409 `MSG-ACCT-NOT-ACTIVE` (the record still
   exists and is visible; neither idempotent-204 nor 404 is honest).
   Success → **204 No Content**; `MSG-ACCT-DELETED` is shown by the frontend
   after the 204, and `MSG-ACCT-DELETE-CONFIRM` is frontend-only — the
   backend produces neither.

Responses expose `accountNumber, accountName, accountTypeCode,
accountTypeName, billingAddressId, accountStatus` ("Active"/"Passive").
They never expose internal ids (`cust_acct.id`, `acct_tp.id`), and there is
no API "Action" field (UI concern).

#### 3.6 `customerNumber` added to the representation (amendment, 2026-08-01)
The representation gains one **additive** field, `customerNumber` — the same
public business number the list endpoint already requires as its `customerId`
query parameter (§2.3). Rationale: order-service must record
`cust_ord.customer_number` (ADR-016 §2.3) but only knows the account it is
selling into, and `GET /api/accounts/{accountNumber}` is the natural,
already-authenticated precondition call for that flow — it also yields the
224/Active checks AC-SALE-02-01 needs. Exposing it leaks nothing new: it is a
public business number, not an internal id, and the caller must already hold an
account number belonging to that customer. The alternative — a dedicated
`/api/accounts/{n}/owner` endpoint returning a single field — was rejected as
endpoint sprawl for no privacy gain. **This changes a shipped response shape**,
additively only (no field removed or renamed), so existing consumers are
unaffected.

### 4. Automatic 223 Customer Account (K-8, approved)
Per the approved use-case flow (FR-ACCT-02 steps 8–8.3):

1. When a customer's **first** 224 Billing Account is created and the customer
   has no 223 Customer Account yet, the system creates the 223 **inside the
   same local ACID transaction**, before the 224.
2. The 223 receives a real, unique KR-11 number from the **same generator and
   sequence** as 224 accounts (KR-11's `[T]` digit encodes the customer
   segment, not the account type).
3. `account_name` is the fixed system constant **"Customer Account"** (not
   user-editable, never surfaced in UI lists); `address_id` is the customer's
   **primary active address**, resolved through the same customer-service
   address lookup used to validate the 224's address.
4. **At most one 223 per customer**, enforced by a partial unique index on
   `cust_acct (customer_number) WHERE account_type = 223 AND deleted_date IS
   NULL` (which deliberately does not constrain 224 rows).
5. The 223 is a pure side effect: **no endpoint creates, lists, updates or
   deletes a 223 directly.** It never appears in `GET /api/accounts`
   (224-only list), and `GET/PUT/DELETE /api/accounts/{accountNumber}` treat a
   223's number as **404 `MSG-ACCT-NOT-FOUND`**. It is immutable once created
   and follows the same soft-passivation rules as any account row (relevant
   only to future cross-domain flows such as customer deletion).

### 5. Product involvement ownership
1. `cust_acct_prod_invl` is **owned and written only by account-service** and
   is real, queried local state: an account has an active product involvement
   iff a row exists with `status_id = ACTV AND deleted_date IS NULL`. This is
   the **sole source of truth** for the AC-ACCT-04-03 delete guard.
2. Until product-service exists the table is populated only by seed/test data.
   **Documented follow-up TODO (not implemented, never faked):** future
   order/product services populate and maintain this projection exclusively
   through an account-service command/API or an event consumed by
   account-service — they must never write `account_db` directly, and this
   sprint deliberately implements **no** live cross-service product call
   (mirroring customer-service's conscious-TODO pattern for its own
   cross-domain checks).

### 6. Message keys
Analyst catalog keys used: `MSG-ACCT-HAS-PRODUCTS` (409). Frontend-only
catalog keys (never produced by the backend): `MSG-ACCT-DELETE-CONFIRM`,
`MSG-ACCT-DELETED`. Documented **project additions** (EN/TR texts in
`docs/architecture/account-service-decisions.md`):

| Key | HTTP | Meaning |
|---|---|---|
| `MSG-ACCT-NOT-FOUND` | 404 | No visible account with that number |
| `MSG-ACCT-NOT-ACTIVE` | 409 | Update/delete refused: account is Passive |
| `MSG-ACCT-IMMUTABLE-FIELD` | 400 | Request carried immutable/unknown fields |
| `MSG-ACCT-DUP-NUMBER` | 409 | Account-number uniqueness race (DB constraint) |
| `MSG-ACCT-NUMBER-CAPACITY-EXCEEDED` | 409 | KR-11 sequence exhausted for segment+year |

Plus the established shared keys: `MSG-VALIDATION-ERROR`,
`MSG-CUST-NOT-FOUND`, `MSG-SERVICE-UNAVAILABLE` (lookup **or**
customer-service unreachable during a write; fail closed, nothing persisted),
`MSG-INTERNAL-ERROR`, `MSG-AUTH-UNAUTHORIZED`, `MSG-AUTH-FORBIDDEN`.

### 7. Read side of the involvement projection (amendment, 2026-08-01)
Shipped 2026-07-29 with the product-service read-only slice but never recorded
architecturally (the ADR debt named in PROJECTBRAIN §9.1b). It is hereby part of
the boundary, not an undocumented convenience:

1. **`GET /api/accounts/{accountNumber}/product-ids` → `200 [Long]`** is the
   **single public reading point** of `cust_acct_prod_invl`. No other service
   reads `account_db` — not through a replica, not through a shared schema, not
   through a direct JDBC connection.
2. It returns `product_id` for rows with **`deleted_date IS NULL`**, ascending.
   It is **deliberately not filtered by the involvement's own `status_id`**:
   AC-PROD-01-03 lists both Active and Passive products and the *displayed*
   status is the product's, not the involvement's. The ACTV-only filter stays
   exclusive to the AC-ACCT-04-03 delete guard (§5.1) — the two must not be
   conflated.
3. Visibility follows §3.3/§4.5 exactly: **224 only**, so an unknown number and a
   K-8 223's number are both `404 MSG-ACCT-NOT-FOUND`; a **Passive** 224 stays
   readable (AC-ACCT-04-02). An account with no involvements is `200 []`, never
   404.
4. It is reached **directly via Eureka (`lb://account-service`) with the user's
   token propagated** (ADR-010) — never through the gateway, which is the browser
   edge (ADR-007). It is nonetheless matched by the existing `/api/accounts/**`
   gateway route; accepted, because it exposes only ids and demands the same
   `crm-user` JWT as every other account endpoint.
5. This endpoint carries **no product semantics**. It does not know what a
   product is, whether it is active, or what it costs; product-service joins the
   returned ids against `product_db` itself (ADR-015 §3).

### 8. Write side — the involvement command (amendment, 2026-08-01)
§5.2 deferred this ("a future account-service command/API or an event consumed
by account-service"). FR-SALE-01 forces the decision. **A synchronous REST
command is chosen; no event, no broker** — the platform has no message broker and
one is not being introduced for a single request-bound hop, which would trade a
two-hour integration for an infrastructure component, an outbox, and an
eventual-consistency window the sale flow cannot observe anyway (the user is
waiting on the HTTP response).

1. **`POST /api/accounts/{accountNumber}/product-involvements`**, body
   `{"productIds": [21, 22, 23]}` → **`201`** with the resulting id list.
   The call is **bulk and single-shot**: one call per sale, never N calls, so the
   whole projection update is one local ACID transaction in `account_db` and a
   partial link set can never be observed.
2. **Target validation** — the same visibility rules as everywhere else, plus an
   activity rule the read side does not have:
   - unknown number, or a K-8 223's number → **404 `MSG-ACCT-NOT-FOUND`**;
   - a **Passive** 224 → **409 `MSG-ACCT-NOT-ACTIVE`**. A Passive account is
     readable (§3.3) but must never acquire new products; this is the
     server-side enforcement of AC-SALE-02-01, which the UI states only as a
     disabled action.
3. **What is written:** one `cust_acct_prod_invl` row per product id, with
   `short_code = 'ACCT_PROD'` — verified against the V1 DDL default and every
   V2/V3/V4 seed row: it is the workbook's per-table short-code constant (the
   same convention as `acct_tp.short_code`), **not** a campaign or offer code.
   Nothing else may be inferred into it. `status_id` is `ACTV`, resolved through
   lookup-service, **failing closed with 503 `MSG-SERVICE-UNAVAILABLE`** if the
   catalog is unreachable and uncached (ADR-002 §7) — nothing is persisted.
4. **Idempotent per (account, product):** if a non-deleted row already exists for
   that pair it is left untouched rather than duplicated or rejected. The caller
   is a compensating orchestrator (ADR-016 §5) that may retry; duplicate
   involvement rows would corrupt the AC-ACCT-04-03 guard's meaning.
5. **`product_id` is accepted as an opaque external reference.** account-service
   does **not** call product-service to verify it — that would invert the
   dependency direction (product-service already depends on account-service, §7)
   and create a call cycle. The caller owns the guarantee that the ids exist;
   account-service owns only the projection. This is a deliberate, recorded
   trade-off, symmetrical to `customer_number`/`address_id` being FK-less
   external references (§2.3/§2.4).
6. **No involvement *delete* command is added.** Nothing in FR-SALE or KR-7
   removes a product from an account — KR-7 leaves product cancellation out of
   phase entirely. In ADR-016 §5 the involvement write is the **commit point** of
   the sale: every step that can still fail *before* it is compensated by
   discarding never-committed product rows, and the one step *after* it
   (activating those rows, §5 step 4) is a status refinement whose failure does
   not un-sell the order and therefore never needs the involvement rolled back.
   Adding a delete command would create a write path with no requirement behind
   it — and, being indistinguishable from the KR-7 cancellation that is out of
   phase, it would be the wrong thing to have lying around.

## Consequences
- customer-service keeps its documented 501/no-op TODOs (`accountNumber`
  search, active-product guard, billing-account passivation on customer
  delete); converting them to real calls against this service is future work
  and requires no contract change here.
- The workbook deviations (customer_number instead of customer_id;
  regenerated KR-11 seed numbers; the added `acct_number_seq` table; the fixed
  223 account-name constant) are recorded here and in
  `docs/requirements/document-delta.md` — the workbook itself is never edited.
- Future services needing account-product involvement integrate through
  account-service's boundary (§5), keeping `account_db` single-writer.
- **(2026-08-01)** With §7 and §8 in place, `cust_acct_prod_invl` is fully
  boundary-served in both directions and §5.2's "documented follow-up TODO" is
  discharged: the projection is no longer seed-only, and `account_db` remains
  single-writer with the write path now exercised by a real caller
  (order-service, ADR-016) rather than by fixtures.
- **(2026-08-01)** account-service gains **no** dependency on product-service or
  order-service. Its outbound dependency set is unchanged (lookup-service,
  customer-service), so the service graph stays acyclic: order → account,
  order → product, product → account, product → customer, account → customer,
  account → lookup.
- **(2026-08-01)** The AC-ACCT-04-03 delete guard becomes reachable in real use
  for the first time: an account that has just been sold into can no longer be
  passivated (409 `MSG-ACCT-HAS-PRODUCTS`). That is the intended requirement, but
  it was previously only ever triggered by seed data.
