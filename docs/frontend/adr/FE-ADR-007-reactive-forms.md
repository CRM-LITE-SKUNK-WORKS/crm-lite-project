# FE-ADR-007: Reactive Forms; Client Validation Is UX Only

## Status
Accepted (2026-07-23).

## Context
The forms in scope are not trivial. Create Customer is a **three-step wizard**
(`docs/frontend/mock-ui-analysis.md` §6.3) with a nested address dialog, a
dynamic address array, cross-field rules (age ≥ 18 derived from birth date,
city→district dependency) and per-step gating of the Next button. Customer Info
adds two more modal edit forms over the same field sets.

The backend enforces its own validation catalogue independently — VR-NAME,
VR-NATID, VR-EMAIL, VR-PHONE, VR-MOBILE plus business rules (birth date not in
the future, age ≥ 18, globally unique Nationality ID, district belongs to city)
— and returns `400` with a `validationErrors` map on failure.

## Decision

### 1. Reactive Forms only; template-driven forms are not used
`FormGroup` / `FormControl` / `FormArray`, built with `NonNullableFormBuilder`
and fully typed (which strict mode, FE-ADR-002 §2, makes meaningful).

**Why not template-driven:** validation state would live in the template as
directives, which cannot express the wizard's cross-step and cross-field rules
without contortion; typed forms are unavailable; and unit-testing a form
requires rendering a component instead of instantiating a `FormGroup`. The
address `FormArray` alone (add / edit / remove / promote-to-primary) is
impractical with `ngModel`.

Mixing the two models in one application is worse than either; the choice is
global.

### 2. Client validation mirrors backend rules — for feedback speed only
Client-side rules exist so the user sees an error in milliseconds instead of
after a round trip. They are duplicated deliberately, and the duplication is
**documented** in `docs/frontend/mock-ui-analysis.md` §6.2/§6.3 so it can be
audited against the backend catalogue.

### 3. The backend is always the final authority
Absolute rules:
- A request is **never** skipped because the client believed it would fail.
- A `400` response is **never** treated as a frontend bug. Rules exist that the
  client cannot evaluate: Nationality ID uniqueness across **soft-deleted**
  records (ADR-003), MERNIS verification (KR-10), district↔city membership
  against real reference data.
- Some backend outcomes have **no client-side equivalent at all** and must be
  handled as first-class results, not edge cases:
  `409 MSG-CUST-DUP-NATID`, `400 MSG-CUST-NATID-VERIFICATION-FAILED`,
  `503 MSG-MERNIS-UNAVAILABLE`, `409 MSG-ADDR-LAST-DELETE`,
  `409 MSG-ADDR-PRIMARY-DELETE`.

The mock illustrates the trap: it validates a *hardcoded list of two* registered
Nationality IDs client-side and has no MERNIS step whatsoever. Copying that
behaviour would produce a form that reports success where the real API returns
409 or 503.

### 4. `validationErrors` is mapped back onto controls
A `400` carrying `validationErrors: { "contactMedium.email": "…" }` is mapped
onto the matching control via `setErrors({ server: messageKey })`, so
server-detected problems render in the same `FormField` error slot as local
ones. The user sees one consistent error surface regardless of origin.
Server errors clear as soon as the control changes.

### 5. Submit buttons are disabled by form validity, not by a manual flag
This preserves the mock's documented behaviour (Next disabled until required
fields are filled, `AC-CUST-01-02`'s "Search disabled while all filter fields
are empty") without a parallel boolean that can drift from the form's real
state.

### 6. Field-level error text comes from the i18n catalogue
Validation messages are `MSG-VAL-*` keys resolved through FE-ADR-012, never
literal strings in a validator. A custom validator returns
`{ 'MSG-VAL-AGE-MIN': true }`; the template resolves the key.

## Consequences
- Every form is unit-testable without rendering: build the `FormGroup`, set
  values, assert validity.
- Validation logic is duplicated between client and server by design. When the
  backend catalogue changes, the frontend must follow — the coupling is recorded
  in `docs/frontend/scope-and-conflicts.md` so it is not forgotten.
- Typed forms plus strict mode make a renamed contract field a compile error in
  the form definition too, not only in the HTTP layer.
- Error handling has exactly one path (FE-ADR-008): field-scoped errors land on
  controls, request-scoped errors land on the toast/banner surface.
