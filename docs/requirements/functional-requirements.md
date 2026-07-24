# Functional Requirements — Implementation Summary

Source of truth: `docs/source/requirements/CRM_Lite_FR_AC_v8-1_Final.docx`
(**23.07.2026 revision**, supersedes the 16.07.2026 v8 Final — see
[document-delta.md](document-delta.md) for what changed). This file summarizes what
the backend implements today and where behaviour is intentionally deferred. IDs
refer to the FR document.

## Implemented (customer-service, port 8082)

| FR | Capability | Notes |
|---|---|---|
| FR-CUST-01 / AC-CUST-01-00 | Customer **browse + filter** | `GET /api/customers` (canonical, only list endpoint — ADR-005). **No criteria ⇒ all active customers**, A-Z (firstName→lastName→customerNumber), server-side paginated. KR-01 filter semantics: firstName = word-start match over First+Middle, lastName = word-start over Last Name, AND-ed together; GSM prefix; NAT ID / Customer ID exact; groups OR-ed; only active customers. Rows carry the full `CustomerDetailResponse` contract |
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
| FR-ACCT-04 | Delete = passivation | `DELETE /api/accounts/{accountNumber}` → 204; soft passivation only, row stays list-visible as Passive (v8-1 AC-ACCT-04-02); active involvement (local `cust_acct_prod_invl` projection) → 409 `MSG-ACCT-HAS-PRODUCTS`; re-delete → 409 `MSG-ACCT-NOT-ACTIVE`; `MSG-ACCT-DELETED`/`MSG-ACCT-DELETE-CONFIRM` are frontend-only |

Details: `docs/api/account-service.md`; decisions: ADR-013/ADR-014 +
`docs/architecture/account-service-decisions.md` (K-8 analyst approval, Passive
policy, seed regeneration).

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

From the analyst catalog (v8 Final, 16.07.2026), as used by the backend:
`MSG-CUST-NOT-FOUND`, `MSG-CUST-DUP-NATID`, `MSG-CUST-HAS-PRODUCTS`, `MSG-ADDR-IN-USE`,
`MSG-VAL-NATID`, `MSG-VAL-BIRTHDATE`, `MSG-VAL-AGE-MIN`, `MSG-VAL-NAME`, `MSG-VAL-EMAIL`,
`MSG-VAL-PHONE`, **`MSG-CUST-NATID-VERIFICATION-FAILED`**, **`MSG-MERNIS-UNAVAILABLE`**.

The two MERNIS keys are the analyst-approved names from the 16.07.2026 catalog; they
**replaced** the older project-specific `MSG-NATID-VERIFY-FAILED` and (for MERNIS
outages) the generic `MSG-SERVICE-UNAVAILABLE`.

**Documented project additions** (not in the analyst catalog — framework/integration
outcomes the catalog does not name): `MSG-FEATURE-NOT-IMPLEMENTED`,
`MSG-VALIDATION-ERROR`, `MSG-INTERNAL-ERROR`, `MSG-SERVICE-UNAVAILABLE` (shared
catalog outages, ADR-002; also customer-service outages seen by account-service,
ADR-013), `MSG-ADDR-LAST-DELETE`, `MSG-ADDR-PRIMARY-DELETE`,
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

**Retired:** `MSG-SEARCH-CRITERIA-REQUIRED` — removed together with the mandatory
search-criteria rule (ADR-005); no endpoint uses it anymore.

## Intentionally deferred (explicit TODOs, never silent)

- `accountNumber` / `orderNumber` search → **501 MSG-FEATURE-NOT-IMPLEMENTED** in
  customer-service. account-service now exists, so wiring `accountNumber` search to it
  (KR-02) is a **separate customer-service follow-up PR** — this sprint did not modify
  customer-service; `orderNumber` still waits for the order domain.
- Active-product check on customer delete (AC-CUST-05-03) and billing-account
  passivation on customer delete (part of AC-CUST-05-04) → still customer-service
  no-ops; converting them to real account-service calls is the same follow-up PR.
- Address in-use check (AC-ADDR-04-04, MSG-ADDR-IN-USE) → still a customer-service
  no-op; billing accounts now reference addresses (`cust_acct.address_id`), so the
  real check becomes possible in that follow-up PR.
- **Product involvement sync**: `cust_acct_prod_invl` is real, queried guard state
  populated only by seed/test data until product-service exists; future population
  goes through an account-service command/API or a consumed event — never direct
  `account_db` writes by product/order/sale services (ADR-013 §5).
- **PROD** (FR-PROD-01..02) and **SALE** (FR-SALE-01..02, KR-06/KR-7 basket+order flow)
  → planned product/order services.
- **LANG** (FR-LANG-01: TR/EN label+message catalogs, **default language English** per
  the 16.07.2026 revision) → frontend + planned localization capability; the backend
  deliberately returns `messageKey`s so localization stays a catalog concern.

## Superseded wording in stale sources (recorded, not silently resolved)

- Nationality ID uniqueness is **global and permanent** (ADR-003). The use-case
  document (FR-CUST-03 alternative step 4.5) still says "eşleşen **aktif** bir müşteri"
  — outdated, not canonical; the FR/AC v8 wording (AC-CUST-03-12, no active qualifier)
  and ADR-003 govern.
- The draw.io FR-CUST-01 page still carries the old "içinde-geçen" (contains) match
  note — KR-01 word-start matching governs.
- KR-04 default page size: UI default 15 (options 15/30/50) vs API default 20 —
  open item recorded in ADR-005.
