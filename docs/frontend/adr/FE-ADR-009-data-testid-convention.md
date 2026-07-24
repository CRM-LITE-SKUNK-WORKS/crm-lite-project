# FE-ADR-009: `data-testid` Convention

## Status
Accepted (2026-07-23). Formalizes the rule recorded in
`docs/frontend/mock-ui-analysis.md` §8.

## Context
The mock UI bundle contains **zero** `data-testid` attributes — verified by
extracting all seven screen documents and counting occurrences (0 in each). Test
selectors are therefore entirely a layer this project adds.

The convention is not unprecedented in this repository: the Keycloak login theme
(`infra/keycloak/themes/crm-lite/login/login.ftl`) already ships
`login-page`, `login-username-input`, `login-password-input`,
`login-password-toggle`, `login-submit`, `login-alert`,
`login-locale-switcher`, `login-locale-en`, `login-locale-tr`. The frontend
continues the same convention rather than inventing a second one.

## Decision

### 1. Every interactive and every assertable element carries `data-testid`
It is added **when the element is written**, not retrofitted when a test needs
it. An element without a `data-testid` is an incomplete element.

Required on:

| Category | Examples |
|---|---|
| Form controls | input, select, datepicker, checkbox, radio |
| Buttons | primary/secondary/danger, icon buttons, link buttons |
| Navigation | sidenav items, tabs, pagination controls, logo link |
| Table structure | table root, each row (identified), clickable cells/links |
| Overlays | modal root, confirm dialog, toast, dropdown panel |
| State surfaces | empty state, error banner, field error text, loading/skeleton |
| Wizard | stepper root, each step, step navigation buttons |

### 2. Naming: `{feature}-{section}-{element}[-{variant}]`, kebab-case, English
```
customer-search-filter-id-number-input
customer-search-filter-clear-button
customer-search-submit-button
customer-search-results-table
customer-search-results-row-1001
customer-search-results-row-1001-open-link
customer-search-empty-state
customer-search-pagination-next
customer-search-page-size-select
customer-create-step-2
customer-create-address-add-button
customer-create-address-dialog
customer-detail-tab-contact
customer-detail-delete-confirm-yes
app-toast
app-language-switcher
```

### 3. Dynamic elements are identified by business identifier, never by index
```
✅ customer-search-results-row-1001        (customerNumber)
✅ customer-detail-address-4               (addressId)
❌ customer-search-results-row-0           (array index)
```
Indices shift when sorting, pagination or filtering changes, producing tests
that pass against the wrong row. The backend already exposes stable business
identifiers (`customerNumber`, `addressId`) — they are used.

### 4. `data-testid` is NEVER a CSS selector
No stylesheet, no Tailwind `@apply`, no `[data-testid=...]` rule anywhere.
Styling attaches to classes and component structure.

**Why:** the moment a selector styles it, renaming a test id becomes a visual
regression, and the attribute stops being free to change with the test suite.
The two concerns must be independently movable.

### 5. `data-testid` is NEVER read by application logic
No `querySelector('[data-testid=...]')` in a component or service, no branching
on it, no use as a map key. It is write-only from the application's perspective
and read-only from the test's.

### 6. It is never derived from translated text
Test ids are fixed English identifiers. Deriving one from a label would make the
entire suite fail when the UI language changes (FE-ADR-012) — the exact coupling
this rule exists to prevent.

### 7. It is retained in production builds
Attributes are **not** stripped by the build. E2E tests run against the
containerized application (FE-ADR-010), which is the production artifact; a
selector that exists only in development tests nothing that ships.
The cost is a few bytes per element, and the alternative is a build-specific
divergence between what is tested and what is shipped.

### 8. `data-testid` does not replace accessibility
`aria-label`, `role`, `aria-current`, `aria-invalid` and correct semantic
elements are provided independently and correctly (FE-ADR-011 §g). The mock's
existing labels are carried over: `Go to customer search`, `Log out`,
`Previous page`, `Next page`, `Dismiss notification`, `Edit customer info`,
`Delete customer`, `Edit address`, `Delete address`, `Close`.

A test **may** legitimately assert on accessible roles and names; `data-testid`
exists for the cases where that is ambiguous or brittle, not to replace it.

## Consequences
- Tests are decoupled from markup structure, CSS classes and translated text —
  the three things that change most often.
- Reviewers can reject a component that ships without test ids on the same
  footing as one that ships with a hardcoded string (FE-ADR-012 §b).
- The convention is uniform across the Keycloak login page and the Angular
  application, so an end-to-end journey that starts at login uses one selector
  vocabulary.
- **No lint rule enforces `data-testid` presence** (decided 2026-07-23). In
  Angular templates "interactive element" cannot be detected reliably, so such
  a rule produces false positives on read-only or decorative markup; the
  predictable result is a spread of `eslint-disable` comments, after which the
  rule enforces nothing. Enforcement is therefore **PR review plus the E2E
  suite itself** — a missing selector makes the test unwritable, which is
  feedback that arrives naturally and cannot be silenced with a comment.
  (The layer-boundary rule in FE-ADR-003 *is* linted, because there the
  detection is exact.)

  > **Refined 2026-07-24 — still not an ESLint rule.** A narrow, dependency-free
  > check was added at `frontend/scripts/check-conventions.mjs`
  > (`npm run check:conventions`). It does not reopen the decision above; it
  > satisfies the same reasoning by construction:
  >
  > - **Narrow.** It inspects only markup whose interactivity is unambiguous —
  >   `<button>`, `<input>`, `<select>`, `<textarea>`, an `<a>` that actually
  >   navigates, and any element carrying a `(click)` handler. Decorative markup
  >   is never examined, so the false-positive surface the ESLint rule would
  >   have had does not exist.
  > - **Unsilenceable.** There is no inline suppression, deliberately: per §1 the
  >   fix is always to add the attribute, never to exempt the element. This is
  >   what an `eslint-disable`-able rule could not guarantee.
  >
  > The same script also enforces FE-ADR-012 §b (no hardcoded user-visible text,
  > including literal `title` / `aria-label` / `placeholder` / `alt`). PR review
  > and the E2E suite remain the primary enforcement; the script is a safety net
  > that catches the omission before review does. Day-to-day usage:
  > `docs/frontend/testing-conventions.md`.
