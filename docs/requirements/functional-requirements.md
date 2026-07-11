# Functional Requirements — Implementation Summary

Source of truth: `docs/source/requirements/CRM_Lite_FR_AC_v8_Final.docx`.
This file summarizes what the backend implements today and where behaviour is
intentionally deferred. IDs refer to the FR document.

## Implemented (customer-service, port 8082)

| FR | Capability | Notes |
|---|---|---|
| FR-CUST-01 | Customer search | `GET /api/customers` (canonical, only search endpoint). KR-01 semantics: firstName = word-start match over First+Middle, lastName = word-start over Last Name, AND-ed together; GSM prefix; NAT ID / Customer ID exact; groups OR-ed; only active customers; 20/page; firstName→lastName sort (AC-CUST-01-01..09, KR-04) |
| FR-CUST-02 | Customer detail | `GET /api/customers/{customerNumber}` — business number, never the internal id |
| FR-CUST-03 | Atomic create | `POST /api/customers` with `demographic` + `addresses[]` + `contactMedium`; persists PARTY, IND, PARTY_ROLE, CUST, ADDR, CNTC_MEDIUM in one transaction (AC-CUST-03-21); KR-10 MERNIS verification before persist |
| FR-CUST-04 | Demographic update | `PUT /api/customers/{customerNumber}`; NAT ID uniqueness excludes own record (AC-CUST-04-04) |
| FR-CUST-05 | Soft delete | `DELETE /api/customers/{customerNumber}`; passivates CUST/PARTY_ROLE/PARTY/IND/ADDR/CNTC_MEDIUM with deleted/updated audit metadata |
| FR-ADDR-01..05 | Address management | List/add/update/delete/set-primary under `/api/customers/{n}/addresses`; first address auto-primary, one active primary (DB partial unique index), last-address and primary-delete guards, cascading city→district, `GET /api/cities`, `GET /api/cities/{id}/districts` |
| FR-CNTC-01..02 | Contact medium | `GET/PUT /api/customers/{n}/contact-medium`; Email+Mobile required, VR-EMAIL/VR-PHONE/VR-MOBILE enforced |
| KR-10 | Fake MERNIS | `backend/mernis-stub` (:8084); rejection or unavailability ⇒ customer NOT created (fail closed) |

Validation formats implemented exactly per the FR catalog: VR-NAME (Turkish letters,
1–50, trim-first), VR-NATID, VR-EMAIL, VR-PHONE, VR-MOBILE.

## Message keys

All keys from the FR message catalog used by the backend, plus **documented project
additions** the catalog does not define (framework/integration outcomes):
`MSG-SEARCH-CRITERIA-REQUIRED`, `MSG-FEATURE-NOT-IMPLEMENTED`, `MSG-VALIDATION-ERROR`,
`MSG-INTERNAL-ERROR`, `MSG-NATID-VERIFY-FAILED`, `MSG-SERVICE-UNAVAILABLE`,
`MSG-ADDR-LAST-DELETE`, `MSG-ADDR-PRIMARY-DELETE`.

## Intentionally deferred (explicit TODOs, never silent)

- `accountNumber` / `orderNumber` search → **501 MSG-FEATURE-NOT-IMPLEMENTED** until the
  account/order domains exist (KR-02 resolution will live there).
- Active-product check on customer delete (AC-CUST-05-03) → no-op until product/account domains exist.
- Billing-account passivation on customer delete (part of AC-CUST-05-04) → cross-service future work.
- Address in-use check (AC-ADDR-04-04, MSG-ADDR-IN-USE) → no-op until account/service-address records exist.
- AUTH, ACCT, PROD, SALE, LANG requirement groups → other services / frontend, out of customer-service scope.

## Superseded wording (see ADR-003)

Nationality ID uniqueness is **global and permanent** per the 2026-07-10 analyst
decision. The use-case document (FR-CUST-03 step 4.4) and the draw.io FR-CUST-03 page
still say "aktif müşteri" (active-only) — they are outdated, not canonical.
