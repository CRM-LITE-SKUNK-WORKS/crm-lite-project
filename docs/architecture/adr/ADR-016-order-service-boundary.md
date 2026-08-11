# ADR-016: order-service Boundary, Order Number Generation and Sale Orchestration

## Status
Accepted (2026-08-01) — approved by the team before implementation began.
Implements the FR/AC v8-1 Final (23.07.2026) SALE scope: **FR-SALE-01,
FR-SALE-02** (§2.7 Ürün Satışı), covering the Offer Selection → Product
Configuration → Submit Order flow's backend.

**Reviewed 2026-08-05 (FR/AC v8-2, 03.08.2026):** the revision simplified
AC-SALE-02-01's wording (single Active-account condition, no more "offered AND
active") and rewrote the three basket-validation messages as explicit error
conditions (message keys unchanged). Both already match this ADR's §5 wording —
**no change to this decision.** See `docs/requirements/document-delta.md`.

Companions: **ADR-013 §3.6/§7/§8** (the account representation and the
involvement read/write commands this service consumes), **ADR-015** (the product
write slice and where basket/characteristic validation lives), **ADR-014** (the
KR-11 generator this ADR's §4 copies), **ADR-002** (shared catalogs),
**ADR-009/010/012** (zero-trust, service-to-service auth, unified Swagger).

**Amended 2026-08-06 (§10 — Idempotency addendum):** §5.3b already recorded a real
production incident — httpclient5's default retry strategy silently re-executing a
503'd `POST /api/products` and producing two orders from one client request — and
disabled transport-level retries as the fix. That closed the SERVER's own retry
hazard but left the CLIENT's: a client that never sees the response (a timeout, a
dropped connection) has no safe way to know whether the sale happened, and the
project-wide rule against automatic retries on unsafe writes (unchanged, still
applies) means the client cannot just try again blindly. §10 adds an
`Idempotency-Key` foundation to `POST /api/orders` that answers exactly that
question — closing the gap from the OTHER side of the same incident §5.3b already
fixed. No Kafka, no Debezium, no asynchronous processing or saga state is
introduced; the synchronous orchestration in §5 is otherwise unchanged.

> ### Addendum 2026-08-10 — partially superseded by ADR-018
>
> **This document is preserved as written and remains the record of the synchronous
> design.** `POST /api/orders` and everything §5 describes still exist in the build,
> deprecated, as the rollback route and for the currently merged frontend. Three parts of
> it are no longer current, and all three are changed by **ADR-018**, not by editing here:
>
> - **§8.3 — "the Order Number exists only after submission" is SUPERSEDED
>   (ADR-018 §1.1).** The analyst clarified on 2026-08-10 that the records already exist
>   when the user starts the process, with a status that is not yet MIDLWARE, and that the
>   Order ID may therefore be displayed at that point. The workbook's existing
>   `GNL_ST WAIT` is that status. §8.3's two objections were real and are answered in
>   ADR-018 and in `document-delta.md`, not waved away.
> - **§6 — "WAIT is never written" is SUPERSEDED** for the same reason. `CANCELLED`
>   remains system-only, and there is still no user-facing order cancellation (KR-7).
>   `GNL_TP TRANSFER`/`CANCEL` remain unwritten: out of scope by analyst decision.
> - **§4 — KR-12's status is CORRECTED (ADR-018 §1.2).** It is no longer "a
>   project-proposed rule awaiting analyst sign-off". The analyst set the *requirement*
>   (the Order ID must be unique) and left the rule to the technical team, so the format
>   below is a **project technical choice that satisfies an analyst requirement**. The
>   algorithm is unchanged; only its status is.
>
> **§5's orchestration is superseded as the LIVE flow** by ADR-018's saga, which also
> reverses the activation/involvement ordering (§5 activates then links; the saga links
> then activates, so a failure has something it can cleanly undo). §5's own honest
> admission — that a failing compensation is "logged and swallowed" — is what ADR-018
> closes.

## Context

FR §2.7 defines the only multi-service write flow in the system. The entity
workbook gives the order domain three tables:

| Workbook | Columns |
|---|---|
| `BSN_INTER` (Is Etkilesimi) | `id, customer_account_id, bsn_inter_type_id, description, status_id, <audit>` |
| `CUST_ORD` (Siparis Basligi) | `id, order_number, customer_id, bsn_inter_id, status_id, <audit>` |
| `CUST_ORD_ITEM` (Siparis Kalemi) | `id, cust_ord_id, product_offer_id, product_id, status_id, <audit>` |

