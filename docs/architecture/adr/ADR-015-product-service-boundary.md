# ADR-015: product-service Boundary, the FR-PROD Composition Pattern and the FR-SALE Write Slice

## Status
Accepted (2026-08-01) — approved by the team before implementation began.

Two things are decided here in one document, because they are the same boundary:

1. **Retroactive**: the read-only FR-PROD-01..02 slice shipped 2026-07-29 with
   **no ADR at all**. `docs/api/product-service.md`, PROJECTBRAIN §9.1b and
   `service-boundaries.md` all record this as explicit, outstanding ADR debt.
   §1–§3 below record what shipped and *why*, so the composition pattern is
   architecturally approved rather than merely observed in code.
2. **New**: the **write slice** required by FR-SALE-01 (§2.7 Product
   Configuration → Submit Order) — product creation, characteristic values, and
   the two-phase reservation the sale orchestration needs (§4–§7).

Companions: **ADR-002** (shared catalogs), **ADR-009** (zero-trust),
**ADR-010** (service-to-service auth), **ADR-013 §5/§7/§8**
(`cust_acct_prod_invl` single-writer, read and write commands),
**ADR-016** (order-service boundary and the sale orchestration that drives §5–§7).

## Context

FR §2.6 (FR-PROD-01..02) lists and details a customer's products; FR §2.7
(FR-SALE-01) creates them. The entity workbook gives the product domain ten
tables (`PROD_SPEC`, `PROD_OFR`, `CMPG`, `CMPG_PROD_OFR`, `PROD`,
`PROD_SPEC_CHAR`, `PROD_SPEC_CHAR_USE`, `PROD_CHAR_VAL`, `PROD_CATAL`,
`PROD_CATAL_PROD_OFR`) and one structural surprise that shapes everything below:

> **`PROD` has no customer column and no account column.** The product ↔ billing
> account link exists only in `account_db.cust_acct_prod_invl`, a table
> account-service owns and is the sole writer of (ADR-013 §5).

FR §2.6 never states how a product links to an account, yet FR-PROD-01 lists
products *per account* — recorded as open conflict #11 in
`docs/requirements/document-delta.md`. That gap is what §3 answers.

The 2026-07-29 slice is read-only by design and carries a standing warning: it
has **no lookup HTTP client**, because no write needed a live catalog resolve.
§4.1 discharges that warning before the first line of write code.

## Decision

### 1. Deployable, database, ports, routing
1. **`backend/product-service`** is a separate deployable owning **`product_db`**,
   package root **`com.crm.product`**, internal port **8086**.
2. **No host-published port in any Compose profile** (ADR-009 unchanged). Client
   traffic enters through the gateway BFF via `/api/products/**`,
   `/api/offers/**`, `/api/campaigns/**` with `TokenRelay=` +
   `RemoveRequestHeader=Cookie`.
3. product-service is a **zero-trust JWT resource server** via
   `crm-security-starter` (ADR-009); only `/actuator/health` and
   `/v3/api-docs/**` are anonymous (ADR-012 pattern).

### 2. Data ownership and external references
1. `product_db` owns exactly the ten workbook tables listed above, physical names
   lowercase, and **nothing else**.
2. **No local `gnl_st`/`gnl_tp`, no customer/account/order tables, no
   cross-database foreign keys** (ADR-002). `status_id`,
   `prod_spec.service_type_id` and `prod_catal.catalog_type_id` store central
   catalog contract IDs as external references. An integration test asserts over
   `information_schema.columns` that no account or customer column exists — that
   test is a boundary guard and must not be relaxed.
3. `prod.service_address_id` is an **FK-less external reference** into
   `customer_db` (`addr.id`, the same value the address API exposes as
   `addressId`), resolved through customer-service's internal
   `GET /api/addresses/{addressId}` with the user's token propagated (ADR-010).
4. `parent_prod_id` and `parent_offer_id` are **local self-FKs**; every other
   relation carries a normal local FK.
5. The **public product identifier is `prod.id`.** No KR-11-style business number
   is invented for products: no FR asks for one, and inventing an identifier
   scheme is an analyst decision, not a technical one. (Orders *do* get one —
   ADR-016 §4 — because AC-SALE-01-12 displays an Order ID.)
6. The **public campaign identifier is `cmpg.campaign_code`** (e.g.
   `CMP-ADSL-01`); the internal `cmpg.id` never leaves the service.

### 3. Read side: FR-PROD-01 is a composition, not a join (records what shipped)
1. Because of the missing account column (§Context), **`GET /api/products?
   accountNumber=` calls account-service's `GET /api/accounts/{n}/product-ids`**
   (ADR-013 §7) and joins the returned ids locally. The call goes **directly via
   Eureka (`lb://account-service`) with the user's token propagated** (ADR-010),
   **never through the gateway** — the gateway is the browser edge (ADR-007), not
   an internal hop.
