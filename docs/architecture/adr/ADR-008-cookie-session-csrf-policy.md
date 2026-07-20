# ADR-008: Cookie, Session, CSRF and Browser Security Policy

## Status
Accepted (2026-07-17) — implemented at the gateway BFF (ADR-007).

## Decision
1. **Session cookie** (gateway-issued `JSESSIONID`): `HttpOnly`, `SameSite=Lax`,
   `Secure` in every non-local environment (`crm.security.cookie-secure`,
   default `false` only for local http). Lax — not Strict — because the OAuth2
   callback from Keycloak is a top-level cross-site GET that must carry the
   session cookie. Idle timeout 30 minutes (KR-9; `server.servlet.session.timeout`
   from `crm.security.session-idle`).
2. **CSRF protection is ON for all unsafe browser-authenticated methods** — the
   previous unconditional `csrf().disable()` at the gateway is gone.
   `CookieCsrfTokenRepository.withHttpOnlyFalse()` issues the readable
   **`XSRF-TOKEN`** cookie (SameSite=Lax, Secure per environment); clients echo
   it as **`X-XSRF-TOKEN`** — exactly Angular `HttpClient`'s built-in convention.
   The SPA-compatible request handler (Spring Security reference pattern) keeps
   BREACH/XOR protection for rendered tokens while accepting the plain cookie
   value via the header. A `CsrfCookieFilter` resolves the deferred token on
   every request so the cookie is (re)issued — **fresh token after login and
   after logout** (logout also deletes it; the next response re-issues).
   `POST /logout` itself is CSRF-protected. CSRF rejections return 403
   `MSG-AUTH-CSRF-REJECTED` (vs `MSG-AUTH-FORBIDDEN` for role denials) so the
   frontend can re-fetch the token and retry.
3. **No token ever reaches the browser** — not in cookies, response bodies,
   redirect URLs or Web Storage. There is nothing for Angular to put in
   localStorage/sessionStorage; the E2E suite asserts no JWT-shaped value in
   browser-visible cookies or the session probe body.
4. **CORS: prefer same-origin.** The recommended Angular dev setup proxies
   `/api`, `/oauth2`, `/login`, `/logout` from the dev server to the gateway —
   zero CORS, first-party cookies. If direct cross-origin calls are ever chosen
   instead, the gateway gets an EXPLICIT allowlist policy (exact origin,
   `allow-credentials: true`, enumerated methods/headers) — never a wildcard
   origin combined with credentials.
5. **Cookie handling exists only at the gateway.** Domain services are
   cookie-blind (ADR-009); the `RemoveRequestHeader=Cookie` route filter enforces
   it at the edge.

## Consequences
- Browser-visible cookies are limited to `JSESSIONID` + `XSRF-TOKEN`.
- Angular integration needs no CSRF code beyond Angular's defaults, but must
  call `GET /api/session/me` after login/logout transitions (see
  `docs/api/authentication.md`).
- Back-button after logout: protected responses carry Spring Security's
  default no-store cache headers, and any API call with the dead session gets
  401, which the frontend maps to the login screen (AC-AUTH-02-02).