Four properties of this flow drive every decision below:

1. **It writes to three databases** — `order_db`, `product_db`, `account_db` —
   and the platform has **no distributed transaction** and **no message broker**.
2. **The product ↔ account link is not ours to write.** `cust_acct_prod_invl`
   belongs to account-service and only account-service writes it (ADR-013 §5).
3. **`CUST_ORD_ITEM.product_id` points at a product that does not exist yet** when
   the order header is written. The workbook's own column layout forces an
   ordering problem into the design (§5.2).
4. **The workbook supplies no order-number rule** — `CUST_ORD.order_number` is
   the bare integer `5001` in the seed, while AC-SALE-01-12 displays an "Order ID"
   to the user. KR-11 exists for accounts; nothing equivalent exists for orders.

The FR is also silent or contradictory in places that this ADR records rather
than resolves silently (§8).

## Decision

### 1. Deployable, database, ports, routing
1. **`backend/order-service`** is a separate deployable owning **`order_db`**,
   package root **`com.crm.order`**, internal port **8087**. It is built by
   copying the `backend/product-service` module skeleton verbatim — POM,
   Dockerfile, `common/**`, `OpenApiConfig`, the external-client pattern, the
   Testcontainers harness — because a new service inventing its own conventions
   is how a monorepo rots.
2. **No host-published port in any Compose profile** (ADR-009 unchanged). Client
   traffic enters through the gateway BFF via a new route `/api/orders/**` →
   `lb://order-service` with `TokenRelay=` + `RemoveRequestHeader=Cookie`.
3. order-service is a **zero-trust JWT resource server** via
   `crm-security-starter` (ADR-009); only `/actuator/health` and
   `/v3/api-docs/**` are anonymous (ADR-012 four-step checklist applies).

### 2. Data ownership and external references

1. `order_db` owns exactly: `bsn_inter`, `cust_ord`, `cust_ord_item`, and
   `order_number_seq` (a project addition required by §4, exactly parallel to
   ADR-014's `acct_number_seq`; the workbook has no sequence table — recorded
   deviation).
2. **No local `gnl_st`/`gnl_tp`, no customer/account/product tables, no
   cross-database foreign keys** (ADR-002). `status_id` and
   `bsn_inter.bsn_inter_type_id` store central catalog contract IDs as external
   references. An integration test asserts over `information_schema` that no
   `gnl_*` table exists here.
3. **`cust_ord.customer_number` replaces the workbook's `customer_id`**, and
   **`bsn_inter.customer_account_number` replaces the workbook's
   `customer_account_id`**. Recorded deviations, both applying the identical
   reasoning ADR-013 §2.3 used for `cust_acct.customer_number`: internal ids never
   leave the service that owns them, so they are not observable here; the public
   business number (customer number, KR-11 account number) is the only stable
   cross-service reference. FK-less, by design.
