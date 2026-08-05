# FE-ADR-013: Scope Management — Mock UI Is Wider Than the Existing Backend

## Status
Accepted (2026-07-23). The frontend counterpart of the backend's
`document-delta.md` discipline.

**Amended 2026-07-24** — `account-service` shipped on `dev` (ADR-013/014,
`docs/api/account-service.md`), so the FR-ACCT premise of §b ("service does not
exist") no longer holds. See **§Amendment (2026-07-24)** at the end: FR-ACCT
enters buildable scope, and a "Coming soon" behaviour rule supersedes §d for
the Customer Info product section. Per ADR discipline the original wording
below is retained and marked, never rewritten.

**Amended again 2026-07-31** — `product-service` shipped on `dev`
(`docs/api/product-service.md`), so the FR-PROD premise no longer holds either
for the two viewing requirements. See **§Amendment B (2026-07-31)**: FR-PROD-01
and FR-PROD-02 enter buildable scope and the product section stops being
"Coming soon"; the FR-PROD/FR-SALE *screens* stay out, now for a sharper reason
than "no service" — they are one selection-and-order flow whose backend
(order-service) does not exist. §Amendment A3's "Coming soon" rule is
**superseded for the product section** but remains binding wherever else it is
used. Earlier wording is retained and marked throughout, never rewritten.

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

> ⚠️ **Superseded in part (2026-07-24).** The FR-ACCT account-section row below
> is superseded by §Amendment A1 (account-service now exists; the section is IN
> scope). The FR-PROD product-section row's *behaviour* changed from "hidden"
> to "Coming soon" per §Amendment A3. The `accountNumber` search-filter row is
> **unchanged** — customer-service still returns `501` (follow-up PR pending).
> The table is kept as accepted on 2026-07-23.
>
> ⚠️ **Superseded further (2026-07-31).** The FR-PROD product-section row is
> now superseded outright by §Amendment B1: product-service exists, the section
> is IN scope, and it is neither hidden nor "Coming soon" — it is built. Only
> its `Deactivate product` action stays inert (§Amendment B3). The three
> FR-PROD/FR-SALE **screen** rows are unchanged and stay out (§Amendment B2).

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

> ⚠️ **Superseded (2026-08-05) — see §Amendment C.** Both search-filter rows are
> closed: customer-service resolves `accountNumber`/`orderNumber` for real
> (backend ADR-005 §Addendum 2026-08-05), so the inputs are **enabled** and their
> values ARE sent. The rows above are kept as accepted history.

### (c) Currently IN scope

> ⚠️ **Extended twice.** FR-ACCT joined this list on 2026-07-24 (§Amendment A1)
> and FR-PROD-01..02 on 2026-07-31 (§Amendment B1). The list below is the
> 2026-07-23 wording, kept as accepted.

- **Customer Search** — `GET /api/customers` (browse + filter, ADR-005)
- **Create Customer** — `POST /api/customers` (3-step wizard, atomic create)
- **Customer Info** — demographic, address and contact sections only:
  `GET/PUT /api/customers/{n}`, `DELETE`, `/addresses` CRUD +
  `PATCH .../primary`, `GET/PUT /contact-medium`
- Supporting reference data — `GET /api/cities`, `/api/cities/{id}/districts`,
  `/api/lookups/**`
- Authentication shell — session probe, login redirect, logout (FE-ADR-005)

### (d) Out-of-scope areas are hidden, not stubbed

> ⚠️ **Superseded in part (2026-07-24)** for the Customer Info **product
> section only**, which is now rendered as a visible-but-inert "Coming soon"
> section under the rules of §Amendment A3. The account-tab consequence below
> is moot — the account section is in scope (§Amendment A1). The rule stands
> unchanged for the three FR-PROD/FR-SALE **screens**, which remain entirely
> absent.
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

---

## Amendment (2026-07-24): FR-ACCT enters scope; "Coming soon" rule defined

### Status
Accepted (2026-07-24). Supersedes parts of §b and §d above (marked in place);
everything else in this ADR — §a in particular — stands in full force.

### Context
`account-service` merged to `dev` (commit `635b9a2`): FR-ACCT-01..04 + KR-11
are implemented per backend **ADR-013/ADR-014** with a complete, documented
contract — five endpoints under `/api/accounts/**`
(`docs/api/account-service.md`). FR-PROD and FR-SALE still have no backend, and
customer-service's `accountNumber` search filter still answers `501` — its
conversion to a real account-service call is an explicitly deferred follow-up
PR (`docs/api/account-service.md` §Deliberate limitations).

### A1. The Customer Info account section enters buildable scope
- Implemented as the new sibling feature **`features/account/`** — exactly the
  evolution §Consequences of FE-ADR-003 predicted for this moment.
- The UI works **exclusively with 224 Billing Accounts**. The K-8 automatic 223
  Customer Account is a create-time backend side effect that never appears in
  any response (the list is 224-only; a 223's number answers 404
  `MSG-ACCT-NOT-FOUND`). No UI surface refers to a 223, ever.
- **The KR-11 `accountNumber` is display-only.** It is rendered as read-only
  text and is never bound to a form control. `PUT` sends exactly
  `{accountName, addressId}`; any extra or immutable property is rejected with
  400 `MSG-ACCT-IMMUTABLE-FIELD` — so a "send the whole object back" update
  form is a contract violation, not a shortcut.
- **Delete is passivation.** After the 204 the row stays in the list as
  Passive (AC-ACCT-04-02); ordering is fixed server-side (Active first, then
  Passive, `accountNumber` ascending inside each group — AC-ACCT-01-04) and the
  UI must not re-sort. The active-product delete guard is backend-enforced
  (409 `MSG-ACCT-HAS-PRODUCTS`); the UI surfaces the 409, it never pre-computes
  the check. The confirm prompt (`MSG-ACCT-DELETE-CONFIRM`) and the success
  message (`MSG-ACCT-DELETED`) are frontend-only catalogue keys.
- Tracking rows: `docs/frontend/scope-and-conflicts.md` §1A (including the
  catalogue gap — five project-added `MSG-ACCT-*` keys are not yet in the
  frontend catalogue and must be added before the screens are written).

### A2. Still out of scope (unchanged by this amendment)
- Customer Search `accountNumber` filter: **still rendered disabled** —
  customer-service still returns `501`; enabling it belongs to the follow-up
  PR, not to the frontend. *(Superseded 2026-08-05 — the follow-up PR landed;
  see §Amendment C.)*
- FR-SALE account-row actions (`Start new sale`, `Transfer`,
  `Service address change`): not rendered; the account API deliberately
  exposes no `Action` field (backend ADR-013 §3).
- Offer Selection, Product Configuration and Submit Order screens: no backend,
  not rendered — §d's rule continues to apply to whole screens.

### A3. "Coming soon" behaviour rule — a defined, narrow exception to §d

> ⚠️ **Its original subject is gone (2026-07-31).** The Customer Info product
> section is now BUILT (§Amendment B1), so this amendment no longer governs it.
> The **rule itself stands unchanged** and remains binding wherever "Coming
> soon" is used — currently one control: the `Deactivate product` action
> (§Amendment B3).

§d's "hidden, not stubbed" rule is superseded **for the Customer Info product
section** (`Product offer`, `Campaign`, `View product`, `Deactivate product`):
it is now rendered as a visible but inert **"Coming soon"** section.

**Why §d changes here:** with the account section real, fully hiding the
adjacent product section would misrepresent the screen's final layout to
anyone reviewing progress against the mock. A clearly labelled, inert section
communicates "planned, not built" honestly — which is the very goal §d's
hiding rule served. §a's core prohibition (no fake data, no pretend behaviour)
is untouched: an inert, truthfully labelled section cannot be mistaken for
working software, which is what §a actually forbids.

**The rule, binding wherever "Coming soon" is used:**
1. The section is **visible but fully non-interactive** — controls disabled or
   static, `aria-disabled` where applicable, no affordance suggesting action.
2. **No API call is ever made** for or from the section.
3. **No fake or sample data is shown** — no placeholder rows, no invented
   values (§a stands in full).
4. The section title and **every visible label come from i18n catalogue keys**
   (FE-ADR-012 §b) — including the "Coming soon" text itself.
5. **`data-testid` attributes are still added** (FE-ADR-009 §1), so E2E tests
   can assert the section is present and inert.

---

## Amendment B (2026-07-31): FR-PROD-01..02 enter scope; the sale flow does not

### Status
Accepted (2026-07-31). Supersedes the FR-PROD product-section rows of §b and
§d, and retires §Amendment A3's *subject* while keeping its *rule* (all marked
in place). §a — no fake data, no pretend behaviour — stands in full force and is
the reason the exclusions in B2/B3 below are exclusions rather than stubs.

