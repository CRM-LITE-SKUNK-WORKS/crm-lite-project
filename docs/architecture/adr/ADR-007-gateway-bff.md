# ADR-007: API Gateway as the BFF; auth-service Removed

## Status
Accepted (2026-07-17) — implemented. Partially supersedes ADR-004's
"auth-service as thin facade / token exchange" allowance.

## Context
A browser-facing BFF must own the OAuth2 client role, the session and the
cookies. Two placements were evaluated: (A) the existing api-gateway, (B) the
empty auth-service skeleton. The governing principle: the BFF should live at the
edge through which browser API traffic already flows.

## Decision
1. **api-gateway (Spring Cloud Gateway Server WebMVC, servlet stack) is the BFF**
   and the only public browser edge. It is the OAuth2 Authorization Code + PKCE
   client against Keycloak (`oauth2Login`), owns the HTTP session, the CSRF
   surface (ADR-008) and the `/api/session/me` probe, and performs RP-initiated
   Keycloak logout.
2. **Token custody is fully server-side.** Access and refresh tokens live in the
   gateway's session-bound `OAuth2AuthorizedClientService`. The browser receives
   ONLY the HttpOnly session cookie and the readable `XSRF-TOKEN` cookie — never
   a token, in any cookie, body, or URL. This is a full BFF, not a token-cookie
   pattern.
3. **TokenRelay at the routes.** The WebMVC gateway's `TokenRelay` filter swaps
   the session for the user's access token (`Authorization: Bearer`) before
   proxying to domain services; `RemoveRequestHeader=Cookie` guarantees browser
   cookies never leave the edge. Refresh is transparent via the authorized-client
   manager while the Keycloak SSO session is valid.
4. **Option B rejected**: session ownership in auth-service would split edge
   responsibilities, require cross-service session sharing (or a per-request
   internal call on the critical path of every API request), complicate redirect
   URIs across two origins, and add a runtime whose entire content fits in the
   gateway's existing security chain.
5. **auth-service is REMOVED** — module, config-repo file, root-POM module entry
   and its `/api/auth/**` route. All 20 classes were empty; there was no behavior
   to preserve. With it went the JJWT dependencies (ADR-006 §4). It must not be
   restored; a future *profile* need is a new service designed under ADR-011.

## Consequences and accepted trade-offs
- **In-memory session + authorized clients**: a gateway restart logs everyone
  out, and sessions do not scale beyond a single gateway instance. Accepted for
  local/single-instance operation. The documented scale-out path is Spring
  Session (e.g. Redis) plus a shared/persistent `OAuth2AuthorizedClientService`;
  nothing in the design blocks that upgrade.
- The gateway now depends on Keycloak at LOGIN time only (endpoints are
  configured explicitly; no OIDC discovery at startup), so the boot order does
  not gain a hard Keycloak dependency.
- Angular (future) never handles credentials or tokens; it triggers
  `/oauth2/authorization/keycloak`, reads `/api/session/me`, and calls `/logout`
  (see `docs/api/authentication.md`).
