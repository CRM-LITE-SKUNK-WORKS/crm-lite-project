# Functional Requirements — Implementation Summary

Source of truth: `docs/source/requirements/CRM_Lite_FR_AC_v8-2.docx`
(**03.08.2026 revision**, supersedes the 23.07.2026 v8-1 Final — see
[document-delta.md](document-delta.md) for what changed). This file summarizes what
the backend implements today and where behaviour is intentionally deferred. IDs
refer to the FR document.

## Implemented (customer-service, port 8082)

| FR | Capability | Notes |
|---|---|---|
| FR-CUST-01 / AC-CUST-01-00 | Customer **browse + filter** | `GET /api/customers` (canonical, only list endpoint — ADR-005). **No criteria ⇒ all active customers**, A-Z (firstName→lastName→customerNumber), server-side paginated. KR-01 filter semantics: firstName = word-start match over First+Middle, lastName = word-start over Last Name, AND-ed together; GSM prefix; NAT ID / Customer ID / **Account Number / Order Number exact**; groups OR-ed; only active customers. Rows carry the full `CustomerDetailResponse` contract |
| FR-CUST-01 / **KR-02, AC-CUST-01-04** | Search by **child record** | `accountNumber` and `orderNumber` (2026-08-05, ADR-005 §Addendum). Each is resolved to its OWNING customer through the owning service's public API — `GET /api/accounts/{n}` (account-service, ADR-013 §5) and `GET /api/orders/{n}` (order-service, ADR-016 §3.2) — and only an **Active** account / **MIDLWARE** order counts. No cross-database read, join or table copy; the resolved customer number folds into the same OR expression, so results stay customer-based, distinct and correctly paginated. Owning service unreachable ⇒ **503 `MSG-SERVICE-UNAVAILABLE`** (fail closed) |
| FR-CUST-02 | Customer detail | `GET /api/customers/{customerNumber}` — business number, never the internal id |
| FR-CUST-03 | Atomic create | `POST /api/customers` with `demographic` + `addresses[]` + `contactMedium`; persists PARTY, IND, PARTY_ROLE, CUST, ADDR, CNTC_MEDIUM in one transaction (AC-CUST-03-22); KR-10/AC-CUST-03-06 MERNIS verification before persist |
| FR-CUST-04 | Demographic update | `PUT /api/customers/{customerNumber}`; NAT ID uniqueness excludes own record (AC-CUST-04-04) |
| FR-CUST-05 | Soft delete | `DELETE /api/customers/{customerNumber}`; passivates CUST/PARTY_ROLE/PARTY/IND/ADDR/CNTC_MEDIUM with deleted/updated audit metadata |
| FR-ADDR-01..05 | Address management | List/add/update/delete/set-primary under `/api/customers/{n}/addresses`; first address auto-primary, one active primary (DB partial unique index), last-address and primary-delete guards, cascading city→district, `GET /api/cities`, `GET /api/cities/{id}/districts`. The AC-ADDR-04-03 delete-confirmation modal (`MSG-ADDR-DELETE-CONFIRM`) is a frontend interaction; the backend executes the delete only when called |
| FR-CNTC-01..02 | Contact medium | `GET/PUT /api/customers/{n}/contact-medium`; Email+Mobile required, VR-EMAIL/VR-PHONE/VR-MOBILE enforced |
| KR-10 | Fake MERNIS | `backend/mernis-stub` (:8084); rejection ⇒ 400 `MSG-CUST-NATID-VERIFICATION-FAILED`, unavailability ⇒ 503 `MSG-MERNIS-UNAVAILABLE`; customer NOT created either way (fail closed) |

## Implemented (account-service, port 8085 — 2026-07-23, ADR-013/014)