### Context
`product-service` merged to `dev`: a **deliberately read-only Phase A** slice
implementing FR-PROD-01..02 with a complete, documented contract
(`docs/api/product-service.md`) — `GET /api/products?accountNumber=`,
`GET /api/products/{id}`, plus the catalog reads `GET /api/offers` and
`GET /api/campaigns`. The document is explicit about what is absent: *"No
product creation, provisioning, basket, order, Kafka or Redis exists here.
Product cancellation is explicitly out of phase."*

So FR-PROD is no longer one undifferentiated gap. It splits cleanly:
**viewing** now has a backend; **buying, configuring and cancelling** do not.
This amendment follows that split exactly rather than treating "FR-PROD exists"
as permission to build the whole domain.

### B1. Customer Info product viewing enters buildable scope
- Implemented as the new sibling feature **`features/product/`** — the third
  instance of the evolution FE-ADR-003 §Consequences predicted.
- **Location: inside an EXPANDED account row.** The mock puts the product
  sub-table there and AC-PROD-01-01 calls it an *"expandable per-account
  sub-table"*; the list endpoint is keyed by `accountNumber`, so no
  section-level placement could even know which account to list. This is the
  deferral recorded in `scope-and-conflicts.md` §4.23/2 and §4.24/4 coming due.
- **The two sibling features never import each other.** `AccountSection` takes
  an optional `rowExpansion` **`TemplateRef`** whose context is the
  `accountNumber` **string** and nothing more; Customer Info projects the
  product section into it. `accountNumber` is the product feature's own contract
  parameter, not a borrowed account type. The composition lives in exactly one
  place, which is the direction FE-ADR-003 §Consequences sanctions.