4. **Amount snapshot columns are added** (project addition, not in the workbook):
   `cust_ord_item.amount NUMERIC(12,2)` and `cust_ord.total_amount NUMERIC(12,2)`.
   AC-SALE-01-12 requires per-offer amounts and a Total Amount, and
   `PROD_OFR.product_offer_total_price` is a *current catalog price* — reading it
   back later would silently rewrite the history of a past order whenever a price
   changes. An order's amount is a fact about the moment it was placed.
   **Same "analyst approval pending" status as the invented prices themselves**
   (`document-delta.md` P1/P5, open conflict #10): the columns are sound, the
   values flowing into them are provisional.
5. **`cust_ord_item.product_id` and both amount columns are NULLable** — see
   §5.2, which is the only reason.
6. **No basket/cart table exists.** The basket is entirely frontend/session
   state; the backend learns about a sale exactly once, at Submit, as one
   request body. This is what makes AC-SALE-01-16 ("an abandoned sale must never
   be processed later") true by construction rather than by a cleanup job: there
   is nothing to abandon. It also means LBL-PREVIOUS (AC-SALE-01-13/14) is a pure
   frontend concern.

### 3. Public API contract

Two endpoints. Nothing else, and in particular **no user-facing order-cancel
endpoint** — KR-7 leaves cancellation out of phase and no AC moves an order out
of its created status (§6).

1. **`POST /api/orders`** — the single Submit-Order command (AC-SALE-01-15).
   Body: `accountNumber` (the KR-11 billing account the sale started from,
   AC-SALE-01-01), `serviceAddressId` (AC-SALE-01-11), optional public
   `campaignId`, and `items[]` — one entry per basket offer carrying `offerId`
   and its raw characteristic values. Returns **201** with the order
   representation.

**The representation carries exactly what `order_db` owns** — `orderNumber`,
`orderStatus`, `accountNumber`, `customerNumber`, `totalAmount`, and per item
`offerId`, `productId`, `amount`. Nothing else.

*Amended during implementation (2026-08-02).* An earlier draft of this section also
listed `serviceAddressId`, `campaignId`, `campaignName` and a per-item `offerName`,
on the assumption that the Submit screen's AC-SALE-01-12 field list should be echoed
back. Building it exposed the flaw: **none of those are order-domain facts.** The
service would have had to either persist catalog data it does not own — a second copy
that drifts the moment an offer is renamed — or call product-service on every order
lookup purely to decorate a response. Meanwhile the client already holds all of it:
it assembled the basket, picked the address and chose the campaign. The only things
it genuinely cannot know are what this service just created — the order number, the
status, and which product each offer became — and those are precisely what is
returned. The AC-SALE-01-12 field list describes a *screen*, not a payload.
2. **`GET /api/orders/{orderNumber}`** — the order representation for a known
   order number. Two justifications, both concrete: it makes the 201 verifiable,
   and it is the endpoint customer-service's KR-02 `orderNumber` search (today a
   501) will resolve against. **That customer-service wiring is not part of this
   PR** — same deliberate follow-up-PR pattern as the `accountNumber` search.
   Unknown number → **404 `MSG-ORDER-NOT-FOUND`**.

Responses expose business numbers only. Internal ids (`cust_ord.id`,
`bsn_inter.id`, `cust_ord_item.id`) never leave the service. There is no order
*list* endpoint: no FR asks for one, and inventing a list contract means
inventing sorting, filtering and pagination rules an analyst has not written.

### 4. KR-12 (project-proposed): Order Number generation

**Status of this rule: a project decision awaiting analyst approval**, recorded at
the same level as the invented offer prices (P1/P5). The analyst document numbers
its key rules KR-01..KR-11; this is the next free number and is labelled
**KR-12 (project-proposed)** in every document that references it, never as if it
came from the analyst.

1. **Format `[T][YY][SSSSSS][C]`, `VARCHAR(10) UNIQUE`** — deliberately the exact
   shape KR-11 defines for account numbers. AC-SALE-01-12 requires a displayable
   Order ID and the system already has a proven, documented identifier scheme;
   inventing a second, differently-shaped one would be gratuitous novelty.
2. **`C` is the Luhn (mod 10) check digit** over the first nine digits — the same
   algorithm as ADR-014 §2.
3. **`T` is fixed `1`** this phase, encoding the `BSN_INTER_TYPE` **NEWSALE**.
   `TRANSFER` (8) and `CANCEL` (9) have no FR (§8.2), so other `T` values are
   **reserved** for them should they ever gain one. No code is written for them
   now.
4. **`order_number_seq (segment, seq_year, next_value)`** mirrors ADR-014 §3–§6
   exactly: the same keyed-row design, the same race-safe single-statement
   allocation
   (`INSERT … ON CONFLICT … DO UPDATE … RETURNING next_value - 1`), the same
   invariant (`next_value` is the *next* value to be issued, first issued value
   exactly `100000`), the same injectable `Clock`, and the same overflow
   behaviour — **409 `MSG-ORDER-NUMBER-CAPACITY-EXCEEDED`** past `999999`, never
   a raw 500. A UNIQUE violation falls back to **409 `MSG-ORDER-DUP-NUMBER`**.
5. **The generator is copied into order-service, not shared.** `LuhnCheckDigit`
   and the number generator are duplicated from account-service and adapted. This
   project has **no shared business-logic library** and creating one for two
   classes would couple two services' release cycles to make ~60 lines
   DRY. The duplication is recorded here so it is a decision, not an oversight;
   the two copies are pinned by identical unit-test vectors.
6. **Once assigned, the order number is immutable and never reused**, including
   after a compensated (CANCELLED) sale — a rolled-back order keeps its number
   permanently, matching KR-11's permanence and ADR-003's reasoning.

> **⚠️ Known consequence, flagged for analysts (§8.1):** with `T = 1` and the same
> segment/year sequence start, order numbers and account numbers are drawn from
> **an identical value space** — the first seeded order number is `1261000002`,
> which is also account `1261000002`'s number. They live in different databases
> and different services with no shared namespace, so nothing breaks technically;
> but a human (or KR-02's `accountNumber` / `orderNumber` search fields) cannot
> tell the two apart by looking. See §8.1 for the recommendation.

### 5. Sale orchestration and compensation (no distributed transaction)

The flow writes to three databases with no 2PC, no saga framework and no broker.
The design principle is: **one commit point, everything before it discardable by
construction, everything after it non-destructive.**

#### 5.1 The steps

| # | Step | On failure |
|---|---|---|
| 0 | **Preconditions** (reads only): `GET /api/accounts/{accountNumber}` — must exist, be 224, and be **Active** (AC-SALE-02-01) | 404 `MSG-ACCT-NOT-FOUND` / 409 `MSG-ACCT-NOT-ACTIVE`; **nothing written anywhere** |
| 1 | **Local write**: `BSN_INTER` + `CUST_ORD` + `CUST_ORD_ITEM` rows in **one local `order_db` transaction**, status `MIDLWARE` | The transaction rolls back entirely; error returned; **no external call is made** |
| 2 | **`POST /api/products`** (ADR-015 §5) — products created `PNDG`, returns product ids + offer names + price snapshots | `CUST_ORD` → `CANCELLED`; the caller's own validation errors (400 `MSG-SALE-*`, `MSG-VAL-CHAR-*`) are **passed through unchanged**; an unreachable product-service is 503 `MSG-SERVICE-UNAVAILABLE` |
| 3 | **Local update**: fill `cust_ord_item.product_id`, `cust_ord_item.amount`, `cust_ord.total_amount` — one local transaction | Compensate step 2 (`POST /api/products/cancel`), `CUST_ORD` → `CANCELLED`, 500/503 |
| 4 | **`POST /api/accounts/{n}/product-involvements`** (ADR-013 §8) — **the commit point** | Compensate step 2 (`cancel` the PNDG products), `CUST_ORD` → `CANCELLED`, 503 `MSG-SERVICE-UNAVAILABLE` |
| 5 | **`POST /api/products/confirm`** — PNDG → ACTV | **The sale is NOT rolled back** — see §5.3 |

#### 5.2 Why step 3 exists (a revision to the proposed design, with cause)

The proposed orchestration wrote `CUST_ORD_ITEM` in step 1, but the workbook's
`CUST_ORD_ITEM` carries **`product_id`** — a value that does not exist until step
2 returns. Three ways out were considered:

- **Reorder** (create products first, then the order) — rejected: it destroys the
  property that made the design safe, namely that the first write is always a
  single local transaction with no external state to unwind.
- **Drop the column** — rejected: it is a workbook column and the order → product
  link is the only navigable one (`PROD.transaction_id` is left NULL, ADR-015 §8.2).
- **Make it nullable and fill it in a second local transaction** — chosen. Both
  amount columns (§2.4) are filled in the same update for the same reason, which
  also avoids a second catalog round trip just to price the basket.

The intermediate state (a `MIDLWARE` order whose items have no product yet) is
never observable: it exists only inside the request, and if the request dies
there the row is `CANCELLED`.

#### 5.3 Why step 5's failure does not roll back the sale

After step 4 the sale is **real**: the products exist, they are linked to the
billing account (AC-SALE-01-01 satisfied), and the order is `MIDLWARE` — which is
literally the workbook's *"Siparis Alindi Isleniyor…"*, the exact status
AC-SALE-01-15 tells the user about. Step 5 only refines product status from
"reserved" to "active". Undoing it would require deleting involvement rows, i.e.
an account-service delete command that ADR-013 §8.6 declines to create — a write
path with no requirement behind it, indistinguishable from the KR-7 cancellation
that is out of phase.

So step 5 is **retried once, then abandoned with an ERROR log, and the request
still returns 201.** The residue is bounded and honest: products stay `PNDG`,
which is invisible to the customer (ADR-015 §5.5) rather than mislabelled. It is
identifiable by one query and recorded as an operational follow-up (ADR-015 §8.4)
— **not** papered over with a speculative reconciliation job no requirement asks
for.

#### 5.3b Transport-level retries are disabled (added during implementation, 2026-08-02)

`httpclient5` is on the runtime classpath (transitively, via the Eureka client), so
Spring selects `HttpComponentsClientHttpRequestFactory` — whose default
`DefaultHttpRequestRetryStrategy` **silently re-executes a request that answered
503**. All three outbound clients are therefore built with
`disableAutomaticRetries()`.

This is not tidiness. `POST /api/products` is **not idempotent**: an automatic retry
would create a *second* set of PNDG products, the orchestration would only ever learn
the second set's ids, and the first set would be orphaned in `product_db` — no order
referencing it, and no compensation able to find it. The involvement command
(idempotent by ADR-013 §8.4), `confirm` and `cancel` would survive a retry; product
creation would not, and one unsafe call is enough.

It was found empirically, not by inspection: an integration test proved a single POST
produced two orders and two product-creation calls. The same setting is applied to
the test HTTP client, so the suite measures what one request does.

**Retrying is the orchestration's decision, not the transport's.** §5.3 retries
exactly one step — the idempotent confirm — and compensates everything else. A
transport that quietly retried everything would make that design a fiction.

#### 5.4 Why this is not a saga framework

Every compensation above is a single idempotent HTTP call whose failure mode is
"the same residue, logged". There is no orchestrator state machine, no persisted
saga log, and no retry queue — because there is no asynchronous step and no
requirement for eventual completion. Adding one would add operational surface to
a flow whose entire lifetime is one user-facing HTTP request. If FR-SALE ever
grows a genuine long-running provisioning step, **that** flow gets the
infrastructure, with its own ADR.

#### 5.5 Consistency with the project's fail-closed philosophy
Every failure path ends in "nothing the user can see was created" (ADR-002 §7,
ADR-013 write paths). `CANCELLED` (GNL_ST 5, domain ORDER) is used **only** by
these system-triggered compensations — it is never reachable by a user action
(§6).

### 6. Status usage (and the values deliberately left unused)

| Value | Domain | Use here |
|---|---|---|
| `MIDLWARE` (4) | ORDER | The status every order is created with, and the only one a user ever sees. No AC moves an order out of it |
| `CANCELLED` (5) | ORDER | **System-triggered compensation only** (§5.1). No endpoint lets a user cancel an order — KR-7 keeps that out of phase |
| `WAIT` (3) | ORDER | **Never written.** No AC references it |
| `ACTV` (1) | GENERAL | `CUST_ORD_ITEM` rows (matching the workbook seed) |
| `NEWSALE` (7) | BSN_INTER_TYPE | The only `bsn_inter_type_id` ever written |
| `TRANSFER` (8) / `CANCEL` (9) | BSN_INTER_TYPE | **Never written** (§8.2) |

`bsn_inter.bsn_inter_type_id` exists as a column and is populated with `NEWSALE`,
so the schema stays workbook-faithful and a future transfer/cancel flow needs no
migration.

### 7. Message keys

Produced by order-service — **project additions**:

| Key | HTTP | Meaning |
|---|---|---|
| `MSG-ORDER-NOT-FOUND` | 404 | No order with that number |
| `MSG-ORDER-DUP-NUMBER` | 409 | Order-number uniqueness race (DB fallback — never 500) |
| `MSG-ORDER-NUMBER-CAPACITY-EXCEEDED` | 409 | KR-12 sequence exhausted for segment+year |

Passed through unchanged from downstream services (order-service adds no logic of
its own — ADR-015 §6): all `MSG-SALE-*` and `MSG-VAL-CHAR-*` keys (400,
product-service), `MSG-ACCT-NOT-FOUND` (404) and `MSG-ACCT-NOT-ACTIVE` (409,
account-service). Plus the established shared keys `MSG-VALIDATION-ERROR`,
`MSG-SERVICE-UNAVAILABLE` (any downstream unreachable — fail closed),
`MSG-INTERNAL-ERROR`, `MSG-AUTH-UNAUTHORIZED`, `MSG-AUTH-FORBIDDEN`.

`MSG-SALE-ORDER-CONFIRM` is **frontend-only** (the AC-SALE-01-15 modal); the
backend never produces it.

### 8. Recorded deviations and open questions

1. **Order and account numbers are indistinguishable — decided: accepted.** The
   seeded order number `1261000002` collides in *shape and value* with account
   `1261000002`, and in normal operation the two sequences overlap continuously.
   Two options were weighed. Giving orders a distinct `T` was rejected: `T` is
   already spoken for on both sides — in KR-11 it encodes the **customer
   segment**, in KR-12 the **business-interaction type** (§4.3) — so overloading
   it a third time to mean "which entity is this?" would make the digit
   unreadable and would break K4's reservation of other `T` values for
   TRANSFER/CANCEL. Accepting the overlap costs nothing **systemically**: the two
   numbers live in different databases owned by different services with no shared
   namespace, and KR-02 disambiguates at the point it matters by giving the
   customer search **two separate fields** (`accountNumber`, `orderNumber`) — the
   user states which kind of number they are typing. The residual cost is purely
   human: a bare 10-digit number out of context cannot be classified by eye.
   **Recorded as an analyst note** (if a globally self-describing identifier is
   ever wanted, that is a KR-11 *and* KR-12 change, not a KR-12 patch), not as an
   open blocker.
2. **`BSN_INTER_TYPE` has three values but only one flow.** The mock UI offers
   *Start new sale*, *Transfer* and *Service address change* as account-row
   actions; the catalog offers NEWSALE/TRANSFER/CANCEL. The three sets do not
   line up, and **no FR/AC covers transfer or cancellation**. This PR implements
   **NEWSALE only**; the other two are recorded as an analyst question, not built.
3. **AC-SALE-01-12 shows an "Order ID" on the Submit screen — before submission.
   Decided: the Order ID appears after submission.** Under the
   basket-is-frontend-state decision (§2.6) no order exists until LBL-SUBMIT is
   pressed, so no number can exist to display beforehand. A reservation endpoint
   was rejected on two independent grounds: it would burn a gapless KR-12 number
   on every abandoned sale, and it would create exactly the backend trace of an
   incomplete sale that **AC-SALE-01-16 forbids**. The Order ID is therefore
   rendered from the `201` response, in the AC-SALE-01-15
   *"Sipariş Alındı, İşleniyor…"* state — the same screen, one interaction later.
   The AC's wording is treated as an editorial ordering slip in the same class as
   §8.4, **flagged for analysts**; the requirement's intent (the user leaves the
   flow knowing their Order ID) is fully met. No reservation endpoint is built,
   and no backend contract depends on the answer.
4. **The use-case document's main flow ends at step 15 while its alternative flow
   references steps 17.x** (16–17 missing). Editorial defect; AC-SALE-01-15 is
   unambiguous on its own. Not treated as a blocker — flagged.
5. **Seed regeneration.** The workbook's `CUST_ORD` sample (`order_number = 5001`,
   `customer_id = 1`, `customer_account_id = 2`) is **not copied verbatim**: the
   order number is regenerated to KR-12 and the two ids become the corresponding
   business numbers (§2.3), exactly as ADR-014 §8 regenerated the `CUST_ACCT`
   seed. The three `CUST_ORD_ITEM` rows and the amount snapshots follow the
   product-service fixture prices (provisional, P1).
