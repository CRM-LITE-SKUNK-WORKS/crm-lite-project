# FE-ADR-008: Error Handling — `messageKey` Is the Contract; Backend Text Is Never Rendered

## Status
Accepted (2026-07-23). **Read together with FE-ADR-012 (i18n)** — error text is
served by the same catalogue, not a parallel mechanism.

## Context
Every backend service returns the identical error envelope:

```json
{"timestamp": "…", "status": 409, "error": "Conflict",
 "messageKey": "MSG-CUST-DUP-NATID",
 "message": "A customer already exists with this Nationality ID",
 "path": "/api/customers", "validationErrors": null}
```

ADR-009 §Consequences states the intent plainly: *"401/403 responses are uniform
across the platform, keeping the frontend's error handling single-pathed."*

`functional-requirements.md` documents the design choice behind the `message`
field: the backend *"deliberately returns `messageKey`s so localization stays a
catalog concern."* This was verified against the source on 2026-07-23:
`Accept-Language`, `LocaleResolver`, `LocaleContextHolder`, `MessageSource` and
`messages*.properties` produce **zero matches** anywhere under `backend/`. The
`message` field is therefore a fixed English developer string with no
localization path and no analyst approval.

## Decision

### 1. `messageKey` is the only field used to select user-facing text
The frontend resolves `messageKey` against the i18n catalogue (FE-ADR-012).

### 2. The backend `message` field is NEVER rendered to the user
It may be logged to the console for diagnostics. It must not reach the DOM —
not as a fallback, not "just for unknown keys", not in a toast, not in a
tooltip.

**Why this is absolute:** the field is untranslated English, is not in the
analyst message catalogue, and is worded for developers
(`"must contain digits only"`). Rendering it produces an interface that switches
to English mid-sentence when the UI is Turkish, showing text no analyst
approved. It also creates a silent dependency on server wording that no test
covers.

### 3. Unknown `messageKey` → generic text + console warning
```
resolve(key) → catalogue hit ? text : (console.warn(key), t('UI-ERROR-GENERIC'))
```
The key is never swallowed silently and never displayed raw. A missing
translation is a bug that must be visible to developers and invisible to users.

### 4. Errors are classified by intent, not by status code alone
| Class | Trigger | Surface |
|---|---|---|
| **Field** | `400` with `validationErrors` | On the control (FE-ADR-007 §4) |
| **Business** | `409`, `400` with a domain key | Inline banner or dialog, in context |
| **Auth** | `401` / `403` | FE-ADR-005 §4 — redirect, retry-once, or hard fail |
| **Availability** | `503` | Toast: service temporarily unavailable |
| **Not implemented** | `501` | Should be unreachable — see §6 |
| **Unexpected** | `5xx`, network, parse failure | Generic toast + console error |

### 5. One interceptor classifies; features decide presentation
A single `HttpInterceptor` in `core/` normalizes every failure into a typed
`ApiError { status, messageKey, validationErrors }`. It handles only the
cross-cutting cases itself (401 redirect, CSRF retry-once). It does **not**
decide *where* a business error is shown — a duplicate-Nationality-ID error
belongs beside the field in the create wizard, not in a global toast.

### 6. `501 MSG-FEATURE-NOT-IMPLEMENTED` must be unreachable
`accountNumber` and `orderNumber` return `501` until the account/order domains
exist. FE-ADR-013 disables those inputs, so the frontend never sends them. A
`501` reaching the interceptor therefore indicates a **frontend bug** and is
logged as an error, not shown as a user-facing message.

### 7. Keys with no analyst-catalogue entry get project-authored text
Ten keys the backend returns are absent from the analyst catalogue:
`MSG-VALIDATION-ERROR`, `MSG-INTERNAL-ERROR`, `MSG-SERVICE-UNAVAILABLE`,
`MSG-FEATURE-NOT-IMPLEMENTED`, `MSG-ADDR-LAST-DELETE`,
`MSG-ADDR-PRIMARY-DELETE`, `MSG-LOOKUP-NOT-FOUND`, `MSG-AUTH-UNAUTHORIZED`,
`MSG-AUTH-FORBIDDEN`, `MSG-AUTH-CSRF-REJECTED`.

They are still translated in the catalogue with **project-authored** EN/TR text,
marked as such so the analyst-sourced entries stay distinguishable from ours.
*Whether the analysts should supply official wording is an open question —
`docs/frontend/scope-and-conflicts.md`.*

### 8. Confirmation messages are frontend-only
`MSG-ADDR-DELETE-CONFIRM`, `MSG-CUST-DELETE-CONFIRM` and the other `*-CONFIRM`
keys have **no backend endpoint**. `docs/api/customer-service.md` §K is explicit:
*"the confirmation modal is a frontend interaction — the backend has no
'confirm' endpoint."* They are catalogue entries the frontend renders before
calling the real operation.

## Consequences
- The UI can be fully translated without a single backend change — the design
  goal `functional-requirements.md` set out.
- There is exactly one place to add a new error: the catalogue. No `switch` on
  status codes duplicated across features.
- Backend teams may reword the `message` field freely without breaking the UI,
  because nothing reads it.
- New backend `messageKey`s that nobody adds to the catalogue degrade to a
  generic message plus a console warning — visible in development, harmless in
  production.
- **A catalogue-integrity unit test closes that gap** (decided 2026-07-23): it
  asserts that every `messageKey` documented in `docs/api/*.md` and
  `functional-requirements.md` has both an EN and a TR entry. A key added to the
  backend without a translation turns the suite red instead of surfacing as a
  generic message in production. Written during scaffolding.
