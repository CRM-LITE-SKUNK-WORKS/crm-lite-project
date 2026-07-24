# FE-ADR-013: Scope Management — Mock UI Is Wider Than the Existing Backend

## Status
Accepted (2026-07-23). The frontend counterpart of the backend's
`document-delta.md` discipline.

## Context
The analyst mock contains **seven** screens. The backend implements the customer
aggregate (FR-CUST, FR-ADDR, FR-CNTC) and authentication, and nothing else.
`traceability-matrix.md` §Deferred lists the gap explicitly: FR-ACCT
(account-service), FR-PROD (product-service), FR-SALE (order-service) and
FR-LANG are **"not implemented"**, and PROJECTBRAIN §2 marks all three services
as *"Planlı, sınır analist-final değil"* — planned, boundary not analyst-final.

The temptation this ADR exists to prevent is specific and predictable: a mock
that *looks* complete invites a frontend that fakes the missing half with stub
data, because the screens are right there and the mock even ships sample
records.

The backend already refuses this. PROJECTBRAIN §10 states of the deferred
cross-service checks: *"Bu kontrollerin 'yapıldığı' HİÇBİR yerde iddia
edilmiyor"* — nowhere is it claimed that these checks are performed. The
frontend adopts the same honesty.

## Decision

### (a) Only functionality with an existing backend is built
No mock data, no placeholder implementation, no "works for the demo" behaviour
for anything lacking a backend endpoint. If an endpoint does not exist, the
feature does not exist in the UI.

**Why:** a fake that looks real is worse than an absence. It cannot be
distinguished from working software in a demo, it accumulates code that must be
deleted when the real service arrives, and it silently converts "not built yet"
into "apparently broken" for anyone who tries it.

### (b) Currently OUT of scope

| Item | Domain | Backend status |
|---|---|---|
| **Offer Selection** screen (entire) | FR-PROD + FR-SALE | Service does not exist |
| **Product Configuration** screen (entire) | FR-PROD | Service does not exist |
| **Submit Order** screen (entire) | FR-SALE | Service does not exist |
| Customer Info → **account section** (`Account name`, `Billing address`, `Account status/number/type`, `Create new account`, `Edit account`, `Delete account`) | FR-ACCT | Service does not exist |
| Customer Info → **product section** (`Product offer`, `Campaign`, `View product`, `Deactivate product`) | FR-PROD | Service does not exist |
| Customer Info → account row actions (`Start new sale`, `Transfer`, `Service address change`) | FR-SALE | Service does not exist |
| Customer Search → `accountNumber` filter | FR-ACCT | Parameter **recognized**, returns `501 MSG-FEATURE-NOT-IMPLEMENTED` |
| Customer Search → `orderNumber` filter | FR-SALE | Parameter **recognized**, returns `501` |

The two search filters are a distinct case: the parameters exist in the
contract, so the inputs are rendered but **disabled**, and no request ever
carries them (FE-ADR-008 §6 treats a `501` reaching the interceptor as a
frontend bug).

### (c) Currently IN scope
- **Customer Search** — `GET /api/customers` (browse + filter, ADR-005)
- **Create Customer** — `POST /api/customers` (3-step wizard, atomic create)
- **Customer Info** — demographic, address and contact sections only:
  `GET/PUT /api/customers/{n}`, `DELETE`, `/addresses` CRUD +
  `PATCH .../primary`, `GET/PUT /contact-medium`
- Supporting reference data — `GET /api/cities`, `/api/cities/{id}/districts`,
  `/api/lookups/**`
- Authentication shell — session probe, login redirect, logout (FE-ADR-005)

### (d) Out-of-scope areas are hidden, not stubbed
A section with no backend is **not rendered at all**. It is not shown greyed
out, not shown with a spinner, not shown with "coming soon" placeholder rows.

Consequence for Customer Info: the tab strip shows **three** tabs — Customer
info, Address, Contact medium. The "Customer account" tab is absent.

The exception is the two search filters in §(b), where the analyst's filter
layout is preserved and the inputs are disabled with an explanatory hint —
because there the *contract* exists and only the *implementation* is deferred.

When the corresponding service ships, the section is unhidden. Because the
components were never written against fake data, unhiding is additive work.

### (e) The mock is binding for design, not for behaviour
> **Mock = visual and layout reference. Backend contract + FR/AC = behaviour.**

The mock is a prototype: some functions are approximated, some are absent, and
some contradict the real contract. Where mock behaviour and the backend
contract or the FR/AC documents disagree, **the backend contract and FR/AC
win**, every time.

Verified examples (full list in `docs/frontend/mock-ui-analysis.md` §5A):
- mock sends `city`/`district` as **names**; the API takes `cityId`/`districtId`
- mock formats dates `DD.MM.YYYY` on the wire; the API uses ISO `YYYY-MM-DD`
- mock uses gender values `male`/`female`; the API uses `"Male"`/`"Female"`
- mock's row identifier is `customerId`; the response field is `customerNumber`
- mock folds Turkish diacritics when matching; the backend does not
- mock validates duplicate Nationality ID against two hardcoded values and has
  no MERNIS step at all

Design details — spacing, colour, type scale, component structure, layout grids
— remain **100% binding** (FE-ADR-011).

### (f) Every exclusion and every conflict is recorded
`docs/frontend/scope-and-conflicts.md` is the frontend counterpart of
`document-delta.md`. Every scope exclusion and every mock/backend conflict is
logged there with an explicit status: **analiste soruldu** (asked) /
**karar bekliyor** (awaiting decision) / **karara bağlandı** (decided).

Nothing is resolved silently. If a discrepancy is found and fixed in passing, it
still gets a row — the record is what makes the decision auditable later, which
is precisely the value `document-delta.md` provides on the backend side.

## Consequences
- The delivered application is smaller than the mock. This is correct and must
  be communicated to stakeholders early, because a mock walkthrough sets a
  different expectation.
- No throwaway stub code, and no "remove the fake data" cleanup task later.
- When account/product/order services arrive, each becomes a new feature
  directory (FE-ADR-003 §Consequences) plus the unhiding of an existing section.
- `scope-and-conflicts.md` becomes a standing maintenance obligation; a stale
  record is worse than none.
- Some analyst acceptance criteria cannot be satisfied yet, by construction.
  They are listed as out of scope rather than reported as passing.