2. **product-service never reads and never writes `account_db`.** Three
   alternatives were rejected: (a) adding an `account_id` column to `PROD` —
   invents a workbook column and creates two sources of truth for the same link;
   (b) reading `account_db` directly — breaks ADR-013 §5 and the
   one-database-per-service rule; (c) duplicating the involvement projection into
   `product_db` — a replication problem with no requirement behind it.
3. Upstream unavailability **fails closed**: 503 `MSG-SERVICE-UNAVAILABLE`. A
   partial or fabricated product list is never returned.
4. Derived, never stored: service type via `PROD_SPEC.service_type_id` (the offer
   has no service-type column); campaign total price as the sum of member offer
   prices (`CMPG` gets no price column); product status label from the local
   soft-delete invariant.
5. FR-PROD-02's Service Address rule: `prod.service_address_id` is filled **only
   on the main product** of an installation, so a child product displays **its
   parent's** address by walking the parent chain upwards. A vanished/soft-deleted
   address yields `serviceAddress: null` (the modal still renders); an
   **unreachable** customer-service is a 503.
6. **No pagination** on `GET /api/products` — FR-PROD-01 states no pagination
   rule (unlike KR-04 for customers), so the full list is returned.

### 4. Write slice — prerequisites

#### 4.1 The lookup client boundary is built first (blocking)
The write slice persists `status_id` values, so ADR-002 §5/§7 apply in full.
Before any write code exists, product-service gains the complete
`com.crm.product.lookup` boundary in the shape customer-service and
account-service already use — `LookupCatalogClient` (HTTP) →
`LookupCatalogService` (domain validation + bounded TTL cache + `LookupContract`
contract-ID assertion) — with **fail-closed 503 `MSG-SERVICE-UNAVAILABLE`** when
the catalog is unreachable and the value is uncached. The existing
`LookupContract` constants class stays and is extended, not replaced.
**Reads are unaffected**: active-record filtering remains fully local
(ADR-002 §8), so no query performs a remote call per row or per request.

#### 4.2 New catalog contract values used by the write slice
From the central GNL_ST catalog (ADR-002 — **no local copy, no local seed**):
`PNDG` (id 6, domain `PROD`) joins the already-used `ACTV` (1) and `PASV` (2).
`PNDG` is a workbook row that has existed since day one and has never been
used by any code; §5 gives it a meaning for the first time. **No new catalog row
is invented.**

### 5. Write slice — product creation (two-phase, PNDG-first)

1. **`POST /api/products`** creates one whole installation in **one local ACID
   transaction** in `product_db`. It is bulk by design — the sale creates a main
   product and its children together, and a per-product endpoint would make a
   partially-created installation observable.
   Request: the billing context (`serviceAddressId`, optional public
   `campaignId`) plus one entry per basket offer, each carrying its raw
   characteristic values. Response: one row per created product
   (`offerId → productId`, plus the offer's name and price snapshot the caller
   needs for §6 of ADR-016).
2. **Every created `PROD` row gets `status_id = PNDG`**, and so do its
   `PROD_CHAR_VAL` rows. Semantics: *reserved, not yet committed.* A PNDG product
   is invisible to FR-PROD-01/02 (§5.5), so a sale that dies mid-flight leaves
   nothing the customer can see.
3. **Parent/child structure follows AC-SALE-01-09, derived not declared:** the
   offer deriving from the **INTERNET** service type becomes the **main** product
   and receives `service_address_id`; the RESOURCE and ACTIVATION offers become
   children with `parent_prod_id` pointing at it and a NULL
   `service_address_id` (§3.5 then resolves their address through the parent).
   The client does not get to declare which product is the main one — the service
   type decides, so the FR rule cannot be violated by a caller.
4. **`prod.transaction_id` is left NULL** — see §8.3.
5. **PNDG rows are invisible to the read side.** `GET /api/products` skips them
   (they have no involvement row yet either, so this is defence in depth), and
   `GET /api/products/{id}` answers **404 `MSG-PROD-NOT-FOUND`** for a PNDG
   product. This is a *deliberate* read-side change: FR-PROD-01's Status column
   has exactly two values (Active/Passive) and the existing mapper renders
   anything non-ACTV as "Passive", so a leaked PNDG row would display as a
   passive product the customer never bought. Hiding it is the honest rendering;
   inventing a third status label would be inventing a requirement.
6. **`POST /api/products/confirm`** promotes the given PNDG products (and their
   characteristic values) to `ACTV`. **Idempotent**: ids already ACTV are left
   untouched; an unknown id is `404 MSG-PROD-NOT-FOUND`.
