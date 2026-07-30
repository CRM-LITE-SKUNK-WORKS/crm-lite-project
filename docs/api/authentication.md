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
- All business APIs (`/api/customers/**`, `/api/cities/**`, `/api/lookups/**`,
  `/api/accounts/**`) require an authenticated session **with the `crm-user`
  role**.

## Login

1. Angular navigates (full page redirect, not XHR) to
   **`GET /oauth2/authorization/keycloak`**.
2. The gateway redirects to Keycloak's login page (Authorization Code + PKCE;
   credentials are typed ONLY there).
3. Keycloak redirects back to **`GET /login/oauth2/code/keycloak`** (owned by
   the gateway — Angular never handles the callback or the code).
4. The gateway establishes the session and redirects to the **application root as
   derived from the request** — `http://localhost:4200/` for a login arriving
   through the frontend origin, `http://localhost:8080/` for a direct hit
   (forward-header-corrected, the same derivation `{baseUrl}` and the post-logout
   URI use). Angular's `'' → customers` route then decides the actual screen; the
   gateway never hardcodes the frontend origin.
   The landing is **always** that root: the gateway keeps **no** saved-request
   cache (2026-07-30 fix), so nothing a dead session left behind can hijack the
   next login. Before it, a stale raw API tab — or Angular's own anonymous
   `/api/session/me` probe, or even the browser's `favicon.ico` sub-request —
   could become the landing page and dump the user on JSON at `…?continue`
   (`continue` being Spring Security's saved-request marker). Both origins share
   one session, since nginx proxies `/api`, `/oauth2`, `/login` and `/logout` to
   the gateway. Deep links are the SPA's own concern: nginx serves `index.html`
   for any path and Angular's router owns the URL, so the gateway never sees them.

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

**`POST /logout`** with the `X-XSRF-TOKEN` header (no body) → `200`:

```json
{"logoutUrl": "http://localhost:8180/realms/crm-lite/protocol/openid-connect/logout?id_token_hint=...&post_logout_redirect_uri=..."}
```

The gateway invalidates the session and deletes cookies synchronously, then
returns Keycloak's `end_session` endpoint (RP-initiated logout) as `logoutUrl`
instead of redirecting there itself — an XHR/`fetch` cannot reliably drive a
cross-origin redirect chain (it can stall without completing or erroring), so
Keycloak's own SSO cookie would never actually clear. The caller must perform
a **real top-level navigation** to `logoutUrl` (`window.location.replace` —
`replace`, not `assign`, so the signed-in screen it leaves is not left in the
history to be restored from the browser's back/forward cache), which lets the
browser actually visit Keycloak and clear its SSO session.

`post_logout_redirect_uri` is the gateway's own
`/oauth2/authorization/keycloak`, not an application page: Keycloak clears the
SSO cookie, redirects there, and that endpoint starts a fresh authorization
request which — with the SSO session now gone — renders the sign-in form. The
user therefore lands **directly on the login page**; there is no intermediate
"you have been signed out" screen, because the sign-out was already confirmed
in the UI before the POST was sent. No loop is possible: the session that could
have completed that authorization request silently is the one just destroyed.

After logout (or the browser back button): any API call returns `401`
(AC-AUTH-02-02) — navigate to login.

## Error contract

| Case | Status | Body |
|---|---|---|
| No/expired session on `/api/**` | 401 | `{timestamp, status, error, messageKey: "MSG-AUTH-UNAUTHORIZED", message, path}` |
| Session lacks `crm-user` | 403 | same shape, `MSG-AUTH-FORBIDDEN` |
| CSRF rejection | 403 | same shape, `MSG-AUTH-CSRF-REJECTED` |
| Browser page navigation while anonymous | 302 | redirect to `/oauth2/authorization/keycloak` |
| Path the gateway neither routes nor maps (e.g. `/favicon.ico`) | 404 | same shape, `MSG-NOT-FOUND` — a **documented project addition** (2026-07-30). It previously fell into the catch-all as `500 MSG-INTERNAL-ERROR`, which misreported a missing endpoint as a server fault |

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
