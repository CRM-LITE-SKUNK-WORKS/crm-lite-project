# ADR-009: Zero-Trust Resource Servers and the Role Model

## Status
Accepted (2026-07-17) — implemented via `backend/crm-security-starter`.

## Decision
1. **Every protected domain service validates the Keycloak JWT itself** —
   customer-service and lookup-service are stateless
   `oauth2ResourceServer` (bearer-only) services that independently validate
   **signature (JWKS), issuer (exact canonical match), audience (`crm-api`) and
   expiry**, regardless of what the gateway already checked. Trusting the edge
   alone is not sufficient: direct in-network calls hit the same wall (proved by
   integration tests calling the services without the gateway).
2. **Role model (KR-8):** one realm role **`crm-user`**; the mapper converts
   `realm_access.roles` verbatim to `ROLE_<name>` authorities. Every endpoint
   requires the explicit role — never bare `authenticated()` — at BOTH the
   gateway (session authorities) and each resource server (JWT authorities).
   Only `/actuator/health` is anonymous (orchestration probes). RBAC beyond the
   single role is out of scope (KR-8); the extension path is: add realm roles in
   Keycloak, map them the same way, tighten matchers/`@PreAuthorize` per service.
3. **Shared module `crm-security-starter`** (Spring Boot autoconfiguration)
   carries the defaults: JwtDecoder with issuer/audience validators (JWKS URI
   separately overridable — ADR-006 §6), the realm-role converter, a stateless
   default `SecurityFilterChain`, consistent 401/403 JSON bodies
   (`MSG-AUTH-UNAUTHORIZED` / `MSG-AUTH-FORBIDDEN` in the established error
   shape), `AuditorAware<String>` returning the JWT `sub` (fallback `system`),
   and the opt-in outbound bearer propagation interceptor (ADR-010).
   Every bean is `@ConditionalOnMissingBean` — services override by declaring
   their own bean. Explicit NON-goals: no cookie/session handling, no business
   authorization rules, no domain types, no coupling to gateway internals.
4. **Domain services never parse browser cookies.** Cookie/session extraction is
   exclusively the gateway BFF's job (ADR-007/008); the services' chains are
   `STATELESS` with CSRF legitimately disabled (no cookie-based auth surface).
5. **mernis-stub stays outside the realm** deliberately (ADR-010): it simulates
   an external KPS system, is not gateway-routed, and its container publishes no
   host port.

## Consequences
- Future services (account/product/order/localization) adopt the starter and
  inherit the same validation, role and error contract with one dependency and
  one `crm.security.issuer` property.
- 401/403 responses are uniform across the platform, keeping the frontend's
  error handling single-pathed.
- The starter is a security utility, not a shared-domain library; anything
  business-flavoured is rejected from it by review.