7. **`POST /api/products/cancel`** is the compensation counterpart: it
   **soft-passivates** the given products with the full invariant
   (`status_id = PASV` + `deleted_date`/`deleted_by`), cascading to their
   `PROD_CHAR_VAL` rows. **It accepts PNDG products only** — a non-PNDG id is
   **409 `MSG-PROD-NOT-PENDING`**. This guard matters: an endpoint that could
   passivate a committed product would be exactly the product cancellation KR-7
   puts out of phase, arriving through the back door. Physical deletion is never
   used (project-wide rule).
9. **The service address is validated against the customer who owns it**
   (AC-SALE-01-11). `POST /api/products` therefore requires `customerNumber` — a
   public business number, **not stored** (`PROD` has no customer column, ADR-013 §5)
   and used only for this check: the submitted `serviceAddressId` must appear in
   that customer's **active** address list, fetched through customer-service's
   `GET /api/customers/{n}/addresses`. Same rule, same endpoint and same failure
   (400 `MSG-VALIDATION-ERROR`) account-service applies to billing addresses
   (ADR-013 §2.4). customer-service unreachable → 503, fail closed.

   Checking mere existence through the `GET /api/addresses/{id}` endpoint §3.5
   already uses would **not** be enough: *"does address 7 exist?"* cannot answer
   *"does address 7 belong to **this** customer?"*. Since `prod.service_address_id`
   is an FK-less reference that FR-PROD-02 renders in the detail modal, an
   unvalidated id would let a sale attach **another customer's address** to a
   product and display it back — a cross-customer data leak, not a cosmetic gap.

   **`customerNumber` comes from the billing account, never from the client's sale
   request** (ADR-016 §5.1 step 0 reads it). A caller able to supply it would simply
   claim the customer that owns the address it wanted, and the check would validate
   nothing.

   *Added 2026-08-02, after the first implementation.* The initial cut stored
   `serviceAddressId` verbatim. The gap surfaced while re-reading §2.7 against the
   built code — recorded here rather than quietly patched.
10. These three endpoints are **service-to-service commands** invoked by
   order-service over Eureka with the user's token propagated (ADR-010). They are
   matched by the existing `/api/products/**` gateway route and require the same
   `crm-user` JWT as every other endpoint; that is accepted for the same reason
   ADR-013 §7.4 accepts it for `product-ids`.

### 6. Basket and characteristic validation live here

**Characteristic schema is served here** (`PROD_SPEC_CHAR`,
`PROD_SPEC_CHAR_USE`, `PROD_CHAR_VAL` finally get an endpoint — AC-SALE-01-10/17/
20/21): per offer, the characteristic fields with `name`, `dataType`
(`NUMBER|BOOLEAN|TEXT|DATE`) and `mandatory`. An offer with no characteristics
returns `200 []` — AC-SALE-01-21 requires the product block to render without
fields, which is a frontend concern given an empty list.

**Validation on `POST /api/products`, all producing 400:**

| Rule | Source | Key |
|---|---|---|
| `serviceAddressId` is not one of the customer's active addresses | AC-SALE-01-11 | `MSG-VALIDATION-ERROR` (§5.9) |
| A mandatory characteristic is missing/blank | AC-SALE-01-18 | `MSG-VAL-CHAR-REQUIRED` |
| A value does not parse as its `data_type` | AC-SALE-01-19 | `MSG-VAL-CHAR-FORMAT` |
| A submitted offer is not Active | AC-SALE-01-08 | `MSG-SALE-OFFER-INACTIVE` |
| Zero INTERNET / RESOURCE / ACTIVATION offers | AC-SALE-01-08 | `MSG-SALE-NO-INTERNET` / `-NO-RESOURCE` / `-NO-ACTIVATION` |
| More than one of a service type | AC-SALE-01-08 | `MSG-SALE-MULTI-INTERNET` / `-MULTI-RESOURCE` / `-MULTI-ACTIVATION` |
| The same offer submitted twice | AC-SALE-01-05 | `MSG-SALE-DUP-OFFER` |

**Why the basket-composition rules (AC-SALE-01-08) are validated here and not in
order-service.** They are questions only this service can answer — "is this offer
active?" and "which service type does it derive from?" are `PROD_OFR`/`PROD_SPEC`
facts. Putting them in order-service would mean either replicating the catalog or
adding a second round trip to fetch it, and order-service would still have to
trust the answer. Validating at the point of persistence also makes the rule
unbypassable: the FR states these checks as an Offer-Selection screen behaviour
(LBL-NEXT), and a screen-only check is not a check — the same request could be
posted directly. order-service therefore forwards the basket verbatim and writes
**no** validation logic of its own; it maps the resulting error through
unchanged.

**Campaign coherence:** if `campaignId` is supplied it must name an Active
campaign and every submitted offer must be one of its members (otherwise 400
`MSG-VALIDATION-ERROR`). AC-SALE-01-04 adds campaign offers as a set, so a basket
claiming a campaign it does not match is a client defect, not a discount.

