# Functional Requirements — Implementation Summary

Source of truth: `docs/source/requirements/CRM_Lite_FR_AC_v8_Final.docx`
(**16.07.2026 revision** — see [document-delta.md](document-delta.md) for what changed).
This file summarizes what the backend implements today and where behaviour is
intentionally deferred. IDs refer to the FR document.

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
catalog outages, ADR-002), `MSG-ADDR-LAST-DELETE`, `MSG-ADDR-PRIMARY-DELETE`,
`MSG-AUTH-UNAUTHORIZED` (401), `MSG-AUTH-FORBIDDEN` (403),
`MSG-AUTH-CSRF-REJECTED` (403, CSRF) — ADR-008/009. (`MSG-AUTH-INVALID-CRED`
remains a Keycloak login-page concern, not an API key.)

**Retired:** `MSG-SEARCH-CRITERIA-REQUIRED` — removed together with the mandatory
search-criteria rule (ADR-005); no endpoint uses it anymore.

## Intentionally deferred (explicit TODOs, never silent)

- `accountNumber` / `orderNumber` search → **501 MSG-FEATURE-NOT-IMPLEMENTED** until the
  account/order domains exist (KR-02 resolution will live there).
- Active-product check on customer delete (AC-CUST-05-03) → no-op until product/account domains exist.
- Billing-account passivation on customer delete (part of AC-CUST-05-04) → cross-service future work.
- Address in-use check (AC-ADDR-04-04, MSG-ADDR-IN-USE) → no-op until account/service-address records exist.
- **ACCT** (FR-ACCT-01..04, ACCT_TP/CUST_ACCT ownership) → planned account-service.
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
