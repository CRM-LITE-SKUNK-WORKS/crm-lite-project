# FE-ADR-003: Feature-Based Architecture with `core/` and `shared/` Layers

## Status
Accepted (2026-07-23). Mirrors the aggregate boundary established by
**ADR-001** on the backend.

## Context
The buildable scope (FE-ADR-013) is three screens: Customer Search, Create
Customer and Customer Info. They are not independent: the address card grid and
the address form dialog appear **identically** in Create Customer step 2 and in
Customer Info's Address tab (`docs/frontend/mock-ui-analysis.md` §6.4 records
them as byte-identical markup in the mock).

Backend **ADR-001** decided that address and contact are *not* separate
services: they are internal modules of `customer-service`, justified by the
atomic create. The frontend must not invent a boundary the backend deliberately
refused.

Two organizing principles were evaluated: layer-first (`components/`,
`services/`, `models/`) and feature-first.

## Decision

### 1. Three top-level layers
```
frontend/src/app/
├── core/          # application-wide singletons; imported once
├── shared/        # reusable, feature-agnostic building blocks
└── features/      # user-facing capabilities, lazily routed
```

**`core/`** — instantiated once, injected everywhere: HTTP interceptors
(CSRF/error/auth per FE-ADR-005 and FE-ADR-008), the session service, the i18n
service and catalogs (FE-ADR-012), route guards, and the lookup cache
(cities/districts).
`core/` **never** imports from `features/`.

**`shared/`** — the EDS component library (`shared/ui/`, FE-ADR-011), plus
formatting pipes and structural directives. Contains **no** business logic, **no**
HTTP calls and **no** knowledge of customers.
`shared/` **never** imports from `core/` or `features/`.

**`features/`** — one directory per capability, lazily loaded via
`loadComponent`/`loadChildren`. May import from `core/` and `shared/`; **never**
from a sibling feature.

### 2. Feature layout
```
features/customer/
├── search/            # Customer Search screen
├── create/            # Create Customer 3-step wizard
├── detail/            # Customer Info tabbed screen
├── address/           # ← sub-module, NOT a feature
├── contact/           # ← sub-module, NOT a feature
├── data/              # customer HTTP client + typed contract models
└── model/             # DTO interfaces mirroring the backend contract
```

### 3. Address and contact are sub-modules of the customer feature
`features/customer/address/` and `features/customer/contact/` are **not**
top-level features and never become ones.

**Why:** ADR-001 draws the aggregate boundary around the customer. Their
endpoints are nested under the customer
(`/api/customers/{n}/addresses`, `/api/customers/{n}/contact-medium`), they have
no independent identity in the UI, and the atomic create sends them inside one
`POST /api/customers` body. A top-level `features/address/` would imply a
navigable, independently meaningful capability that neither the backend nor the
screens have. ADR-001's wording is explicit and this ADR restates it for the
frontend: *address-service / contact-service will never be separate deployables.*

The shared address UI (`AddressCardGrid`, `AddressFormDialog`) therefore lives in
`features/customer/address/` and is consumed by both `create/` and `detail/` —
same feature, so no cross-feature import rule is violated.

### 4. Rejected: layer-first organization
A `components/ services/ models/` tree places `customer-search.component.ts`
next to `submit-order.component.ts` and far from the service it uses. Every
change touches three distant directories, deletion of a feature becomes an
archaeology exercise, and lazy-loading boundaries do not fall out of the
structure. It scales inversely with the codebase.

### 5. Rejected: one feature per screen
Making `customer-search`, `customer-create` and `customer-detail` three
top-level features would put the shared address dialog in an awkward position —
either duplicated, or promoted into `shared/` where business-aware code does not
belong. Grouping them under `features/customer/` keeps the shared piece inside
the boundary that owns it.

## Consequences
- The import direction is a one-way graph (`features → core|shared`,
  `core → shared`) and is **mechanically enforced** (decided 2026-07-23) via
  ESLint's built-in `no-restricted-imports` — no extra plugin is needed:
  `core/` and `shared/` may not import `features/*`, and `shared/` may not
  import `core/*`. Enforcement is mechanical because a layer violation is a
  one-line import that reads innocently in review, while undoing it later is a
  refactor.
- When `account-service` and `product-service` arrive (FE-ADR-013), they become
  **new sibling features** (`features/account/`, `features/product/`) without
  restructuring anything — the account tab in Customer Info will import a
  component the account feature exposes.
- The customer feature is the largest directory in the app. That is a faithful
  reflection of the backend, where `customer-service` is likewise the largest
  deployable.
- A per-feature i18n catalog (FE-ADR-012 §g) fits this structure directly:
  `features/customer/search/i18n.ts`.