- **No pagination, ever.** FR-PROD-01 defines no pagination rule and the
  endpoint returns a plain array. `shared/patterns/Pagination` is not used, no
  `page`/`size` is sent, and the mock's 4/8/12 selector is not reproduced — the
  same call already made for the account table (scope §4.24/5).
- **Passive products stay listed** (AC-PROD-01-03) in server order. The client
  neither filters by status nor re-sorts — the same stance the account section
  takes on Active-then-Passive rows.
- **`campaignId` is the public `campaign_code`.** Internal campaign ids never
  leave the service, so it is the only campaign identity any UI surface shows.
  A campaign-less product arrives with both campaign fields `null`; rendering
  `"-"` is documented as the frontend's job and is done in the template.
- **Server-derived values are displayed, never recomputed** — product status,
  service type and campaign totals are all computed backend-side. A child
  product's Service Address is its parent's, resolved by walking the parent
  chain server-side: the detail modal is ONE `GET`, and the client never
  fetches a parent or an address itself.
- **FR-PROD-02 is a modal**, not a routed screen: AC-PROD-02-01 is recorded as
  a "detail modal" in `traceability-matrix.md`, the API document names the
  endpoint the same way, and the mock triggers it from a row-level `eye` action
  — every row-level detail interaction in Customer Info is a modal. Its layout
  follows Customer Info's documented read grid, because the mock's own product
  markup is not recoverable (the bundled mock contains no product markup;
  verified 2026-07-31, recorded in scope §4.28/4 and `mock-ui-analysis.md` §6.4).
- **Message keys.** `MSG-PROD-NONE` is FRONTEND-ONLY — a product-less account is
  a `200 []` and the backend never emits the key, so the empty state produces
  it. `MSG-PROD-NOT-FOUND` (404) is a documented **project addition** the
  backend does return, added to the catalogue and to the `i18n.spec.ts`
  backend-key list; `MSG-PROD-NONE` deliberately is not.
- Tracking rows: `docs/frontend/scope-and-conflicts.md` §1B and §4.28.

### B2. Offer Selection / Product Configuration / Submit Order stay OUT
§d's rule for whole screens continues to apply, and the reason is now sharper
than "the service does not exist":

These three screens are **one flow**, not three views of the product catalog.
The mock's Offer Selection is a *selection* step — a basket, then
configuration, then order submission. `docs/api/product-service.md` §Deliberate
limitations is explicit that none of it exists: *"no basket, no order, no
involvement write command on account-service. The §2.7 sale flow (FR-SALE) needs
the order domain and a command boundary on account-service — neither exists."*
Building the first screen alone would produce a basket that can never be
submitted: working software by appearance, a dead end in fact — precisely what
§a forbids. The three screens will be built **together with order-service**.