| FR | Capability | Notes |
|---|---|---|
| FR-ACCT-01 | Billing-account list | `GET /api/accounts?customerId={customerNumber}` — 224 only, **Active + Passive** (AC-ACCT-01-03), Active first then Passive, accountNumber ASC inside each group (AC-ACCT-01-04), no pagination, `200 []` when none |
| FR-ACCT-02 / KR-11 | Create Billing Account | `POST /api/accounts` `{customerId, accountName, addressId}` → 201; type forced to 224; address validated against the customer's active address list (customer-service, token propagated); KR-11 number (`[T][YY][SSSSSS][C]`, Luhn, ADR-14); **K-8**: first 224 lazily creates the customer's single 223 Customer Account in the same ACID transaction (never exposed via the API) |
| FR-ACCT-03 | Update | `PUT /api/accounts/{accountNumber}` — mutable fields exactly `accountName`+`addressId`; immutable/unknown fields → 400 `MSG-ACCT-IMMUTABLE-FIELD` (rejected, never ignored); Passive → 409 `MSG-ACCT-NOT-ACTIVE` |
| FR-ACCT-04 | Delete = passivation | `DELETE /api/accounts/{accountNumber}` → 204; soft passivation only, row stays list-visible as Passive (AC-ACCT-04-02, v8-2); active involvement (local `cust_acct_prod_invl` projection) → 409 `MSG-ACCT-HAS-PRODUCTS`; re-delete → 409 `MSG-ACCT-NOT-ACTIVE`; `MSG-ACCT-DELETED`/`MSG-ACCT-DELETE-CONFIRM` are frontend-only |

Details: `docs/api/account-service.md`; decisions: ADR-013/ADR-014 +
`docs/architecture/account-service-decisions.md` (K-8 analyst approval, Passive
policy, seed regeneration).

## Implemented (FR-SALE §2.7 — 2026-08-02, ADR-015/016)

| FR | Capability | Notes |
|---|---|---|
| FR-SALE-01 | Product sale, end to end | `POST /api/orders` (order-service, :8087) — **one atomic Submit command carrying the whole basket**. Orchestrates across three databases with no distributed transaction: local order write (`MIDLWARE`) → products created **PNDG** in product-service → product ids + amount snapshots attached locally → products confirmed to `ACTV` → **account-service's involvement command, the commit point**. Every earlier failure discards the products and marks the order `CANCELLED` (ADR-016 §5) |
| FR-SALE-01 (basket) | AC-SALE-01-03..08 basket assembly + validation | **The basket is never persisted** — it is frontend/session state, which is what makes AC-SALE-01-16 true by construction. Composition rules (all offers Active; exactly one INTERNET/RESOURCE/ACTIVATION; no duplicate offer) are enforced **server-side in product-service** (ADR-015 §6), which alone knows offer status and service type; order-service relays `MSG-SALE-*` unchanged |
| FR-SALE-01 (configuration) | AC-SALE-01-10/17/18/19/20/21 characteristics | `GET /api/offers/{id}/characteristics` serves the schema (name, dataType, mandatory) through the offer's spec; values are validated against the declared `data_type` on write (`MSG-VAL-CHAR-REQUIRED` / `MSG-VAL-CHAR-FORMAT`). An offer with no characteristics is `200 []` |
| FR-SALE-01 (amounts) | AC-SALE-01-12 Total Amount | **Snapshot** columns `cust_ord.total_amount` / `cust_ord_item.amount` — project additions (ADR-016 §2.4): a past order's total must not move when the price list does. **The prices feeding them await analyst approval** (document-delta P1/P5) |
| FR-SALE-02 | Sale only from an **Active** billing account | AC-SALE-02-01 enforced server-side twice — at the precondition read and again by account-service's involvement command (409 `MSG-ACCT-NOT-ACTIVE`). The FR states it only as a disabled UI action |
| KR-12 *(project-proposed)* | Order Number | `[T][YY][SSSSSS][C]`, Luhn, per-segment/per-year sequence — the KR-11 shape reused. Immutable, never reused, including after a compensated sale. **Awaiting analyst sign-off** (ADR-016 §4) |

Details: `docs/api/order-service.md`; decisions: **ADR-015** (product boundary +
write slice), **ADR-016** (order boundary, KR-12, orchestration), **ADR-013 §3.6/§7/§8**.

## Implemented (authentication/security milestone, 2026-07-17 — ADR-006..011)

| FR | Capability | Notes |
|---|---|---|
| FR-AUTH-01 | Login | OIDC **Authorization Code + PKCE** against Keycloak (realm `crm-lite`, client `crm-bff`); credentials are entered ONLY on the Keycloak login page. AC-AUTH-01-01 (success ⇒ app) via gateway BFF session; AC-AUTH-01-03/04/05 (unknown / passive / wrong password ⇒ same generic error) satisfied by Keycloak's login page; the remaining AC-AUTH-01 items are login-page UI criteria that bind the Keycloak theme (custom project theme = future work) |
| FR-AUTH-02 | Logout | CSRF-protected `POST /logout` at the gateway: session invalidated + RP-initiated Keycloak logout; back-button / direct access afterwards ⇒ 401 ⇒ login (AC-AUTH-02-01/02) |
| KR-8 | Single role | One realm role **`crm-user`**; every business endpoint requires it explicitly (gateway AND resource servers); RBAC beyond it stays out of scope |
| KR-9 | Session policy | Access token 5 min, idle 30 min (gateway session + Keycloak SSO idle), absolute 24 h (SSO max) — all environment-configurable |

