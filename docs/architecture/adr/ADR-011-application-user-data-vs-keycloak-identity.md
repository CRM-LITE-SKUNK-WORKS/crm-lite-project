# ADR-011: Application User Data vs Keycloak Identity (Workbook USERS Table)

## Status
Proposed (2026-07-17) — implemented as described below; awaiting explicit
analyst sign-off on the documented supersession of the workbook USERS table.

## Context
The final entity workbook (`CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx`) contains
a `USERS (Sistem Kullanicisi)` table with `username` and `password_hash`
columns and three seed users (`ayilmaz`, `edemir`, `mkaya`) with argon2id
placeholder hashes. FR-AUTH-01's AC-AUTH-01-03 says the username is looked up
"in the USERS table". This conflicts with ADR-006: Keycloak is the sole
credential store, and the project must not hash, store or validate passwords.

## Decision
1. **No application USERS/password table exists or may be created.** No Flyway
   migration in any service creates a users table or any password/credential
   column. This is an explicit, recorded supersession of the workbook table —
   not a silent deletion: the conflict is tracked here, in
   `docs/requirements/document-delta.md` and in the traceability matrix.
2. **Keycloak owns** usernames, credentials, enabled/disabled state and their
   lifecycle. "USERS tablosunda bulunamazsa" (AC-AUTH-01-03) is read as "not
   found in the identity store", realized by Keycloak's generic
   invalid-credential handling — which also satisfies AC-AUTH-01-04's
   requirement that a passive user is NOT distinguishable from a wrong password.
3. **The workbook seed users are mirrored as Keycloak development users** in the
   committed realm import: `ayilmaz` and `edemir` enabled, `mkaya` disabled
   (the "passive record" fixture for AC-AUTH-01-04). The workbook's argon2id
   strings are placeholders, not real hashes, and are NOT imported; the dev
   users get deterministic local-only passwords documented in the runbook.
4. **If application-domain profile data is ever genuinely needed** (preferences,
   per-user UI state, …), it lives in a purpose-designed store **keyed by the
   Keycloak `sub` claim** and contains ONLY application-domain data — never
   passwords, password hashes, or credential material of any kind.

## Consequences
- The audit columns (`created_by` etc.) reference Keycloak subjects that have no
  corresponding application-database row — intentional; the identity store is
  external (same pattern as the ADR-002 external catalog references).
- Analyst action: confirm this ADR and (ideally) drop or annotate the USERS
  sheet in a future workbook revision.