### 7. Message keys

From the analyst catalog (§2.7 validation table), produced by product-service:
`MSG-SALE-OFFER-INACTIVE`, `MSG-SALE-NO-INTERNET`, `MSG-SALE-NO-RESOURCE`,
`MSG-SALE-NO-ACTIVATION`, `MSG-SALE-MULTI-INTERNET`, `MSG-SALE-MULTI-RESOURCE`,
`MSG-SALE-MULTI-ACTIVATION`, `MSG-SALE-DUP-OFFER`, `MSG-VAL-CHAR-REQUIRED`,
`MSG-VAL-CHAR-FORMAT` — all **400**.

Frontend-only, never produced by this backend: `MSG-PROD-NONE`,
`MSG-SALE-ORDER-CONFIRM`.

Documented **project additions**:

| Key | HTTP | Meaning |
|---|---|---|
| `MSG-PROD-NOT-FOUND` | 404 | Unknown product id — or a PNDG one (§5.5) |
| `MSG-PROD-NOT-PENDING` | 409 | `cancel` refused: the product is not PNDG (§5.7) |

Plus the established shared keys: `MSG-VALIDATION-ERROR`,
`MSG-ACCT-NOT-FOUND` (unknown `accountNumber` on the list), `MSG-SERVICE-UNAVAILABLE`
(account-service, customer-service **or** lookup-service unreachable — fail
closed, nothing persisted; customer-service is now on the **write** path too, §5.9),
`MSG-INTERNAL-ERROR`, `MSG-AUTH-UNAUTHORIZED`, `MSG-AUTH-FORBIDDEN`.

### 8. Recorded deviations and open questions

1. **Offer prices remain invented fixtures** (`document-delta.md` P1/P5, open
   conflict #10) — **analyst approval still pending**. The write slice now makes
   them load-bearing: they are snapshotted into `cust_ord_item` (ADR-016 §2.4),
   so the provisional values become persisted order history. Recorded, not
   silently upgraded to fact.
2. **`PROD.transaction_id` stays NULL.** It is empty in every workbook row and no
   FR/AC defines it. Its only plausible meaning is a `BSN_INTER` reference — but
   that would be a `product_db → order_db` cross-database reference in the
   *opposite* direction to the one that already exists
   (`cust_ord_item.product_id`), giving two unsynchronised links for one
   relationship. **Open analyst question**, not a technical blocker: leaving it
   NULL loses nothing, because order → product is already navigable.
3. **PNDG is used for the first time.** The workbook reserves it (GNL_ST id 6,
   domain `PROD`, "Pending") and nothing has ever written it. §5 gives it the
   meaning "reserved by an in-flight sale, not yet committed". No new catalog row
   is created, but the *semantics* are a project decision — flagged for analysts.
4. **A stuck PNDG row has no automatic sweeper.** If ADR-016 §5 step 4 fails
   after the sale committed, products stay PNDG while their involvement rows
   exist: invisible to the customer (§5.5) yet counted by the AC-ACCT-04-03
   delete guard. Bounded, logged and identifiable by a single query
   (`status_id = PNDG`); a reconciliation job is **out of scope** for this PR and
   recorded as an operational follow-up rather than built speculatively.
5. **Campaign activation windows are still not enforced** on any read
   (`cmpg.activation_end_date`). §2.6 asks for no such filter and inventing one
   would silently hide catalog rows. The write path does check that a claimed
   campaign is **Active** (§6), which is a status check, not a date check.

## Consequences

- The composition pattern (§3) is now approved rather than merely shipped, and
  the ADR debt recorded in PROJECTBRAIN §9.1b and `service-boundaries.md` for the
  product domain is discharged. Open conflict #11 remains an **analyst** gap (the
  FR still never says how a product links to an account); it is answered
  architecturally, not by editing the workbook.
- product-service acquires its first write path and, with it, its first hard
  dependency on lookup-service (§4.1). Its read paths stay local and unaffected —
  a lookup outage degrades sales, never product viewing.
- The service graph stays acyclic: order → product, order → account,
  product → account, product → customer, account → customer, account → lookup,
  product → lookup. account-service gains **no** dependency on product-service
  (ADR-013 §8.5), so the "who validates the product id" question is answered by
  the caller, not by a back-reference.
- Basket rules living here (§6) means order-service holds **no** catalog
  knowledge: it never learns what an offer costs until product-service tells it,
  and it can never be the reason an invalid basket is persisted.
- The two-phase PNDG protocol is what lets ADR-016 §5 compensate without
  distributed transactions: everything created before the commit point is
  discardable by construction, and the discard path can never touch a real
  product (§5.7).