Details: `docs/api/authentication.md`. The workbook USERS table is deliberately
NOT implemented — Keycloak owns credentials (ADR-011, analyst sign-off pending).

Validation formats implemented exactly per the FR catalog: VR-NAME (Turkish letters,
1–50, trim-first), VR-NATID, VR-EMAIL, VR-PHONE, VR-MOBILE.

## Message keys

From the analyst catalog (v8-2, 03.08.2026 — keys unchanged since v8 Final, 16.07.2026),
as used by the backend:
`MSG-CUST-NOT-FOUND`, `MSG-CUST-DUP-NATID`, `MSG-CUST-HAS-PRODUCTS`, `MSG-ADDR-IN-USE`,
`MSG-VAL-NATID`, `MSG-VAL-BIRTHDATE`, `MSG-VAL-AGE-MIN`, `MSG-VAL-NAME`, `MSG-VAL-EMAIL`,
`MSG-VAL-PHONE`, **`MSG-CUST-NATID-VERIFICATION-FAILED`**, **`MSG-MERNIS-UNAVAILABLE`**.

The two MERNIS keys are the analyst-approved names first introduced in the 16.07.2026
catalog; they
**replaced** the older project-specific `MSG-NATID-VERIFY-FAILED` and (for MERNIS
outages) the generic `MSG-SERVICE-UNAVAILABLE`.

