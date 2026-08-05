# ADR-005: Customer List and Filter Contract (GET /api/customers)

## Status
Accepted (2026-07-16), **amended 2026-07-29** (page-size contract — see
§Amendment), **extended 2026-08-05** (KR-02 `accountNumber`/`orderNumber`
resolution — see §Addendum 2026-08-05) — implements the FR/AC v8 Final revision of 16.07.2026
(AC-CUST-01-00, KR-04, message catalog update). Extends, does not replace, the
KR-01 search semantics already recorded in ADR-001..003-era documentation.

## Context
The 16.07.2026 revision of `CRM_Lite_FR_AC_v8_Final.docx` added **AC-CUST-01-00**:
after login, the main page lists **all customers**, sorted A-Z by customer name.
Until now `GET /api/customers` rejected requests without at least one search
criterion (400 `MSG-SEARCH-CRITERIA-REQUIRED`) and returned a slim
`Page<CustomerSearchResponse>` row (`customerId, firstName, middleName, lastName,
role, nationalityId`).

Two contract problems followed:

1. The frontend cannot render the post-login "all customers" page against an
   endpoint that refuses criterion-less requests.
2. The team decided the list rows must carry **exactly the same field contract** as
   the singular detail endpoint `GET /api/customers/{customerNumber}`, so the UI can
   render list and detail from one model and no second row-DTO needs maintaining.

## Decision
1. **One endpoint, two modes.** `GET /api/customers` stays the single canonical
   customer read-list endpoint (no `/search` alias — that removal stands). With
   **no filter parameters** it is the *browse* mode; with parameters it is the
   *filter* mode. Both modes share pagination, sorting, the active-only rule and the
   response shape, so no second endpoint or alias is introduced.
2. **Browse mode** returns every **ACTIVE, non-deleted** customer
   (`status_id = ACTV AND deleted_date IS NULL`), server-side paginated. "All
   customers" means the whole active list is reachable page by page — there is **no
   unbounded all-rows response**.
3. **Response type** is `Page<CustomerDetailResponse>` in both modes. Every row
   carries exactly the current singular-detail contract:
   `customerNumber, firstName, middleName, lastName, fatherName, motherName,
   birthDate, gender, nationalityId, role, status`. No address/contact collections
   are added, because the singular detail endpoint has none (exact equivalence rule).
4. **Pagination defaults:** `page=0`, `size=20`. **Sort:** `firstName ASC`,
   then `lastName ASC`, then `customerNumber ASC` as a stable tiebreak so pages never
   shuffle equal names.
5. **Filters keep the analyst-approved KR-01 semantics** (unchanged): `firstName` =
   word-start match over First + Middle combined, `lastName` = word-start over Last
   Name, both = AND; `gsmNumber` = mobile-phone prefix; `nationalityId` /
   `customerId` (business customer number) = exact; filled criterion groups OR-ed;
   `accountNumber`/`orderNumber` remain **501 `MSG-FEATURE-NOT-IMPLEMENTED`** until
   the account/order domains exist. *(The 501 clause is **superseded by §Addendum
   2026-08-05** below; the rest of this item stands.)*
6. **Mandatory-criteria rule removed.** `checkAtLeastOneSearchCriterionExists` is
   deleted, its tests removed, and the project-specific key
   `MSG-SEARCH-CRITERIA-REQUIRED` is retired (no other endpoint used it). The UI-level
   rule "LBL-SEARCH is disabled while all filter fields are empty" (AC-CUST-01-02)
   remains a **frontend** behaviour; the API itself now serves the browse mode instead.
7. **`CustomerSearchResponse` deleted.** No released consumer existed; the mapper's
   `toSearchResponse` went with it.
8. **N+1 protection.** The list specification fetch-joins the to-one detail graph
   (customer → partyRole → role/party → individual/contactMedium) for row queries and
   keeps plain joins for the count query. All joins are to-one, so fetch joins cannot
   fan out rows and pagination stays correct.

## Compatibility consequences
- **Breaking for any client of the old row shape:** the business identifier field in
  list rows is now `customerNumber` (was `customerId` in `CustomerSearchResponse`),
  and rows carry the full demographic set. There are no released consumers (no
  frontend yet), so the break is accepted without a transition period.
- A criterion-less request now returns **200 + page 0 of the active list** instead of
  400 — clients relying on the rejection (none known) must adapt.
- The **query parameter** `customerId` keeps its name (public search field naming
  follows the screens); only the response field changed.
- The singular detail endpoint `GET /api/customers/{customerNumber}` is byte-for-byte
  unchanged.

## Recorded discrepancy — KR-04 default page size (SUPERSEDED, see Amendment below)
KR-04 (v8 Final, 16.07.2026) describes the **UI**: default Per Page **15**, options
15/30/50, changeable under the results table. The API default chosen here is
**20** (team decision for this backend contract iteration). This is not silently
reconciled: the frontend must pass `size=15|30|50` explicitly per KR-04, and the API
accepts any positive `size`. If the analysts want the API default itself to be 15,
that is a one-line change; the open question is tracked in
`docs/requirements/traceability-matrix.md` and PROJECTBRAIN "Open conflicts".