Consequently `GET /api/offers` and `GET /api/campaigns` — which DO exist and are
gateway-routed — are **not consumed** in this round. No client wrapper was
written for them: unused code for an unbuilt screen is the same debt §a exists
to prevent. The campaign information in the product detail modal comes from
`GET /api/products/{id}`'s own `campaign` field (contract-verified), so the
catalog endpoints are not needed for anything in scope.

### B3. "Deactivate product" stays inert — the one remaining A3 subject
`Deactivate product` is a WRITE, and Phase A has no write endpoint at all;
AC-PROD-01-04 states outright that the Action column offers only a view icon.
The mock's `ban` control is therefore rendered for layout fidelity but is
**disabled and wired to nothing**, with a catalogue-sourced accessible name that
says "coming soon" and a `data-testid` — i.e. §Amendment A3's rule applied to a
single control rather than a whole section. Nothing about it is faked, and no
request can originate from it (asserted in the spec).

### B4. Offer prices: shown as returned, not adjudicated by the UI
`docs/api/product-service.md` §Recorded deviations 1 marks the seeded offer
prices (299.00 / 149.00 / 49.00, campaign total 497.00 derived) as **"Analyst
approval pending"**. Decision: **the UI displays whatever the backend returns**
— it neither invents a price nor renders an "awaiting approval" caveat, because
a caveat would be UI-authored commentary on data the backend owns. In practice
no price is on screen at all this round: prices exist only in the offer/campaign
catalog responses, and those endpoints are not consumed (B2). When the analysts
confirm or change the figures, zero frontend lines change. Recorded in
`scope-and-conflicts.md` §1B.9.

---

## Amendment C (2026-08-05): the Customer Search `accountNumber` / `orderNumber` filters enter scope

### Status
Accepted. Supersedes the two search-filter rows of §(b) and the first bullet of
§A2. Everything else in this ADR — in particular §(a), §(d) and §(e) — stands
and was the reason those rows existed for three rounds.

### Context
Both filters were held out by ONE thing, and `scope-and-conflicts.md` §1.7a/§1.7b
proved it twice rather than assuming it: not the absence of account-service or
order-service (both had shipped), but customer-service's own **unconditional**
`checkNoUnsupportedCrossServiceSearchCriterion`, which threw
`501 MSG-FEATURE-NOT-IMPLEMENTED` whenever either field was non-empty. Backend
ADR-005 §Addendum (2026-08-05) removed that gate and implemented KR-02: each
number is resolved to its owning customer through the owning service's existing
public endpoint, and the result folds into the same KR-01 OR expression.

### C1. The inputs are enabled and their values are sent
The change §1.7a predicted, and no more: the two `FormControl`s lose
`disabled: true`, join `FILTERABLE_CONTROLS` (so they enable Search per
AC-CUST-01-02, ride in the request, receive bound 400 field errors and are
cleared by Clear/reset), and `accountNumber`/`orderNumber` become representable
in `CustomerSearchCriteria`. §(e) still governs everything else: KR-01/KR-02
matching is a **server** rule, so the screen sends the parameters verbatim and
never filters, re-sorts or de-duplicates the loaded table.

### C2. Digit-only input reuses the existing capability
`TextInput`'s `digitsOnly` input — already carrying the ID number / Customer ID /
GSM / Nationality ID fields since 2026-07-29 — is bound to both new fields. No
keydown handler, no per-screen sanitizer and no `type="number"` (which admits
`e`, `+`, `-` and a decimal separator, and is a poor fit for an identifier).
Because `digitsOnly` sanitizes the `input` event, typing, pasting and mobile
numeric keyboards are covered by one rule.

### C3. Identifiers stay strings
KR-11 and KR-12 numbers are fixed-width Luhn-checked identifiers, not quantities:
`Number('0261000010')` would silently drop a significant leading zero. The form
value, the criteria type and the query parameter are all `string`, matching the
backend, which types both parameters as `String` as well.

### C4. `UI-SEARCH-DEFERRED-HINT` is deleted
The helper text existed only to explain the disabled state, and
`scope-and-conflicts.md` §2.24 was still waiting on analyst wording for it. With
the fields live the sentence would be false, so the key is removed from the
catalogue rather than reworded — and §2.24 closes with it. This is §(d) in
reverse: a scope note disappears when the scope note stops being true.