6. **Amount columns are a project addition** (§2.4) — recorded in
   `document-delta.md` alongside P1/P5 with the same pending-approval status.

### 9. Out of scope for this decision

- **Frontend.** The Offer Selection, Product Configuration and Submit Order
  screens are a separate follow-up; this ADR delivers backend only. The
  `FE-ADR-013` scope rows for those three screens are unchanged by it.
- **customer-service changes.** KR-02's `orderNumber` search stays 501; wiring it
  to §3.2 is the same follow-up PR that wires `accountNumber`.
- **Product cancellation / order cancellation** (KR-7) and the transfer /
  service-address-change flows (§8.2).

### 10. Idempotency addendum (2026-08-06)

1. **`POST /api/orders` requires an `Idempotency-Key` header** — a client-generated
   UUID, one per logical submit attempt. Enforced by a servlet filter
   (`IdempotencyKeyFilter`) positioned AFTER Spring Security's chain (so 401/403 still
   preempt it, exactly as for every other rule) and BEFORE the controller, so it can
   both reject before any work happens and capture the SAME response
   `GlobalExceptionHandler` (or the 201 path) produced, for replay.
2. **New table `idempotency_key`** (Flyway V3 — project addition, not a workbook
   table, the same class of deviation as `order_number_seq`): key, normalized request
   hash, status (`IN_PROGRESS`/`COMPLETED`), order number (once known), a response
   snapshot + HTTP status, created/updated timestamps, and a retention `expires_at`.
   Normalization round-trips the request through its own DTO (parse → re-serialize)
   before hashing — cheap, and sufficient because a genuine retry resends
   byte-identical JSON; it is not a general-purpose canonical-JSON algorithm.