**Documented project additions** (not in the analyst catalog — framework/integration
outcomes the catalog does not name): `MSG-FEATURE-NOT-IMPLEMENTED` (**no longer
produced by any service since 2026-08-05** — the last user, the `accountNumber` /
`orderNumber` 501, is gone; the key stays declared so a future deferral does not
invent a second spelling), `MSG-VALIDATION-ERROR`, `MSG-INTERNAL-ERROR`,
`MSG-SERVICE-UNAVAILABLE` (shared catalog outages, ADR-002; customer-service outages
seen by account-service, ADR-013; **and account-service/order-service outages seen by
customer-service's KR-02 search**), `MSG-ADDR-LAST-DELETE`, `MSG-ADDR-PRIMARY-DELETE`,
`MSG-AUTH-UNAUTHORIZED` (401), `MSG-AUTH-FORBIDDEN` (403),
`MSG-AUTH-CSRF-REJECTED` (403, CSRF) — ADR-008/009. (`MSG-AUTH-INVALID-CRED`
remains a Keycloak login-page concern, not an API key.)

Account-service additions (ADR-013 §6; EN/TR texts in
`docs/architecture/account-service-decisions.md`): `MSG-ACCT-NOT-FOUND` (404),
`MSG-ACCT-NOT-ACTIVE` (409), `MSG-ACCT-IMMUTABLE-FIELD` (400),
`MSG-ACCT-DUP-NUMBER` (409), `MSG-ACCT-NUMBER-CAPACITY-EXCEEDED` (409).
From the analyst catalog: `MSG-ACCT-HAS-PRODUCTS` (409);
`MSG-ACCT-DELETE-CONFIRM`/`MSG-ACCT-DELETED` are frontend-only and never
produced by the backend.

FR-SALE §2.7 keys **from the analyst catalog**, all 400 and all produced by
**product-service** (ADR-015 §6), relayed unchanged by order-service:
`MSG-SALE-OFFER-INACTIVE`, `MSG-SALE-NO-INTERNET`, `MSG-SALE-NO-RESOURCE`,
`MSG-SALE-NO-ACTIVATION`, `MSG-SALE-MULTI-INTERNET`, `MSG-SALE-MULTI-RESOURCE`,
`MSG-SALE-MULTI-ACTIVATION`, `MSG-SALE-DUP-OFFER`, `MSG-VAL-CHAR-REQUIRED`,
`MSG-VAL-CHAR-FORMAT`. `MSG-SALE-ORDER-CONFIRM` is frontend-only (the AC-SALE-01-15
modal) and never produced by any backend.
Order/product **project additions** (ADR-015 §7, ADR-016 §7 — the analyst catalog
names no order outcomes): `MSG-ORDER-NOT-FOUND` (404), `MSG-ORDER-DUP-NUMBER` (409),
`MSG-ORDER-NUMBER-CAPACITY-EXCEEDED` (409), `MSG-PROD-NOT-FOUND` (404),
`MSG-PROD-NOT-PENDING` (409).

**Retired:** `MSG-SEARCH-CRITERIA-REQUIRED` — removed together with the mandatory
search-criteria rule (ADR-005); no endpoint uses it anymore.

## Intentionally deferred (explicit TODOs, never silent)

- ~~`accountNumber` / `orderNumber` search → **501 MSG-FEATURE-NOT-IMPLEMENTED**~~ —
  **done 2026-08-05** (the follow-up PR this entry asked for). KR-02 is resolved for
  real through account-service and order-service; the 501 gate
  (`checkNoUnsupportedCrossServiceSearchCriterion`) is deleted and no backend
  produces `MSG-FEATURE-NOT-IMPLEMENTED` anymore. See the FR-CUST-01 KR-02 row above.
- ~~Billing-account passivation on customer delete (part of AC-CUST-05-04) and the
  active-product check (AC-CUST-05-03)~~ — **done 2026-08-05**: `DELETE
  /api/customers/{n}` now lists the customer's Billing Accounts through account-service
  and passivates every Active one **before** any local entity is touched, so a failure
  leaves nothing to compensate. An account that still has products answers 409
  `MSG-CUST-HAS-PRODUCTS`; account-service unreachable fails the whole delete closed
  with 503. The upfront `checkCustomerHasNoActiveProducts` guard stays a no-op — the
  same rejection is now discovered one layer deeper, at the account-service call.
- ~~Address in-use check, Billing Account branch (AC-ADDR-04-04, MSG-ADDR-IN-USE)~~ —
  **done 2026-08-05 (BUG-API-ADDR-04-03)**: `DELETE /api/customers/{n}/addresses/{id}`
  now lists the customer's Billing Accounts through account-service; an Active account
  whose `billingAddressId` matches the address answers 409 `MSG-ADDR-IN-USE`, Passive
  accounts and Active accounts on a different address do not block, and account-service
  unreachable fails the delete closed with 503. The service-address/product-service
  branch of AC-ADDR-04-04 is **still open** — out of scope for this fix.
- ~~**Product involvement sync**~~ — **done 2026-08-02** (ADR-013 §7/§8):
  `cust_acct_prod_invl` is now populated by real sales through account-service's own
  command endpoint. It remains **single-writer**; no other service writes `account_db`.
  There is deliberately **no removal** path — KR-7 leaves product cancellation out of
  phase (ADR-013 §8.6).
- ~~**PROD** (FR-PROD-01..02)~~ — implemented 2026-07-29 (read) + 2026-08-02 (write
  slice, ADR-015). ~~**SALE** (FR-SALE-01..02)~~ — implemented 2026-08-02 (ADR-016);
  see the FR-SALE section above.
- **LANG** (FR-LANG-01: TR/EN label+message catalogs, **default language English** —
  AC-LANG-01-01, unchanged since the 16.07.2026 revision through v8-2) → frontend +
  planned localization capability; the backend
  deliberately returns `messageKey`s so localization stays a catalog concern.

## Superseded wording in stale sources (recorded, not silently resolved)

- Nationality ID uniqueness is **global and permanent** (ADR-003). The use-case
  document (FR-CUST-03 alternative step 4.5) still says "eşleşen **aktif** bir müşteri"
  — outdated, not canonical; the FR/AC v8-2 wording (AC-CUST-03-12, no active qualifier,
  unchanged since v8 Final) and ADR-003 govern.
- The draw.io FR-CUST-01 page still carries the old "içinde-geçen" (contains) match
  note — KR-01 word-start matching governs.
- ~~KR-04 default page size: UI default 15 (options 15/30/50) vs API default 20~~ —
  **closed 2026-07-29** (BUG-API-CUST-01-14/-16/-17/-18/-19): the API adopts KR-04
  verbatim — default 15, only 15/30/50 accepted, anything else 400. ADR-005 §Amendment.