## Amendment (2026-07-29) — the API adopts KR-04 verbatim
The open question above is **closed in favour of KR-04**. The analysts filed it as
five defects against the API contract (BUG-API-CUST-01-14, -16, -17, -18, -19), which
is the decision this ADR asked for. `GET /api/customers` now:

- defaults `size` to **15** (was 20);
- accepts **only 15, 30 and 50**; every other value — 17, 999999, 0 — is
  **400 `MSG-VALIDATION-ERROR`** with `validationErrors.size`. The previous
  "the API accepts any positive `size`" sentence is **withdrawn**;
- rejects a negative `page` with the same 400 shape (`validationErrors.page`).

Rationale for rejecting rather than clamping: the analysts specified 400 explicitly,
and a silently clamped size makes a client's pagination arithmetic wrong without
telling it.

`size=0` and `page=-1` previously reached `PageRequest.of`, which throws
`IllegalArgumentException` for both and surfaced as **500** through the generic
handler — a presentation-layer validation failure reported as a server fault. The
whitelist and `@Min(0)` stop them at the controller boundary, so no
`IllegalArgumentException` handler is needed (a blanket one would also mask genuine
internal faults as 400s).

The whitelist lives in `common/validation/AllowedPageSize`, which is the single
source of truth: the same constant array drives the check, the violation message and
the controller's `defaultValue`, so the default can never fall outside the whitelist.

**`page` deliberately keeps no upper bound.** A page past the end stays a normal
200 with empty content (Spring Data semantics); KR-04 constrains page *size* only.

### Consequences of the amendment
- **Breaking for the frontend**, which sent `size=20` on every list call: the
  per-page options moved from 20/50/100 to **15/30/50, default 15**
  (`PAGE_SIZE_OPTIONS`). The 100-row option is gone — it is no longer expressible.
  Backend and frontend therefore ship together; a backend-only merge would 400 every
  Customer Search request.
- `docs/frontend/scope-and-conflicts.md` §2.3, §2.4 and §3.1 recorded the superseded
  "default 20 / options 20-50-100" decision and are revised accordingly, as is
  `mock-ui-analysis.md` §6.2/§9.
- No new message key: the existing project-added `MSG-VALIDATION-ERROR` already
  carries `@RequestParam` constraint failures, so nothing is owed by the analyst
  catalog.

## Addendum (2026-07-29) — Nationality-ID availability probe

**Status:** Accepted. Does not amend any decision above; it closes a gap they left.

**Context.** Decision §2 makes every read here **active-only**, while ADR-003 makes
Nationality-ID uniqueness **global and permanent** — a soft-deleted customer keeps its
ID reserved forever. The two are individually right and jointly leave a hole: the create
screen has no way to ask "is this ID free?". `GET /api/customers?nationalityId=` answers
"nothing found" for a soft-deleted holder, then `POST /api/customers` answers 409
`MSG-CUST-DUP-NATID`. Observed cost: the user fills three wizard steps and only then
learns the ID was never usable.

**Decision.** Add one read-only endpoint,
`GET /api/customers/nationality-id-availability?nationalityId={id}` → `{"available": bool}`.

1. **This is not a second list/filter endpoint and not a `/search` revival.** §1 stands.
   It returns no customer data, accepts no paging or sorting, and cannot enumerate
   anything: one ID in, one boolean out.
2. **It reports the ADR-003 rule, not the list's view of it.** It calls the very method
   the create path uses (`CustomerBusinessRules.isNationalityIdAvailableForCreate`), so
   probe and authority are the same predicate over the same rows — soft-deleted and
   passive included. They cannot drift.
3. **Advisory, never a reservation.** `true` is a snapshot. The create-time check and the
   DB UNIQUE constraint remain the authority; clients keep handling 409.
4. **Minimal disclosure.** The response carries `available` and nothing else — no
   customer number, no name, not even an echo of the queried ID. Since the rule covers
   deleted people, any richer answer would make this an existence-mining endpoint.
   Access is the standard `crm-user` requirement (ADR-009); it is not public.
5. **Routing.** The literal segment outranks `/{customerNumber}`; the detail endpoint is
   unchanged (asserted by an integration test).

**Consequences.**
- The create screen can refuse a taken ID at step 1, including the soft-deleted case,
  instead of after the whole wizard.
- One more public surface to keep in step with ADR-003 — mitigated by §2: there is only
  one implementation of the rule.
- A missing required query parameter now answers 400 instead of 500 service-wide
  (`MissingServletRequestParameterException` handler added alongside), matching how
  type-mismatched parameters were already handled.

## Addendum (2026-08-05) — FR/AC v8-2 review

