# ADR-004: Keycloak as the Authentication Boundary

## Status
Proposed (2026-07-10) — not implemented in the customer-service refactor.

## Context
auth-service is an empty skeleton and the API gateway currently runs with a temporary
`permitAll` security chain. A decision is needed on where identity will come from so
that data written today remains attributable later.

## Decision (direction, to be confirmed when auth is implemented)
1. If Keycloak is selected as the identity provider, **Keycloak issues the tokens**.
   **auth-service must NOT independently mint a second, custom JWT** on top of (or in
   parallel with) Keycloak tokens; it may at most act as a thin facade (login UI
   redirect, token exchange) but never as a second token authority.
2. The gateway validates Keycloak-issued JWTs and forwards identity downstream.
   The current `permitAll` configuration is **temporary** scaffolding for local
   development only and must be replaced before anything production-like.
3. Audit attribution: all customer-domain tables already persist
   `created_by` / `updated_by` / `deleted_by` as `VARCHAR(64)`. Until authentication
   exists these carry the constant `system`. The column size and semantics are chosen
   so the future Keycloak `sub` claim (a UUID string) fits without migration.

## Consequences
- No auth work happens inside customer-service; it only preserves the audit columns.
- When Keycloak lands: replace `system` with the token's `sub` (or preferred username),
  tighten the gateway chain, and remove this ADR's "Proposed" status.
