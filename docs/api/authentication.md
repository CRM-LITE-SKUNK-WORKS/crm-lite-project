# Authentication API — Browser/Angular Contract (Gateway BFF)

Last updated: 2026-07-18. Architecture: ADR-006..011. The gateway
(`http://localhost:8080`) is the only browser-facing origin; Keycloak
(`http://localhost:8180`, realm `crm-lite`) hosts the login page.

> **No Angular application exists in the repository yet.** This document is the
> contract that the future Angular shell codes against; everything below is
> implemented and verified server-side today (gateway E2E suite
> `GatewayBffIntegrationTest`).

To verify this contract by hand — browser walkthrough, Keycloak console checks,
Postman setup and the known traps — follow
[docs/runbooks/auth-testing.md](../runbooks/auth-testing.md).

## The rules the browser can rely on

- **No access or refresh token is ever exposed to the browser** — not in
  cookies, response bodies, redirect URLs or headers. There is nothing to put
  in localStorage/sessionStorage, and nothing that may be put there.
- Browser-visible cookies are exactly: `JSESSIONID` (HttpOnly, SameSite=Lax,
  Secure outside local dev) and `XSRF-TOKEN` (readable, for CSRF echo).
- All business APIs (`/api/customers/**`, `/api/cities/**`, `/api/lookups/**`)
  require an authenticated session **with the `crm-user` role**.

## Login

1. Angular navigates (full page redirect, not XHR) to
   **`GET /oauth2/authorization/keycloak`**.
2. The gateway redirects to Keycloak's login page (Authorization Code + PKCE;
   credentials are typed ONLY there).
3. Keycloak redirects back to **`GET /login/oauth2/code/keycloak`** (owned by
   the gateway — Angular never handles the callback or the code).
4. The gateway establishes the session and redirects to the originally
   requested URL (or `/`).

Invalid credentials and disabled users stay on the Keycloak page with its
error message (MSG-AUTH-INVALID-CRED semantics, AC-AUTH-01-03..05); no
application session is created.

## Session probe

**`GET /api/session/me`** → `200`:

```json
{"authenticated": true, "username": "ayilmaz", "subject": "<keycloak-sub-uuid>", "roles": ["crm-user", "..."]}
```

- Call it after startup and after every login/logout transition: it reports the
  principal AND (re)issues the `XSRF-TOKEN` cookie.
- Anonymous/expired session → `401` JSON (below). That 401 is Angular's signal
  to start the login redirect.

## CSRF (unsafe methods: POST/PUT/PATCH/DELETE)

- Read the `XSRF-TOKEN` cookie and echo it as the `X-XSRF-TOKEN` header —
  Angular `HttpClient` does this automatically for same-origin requests.
- Missing/wrong header → `403` with `"messageKey": "MSG-AUTH-CSRF-REJECTED"`
  (distinct from the role denial `MSG-AUTH-FORBIDDEN`): re-fetch
  `/api/session/me`, then retry once.
- Tokens rotate with the session: fresh after login and after logout.

## Logout

**`POST /logout`** with the `X-XSRF-TOKEN` header (no body). The gateway
invalidates the session, deletes cookies, and 302-redirects through Keycloak's
`end_session` endpoint (RP-initiated logout — the SSO session dies too), then
back to the application. Follow the redirects with a full-page navigation.

After logout (or the browser back button): any API call returns `401`
(AC-AUTH-02-02) — navigate to login.

## Error contract

| Case | Status | Body |
|---|---|---|
| No/expired session on `/api/**` | 401 | `{timestamp, status, error, messageKey: "MSG-AUTH-UNAUTHORIZED", message, path}` |
| Session lacks `crm-user` | 403 | same shape, `MSG-AUTH-FORBIDDEN` |
| CSRF rejection | 403 | same shape, `MSG-AUTH-CSRF-REJECTED` |
| Browser page navigation while anonymous | 302 | redirect to `/oauth2/authorization/keycloak` |

API calls are distinguished from page navigation by the path (`/api/**` always
gets JSON, never a redirect).

## Session lifetime (KR-9)

- Access token ~5 min — invisible to the browser; the gateway refreshes it
  transparently.
- Idle timeout 30 min (gateway session AND Keycloak SSO idle).
- Absolute cap 24 h (Keycloak SSO max) — after that, refresh fails and the next
  API call returns 401 → re-login.

All three are environment-configurable; see `config-repo/api-gateway.yml` and
the realm import.

## Development setup recommendation

Run the Angular dev server behind a proxy (`proxy.conf`) forwarding `/api`,
`/oauth2`, `/login`, `/logout` to `http://localhost:8080` — same-origin
cookies, zero CORS configuration (ADR-008 §4).