Reviewed against FR/AC v8-2 (03.08.2026): AC-CUST-01-00 and KR-04 wording are
unchanged from the v8 Final revision this ADR implements. **No change to this
decision.** See `docs/requirements/document-delta.md`.

## Addendum (2026-08-05) — KR-02 child-record criteria (`accountNumber`, `orderNumber`)

**Status:** Accepted. Supersedes the 501 clause in Decision §5; every other decision
above is unchanged.

**Context.** §5 deferred these two criteria because their records live in domains
that did not exist. Both now do: **account-service** owns `account_db.cust_acct`
(2026-07-23, ADR-013) and **order-service** owns `order_db.cust_ord` (2026-08-02,
ADR-016). KR-02 and AC-CUST-01-04 require that searching by either number returns
*the customer that owns the matching child record*, with the result list always
customer-based and one row per customer.

**Decision.**

1. **They stay criteria of `GET /api/customers`.** §1 ("one endpoint, two modes")
   is not weakened: no `search-by-account` endpoint, no alias, no second row DTO.
   The two criteria join the same OR expression, the same active-only rule, the same
   sort and the same page envelope as everything else.
2. **Resolution happens through the OWNING service's existing public API**, not
   through its database: `GET /api/accounts/{accountNumber}` (ADR-013 §5) and
   `GET /api/orders/{orderNumber}` (ADR-016 §3.2, which already named this as its
   purpose). Both already return `customerNumber`, so no new endpoint, no contract
   change and no id translation were needed on either side.
   *Rejected alternatives:* reading `account_db`/`order_db` from customer-service
   (violates database-per-service outright), a cross-database view or FK (same), a
   local copy of the two tables (a second source of truth for data this service does
   not own), and orchestrating the two lookups in the API Gateway (ADR-007 keeps the
   gateway an edge/BFF, not a business aggregator).
3. **Only a LIVE child record resolves.** For an account that means
   `accountStatus = "Active"` — FR-ACCT-04 makes passivation account-service's soft
   delete, so a Passive account must not surface its owner. For an order it means
   `orderStatus = "MIDLWARE"`, the only status an accepted order holds (ADR-016 §6);
   `CANCELLED` is a compensated, rolled-back sale. Each domain's own notion of
   "live" is used — no generic ACTV check is imposed on a domain that never writes it.
   The K-8 223 Customer Account is 404 at account-service by design (ADR-013 §4.5),
   so it can never become a search key — the invariant that it "never appears in any
   API response" is preserved for free by reusing that endpoint.
4. **A filled but unresolved number matches NOTHING.** It contributes a FALSE
   predicate, deliberately not a dropped criterion: dropping it would leave zero
   criteria and silently return the whole active list (§2 browse mode) for a query
   whose honest answer is "no such customer".
5. **Distinctness and pagination are structural.** Each number is globally unique, so
   it resolves to at most one customer number; the predicate is an equality on
   `cust.customer_number` and adds **no join**. AC-CUST-01-04's "one row even when
   several child records match" therefore holds by construction, and §8's guarantee
   that page metadata counts customers (never joined rows) is untouched.
6. **Fail closed on an outage.** An unreachable owning service answers 503
   `MSG-SERVICE-UNAVAILABLE` — the same rule ADR-002 applies to the shared catalog.
   Returning an empty page would report "customer not found" for a customer that
   exists; ignoring the criterion would return everyone.
7. **Security is unchanged (ADR-009/010).** Both calls go directly via Eureka with
   the end user's Keycloak token propagated — same subject, same audience, same
   `crm-user` requirement. No client credentials, no service account, no new
   `permitAll()`, and the browser still reaches only the gateway.
8. **Matching is EXACT**, per KR-01 and AC-CUST-01-03, which agree; the resolution
   is recorded in `docs/requirements/document-delta.md` (conflict #7).

**Consequences.**
- `MSG-FEATURE-NOT-IMPLEMENTED` is no longer produced by any service.
- A criterion-less browse and every pre-existing criterion still cost exactly one
  local query: the owning services are contacted only when their field is filled.
- customer-service now depends on account-service and order-service at *request*
  time. This is deliberately **not** a compose `depends_on`: account-service already
  depends on customer-service (address validation), so the reverse edge would create
  a startup cycle. A not-yet-started dependency degrades to a 503 on that one
  criterion, and to nothing at all on every other request.
- The frontend's Account/Order Number inputs stop being disabled
  (`scope-and-conflicts.md` §1.7c).

## Consequences
- Frontend can implement AC-CUST-01-00 (post-login all-customer list) directly.
- One DTO to maintain for customer reads; list and detail can never drift apart.
- Browse-mode pages are heavier than the old slim rows (5 extra scalar fields per
  row) — negligible at this data scale; revisit only if row counts explode.
- Tests cover: browse returns all active customers, soft-deleted exclusion, A-Z
  ordering, full detail fields per row, pagination, unchanged filter semantics,
  unchanged singular detail, absent `/search` alias.