3. **Enforcement, in order:**
   - missing/non-UUID key → `400 MSG-IDEMPOTENCY-KEY-REQUIRED`;
   - same key + same normalized body → the ORIGINAL response is replayed verbatim,
     the orchestration does not run again — this is deliberately the SAME answer for
     an intentional duplicate call and for a retry after a lost response, because the
     server has no way to tell the two apart and does not need to;
   - same key + a different normalized body → `409 MSG-IDEMPOTENCY-KEY-CONFLICT`;
   - same key, a concurrent request for it still `IN_PROGRESS` → `409
     MSG-IDEMPOTENCY-KEY-IN-PROGRESS`.
4. **The database UNIQUE constraint on `idempotency_key.idempotency_key` is the
   final concurrency guard, not an in-memory check.** The reservation INSERT
   (`IdempotencyPersistence#reserve`, its own `REQUIRES_NEW` transaction, committed
   independently and immediately) either wins the row or fails against it; the loser
   re-reads the row in a fresh transaction to decide replay vs. conflict. Two
   concurrent requests for the same key can therefore never both reach the
   orchestration.
5. **Every terminal outcome is recorded and replayed alike — success or a handled
   failure (400/404/409/503).** This is a deliberate simplification, not an
   oversight: distinguishing "safe to silently re-run" failures from "must replay"
   ones would require a second policy on top of the one this addendum already adds,
   for a benefit no test or requirement asks for. A stuck `IN_PROGRESS` row (the
   owning request crashed before `complete()` ran) is bounded, logged residue —
   the same class of trade-off §5.3's stuck-PNDG residue and ADR-015 §8.4 already
   accept, not a speculative reclaim-after-timeout policy.
6. **The same client-supplied key is forwarded, unchanged, as product-service's
   `saleOperationId`** (ADR-015's own idempotency addendum) and to the sale-scoped
   `POST /api/products/compensate`. order-service invents no operation id of its
   own and does not interpret the key beyond forwarding it — it is opaque here.
7. **The Angular client mints the key** (`crypto.randomUUID()` in
   `OrderSubmitStore`) once per logical submit attempt and reuses it for a retry of
   the SAME attempt (identical request body); editing the basket before resubmitting
   changes the body and therefore mints a fresh key — reusing a stale one would hit
   this addendum's own same-key-different-payload 409 instead of submitting the
   edit. This is a client-side decision, not a new transport-level retry: the
   project-wide rule against automatic retries on unsafe writes (§5.3b) is
   unchanged — `OrderApiService.submit` still never retries a call itself.

## Consequences

- The system gains its first cross-service write orchestration. The safety
  argument rests entirely on **ADR-015 §5's PNDG protocol**: if the two-phase
  product creation is ever removed, this design's compensations become
  destructive and the ADR must be revisited.
- `order_db` is single-writer like every other database, and order-service reads
  no other service's schema. Its dependency set is lookup-service (statuses),
  account-service (precondition + involvement command), product-service (product
  creation) — all synchronous, all Eureka-direct with the user's token
  propagated (ADR-010), never through the gateway. The service graph stays
  acyclic (ADR-013 §Consequences).
- **KR-12 is the second project-proposed business rule awaiting analyst
  sign-off**, joining the invented offer prices. Both are load-bearing for §2.4's
  order history, so approval matters more now than it did when only a catalog
  screen depended on them.
- Selling into an account now blocks its passivation (409
  `MSG-ACCT-HAS-PRODUCTS`, AC-ACCT-04-03) for the first time in real use rather
  than only via seed fixtures.
- `WAIT`, `TRANSFER` and `CANCEL` remain unused catalog rows. That is visible and
  intentional; a future reader should read §6 before concluding something was
  forgotten.
