# FE-ADR-004: Same-Origin Access via Relative Paths; CORS Deliberately Rejected

## Status
Accepted (2026-07-23) — implements the deployment recommendation of backend
**ADR-008 §4** and `docs/api/authentication.md` §Development setup.

## Context
Backend **ADR-007** makes `api-gateway` (`:8080`) the only browser-facing origin
and a full BFF: the browser holds an HttpOnly `JSESSIONID` and a readable
`XSRF-TOKEN` cookie, and nothing else. **ADR-008 §2** issues CSRF tokens through
a cookie that the client must echo as the `X-XSRF-TOKEN` header, chosen
explicitly because it is *"exactly Angular HttpClient's built-in convention"*.

That built-in convention has a hard precondition: **Angular's `HttpClient` only
attaches `X-XSRF-TOKEN` to same-origin requests.** Cookies carry the same
constraint from the other side — the session cookie is `SameSite=Lax`, which is
not sent on cross-site XHR.

ADR-008 §4 states the recommended setup outright: proxy `/api`, `/oauth2`,
`/login`, `/logout` from the dev server to the gateway — *"zero CORS,
first-party cookies"* — and adds that if cross-origin is ever chosen instead,
the gateway gets an explicit allowlist, *"never a wildcard origin combined with
credentials."*

## Decision

### 1. All HTTP calls use relative paths
Every request is issued against a **relative** path:

```
GET  /api/customers?page=0&size=20
GET  /api/customers/1001/addresses
GET  /api/session/me
POST /logout
```

No absolute URL, no scheme, no host, no port appears in application code.

### 2. There is no API base URL in `environment.*.ts`
**A base-url constant is not introduced at all** — not as an empty string, not
as a token, not "for flexibility later". The environment files carry no API
host, and no interceptor prefixes requests with one.

**Why this is a hard rule and not a preference:** the moment a base URL exists,
someone sets it to `http://localhost:8080` to make `ng serve` work without a
proxy. That single value silently converts every request from same-origin to
cross-origin, which (a) stops `HttpClient` from attaching `X-XSRF-TOKEN`, so
every mutating request fails with `403 MSG-AUTH-CSRF-REJECTED`, and (b) stops
the `SameSite=Lax` session cookie from being sent, so every request looks
anonymous and returns `401`. The failure is confusing precisely because the
"fix" looks reasonable. Removing the concept removes the failure mode.

### 3. Both environments proxy the same four path prefixes
The identical set is proxied in development and in the container, so the
application cannot behave differently between them:

| Prefix | Purpose |
|---|---|
| `/api` | All business APIs + `/api/session/me` |
| `/oauth2` | Login initiation (`/oauth2/authorization/keycloak`) |
| `/login` | OAuth2 callback (`/login/oauth2/code/keycloak`) — gateway-owned |
| `/logout` | RP-initiated logout |

- **Development:** `ng serve` with `proxy.conf.json` → `http://localhost:8080`.
- **Container:** nginx `proxy_pass` → `http://api-gateway:8080` on the compose
  network (FE-ADR-010).

Everything else is served by the frontend itself, with SPA fallback to
`index.html`.

### 4. CORS is deliberately rejected
The gateway is **not** given a CORS policy for the frontend, and the frontend is
**not** served from a different origin than the one it calls.

**Why not CORS:** it is not merely extra configuration, it actively fights the
authentication design. `allow-credentials: true` with an exact-origin allowlist
is the only safe shape, preflight `OPTIONS` requests are added to every mutating
call, `SameSite=Lax` still blocks the session cookie on cross-site XHR, and
`HttpClient` still refuses to attach the CSRF header. One would then be tempted
to loosen `SameSite` to `None` — which ADR-008 §1 chose `Lax` deliberately to
avoid. Same-origin makes the entire class of problems not exist.

### 5. `withXsrfConfiguration` is not customized
Angular's default cookie/header names are `XSRF-TOKEN` / `X-XSRF-TOKEN` —
exactly what ADR-008 §2 configured server-side. The defaults are left alone; no
custom names, no manual header writing.

## Consequences
- **Zero CSRF code in the frontend.** ADR-008's consequence section already
  predicted this: *"Angular integration needs no CSRF code beyond Angular's
  defaults."*
- The dev proxy config becomes load-bearing infrastructure. If `ng serve` is
  started without it, everything 401s. This belongs in the frontend README.
- Switching the gateway's host/port requires editing exactly two files
  (`proxy.conf.json`, `nginx.conf`) and zero lines of TypeScript.
- Browser devtools show requests to the frontend's own origin, not to `:8080` —
  expected, and worth stating so it is not mistaken for a bug.

---

## Addendum (2026-07-23): the OAuth redirect origin — RESOLVED

### The problem
The frontend is published on `:4200` and the gateway on `:8080`. For the browser
to stay on **one** origin, the whole OAuth2 redirect chain must also stay on
`:4200`. Two things blocked that:

1. The realm registered exactly one redirect URI —
   `http://localhost:8080/login/oauth2/code/keycloak`, with
   `webOrigins: ["http://localhost:8080"]`.
2. The gateway derives its callback from the incoming request
   (`redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"`) but — unlike
   customer-service and lookup-service — did **not** set
   `server.forward-headers-strategy`, so it ignored `X-Forwarded-*` and could not
   know the browser-facing origin.

Left unfixed, login would fail with an `invalid_redirect_uri` error from
Keycloak.

### The decision — three coordinated, minimal changes
**1. `api-gateway.yml` gains `server.forward-headers-strategy: framework`.**
The gateway now builds `{baseUrl}` from the `X-Forwarded-*` headers nginx sets,
so `redirect_uri` becomes `http://localhost:4200/...` for requests arriving
through the frontend, and remains `http://localhost:8080/...` for direct hits
(Swagger UI, Postman, curl). Both work; neither is privileged.

**2. The `crm-bff` client registers both origins.**
```json
"redirectUris": ["http://localhost:8080/login/oauth2/code/keycloak",
                 "http://localhost:4200/login/oauth2/code/keycloak"],
"webOrigins":   ["http://localhost:8080", "http://localhost:4200"],
"post.logout.redirect.uris": "http://localhost:8080/*##http://localhost:4200/*"
```

**3. `keycloak-init` re-applies the client settings on every `up`.**
This is the load-bearing part for "clone and run". `--import-realm` imports
**only when the realm is absent** from `keycloak_db`, so editing the realm JSON
alone reaches a fresh clone but **not** an existing developer's database. The
repository already solved this exact problem once — `keycloak-init` exists
because the login theme had the same limitation (compose comment, PROJECTBRAIN
§4.7) — so the fix extends that established mechanism rather than inventing a
second one.

Verified on the running stack (2026-07-23): the `kcadm` sequence applies both
origins and **preserves** `pkce.code.challenge.method: S256` and
`directAccessGrantsEnabled: false`.

**4. nginx forwards the browser's real origin** (FE-ADR-010 §3):
```nginx
proxy_set_header Host              $host;
proxy_set_header X-Forwarded-Host  $host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Port  $server_port;
```
`$host` is passed through rather than hardcoded, so the same image works behind
any hostname.

### Why not the alternatives
- **Serve the frontend from the gateway origin (`:8080`).** The gateway would
  become a static-asset server, diluting the BFF's single responsibility, and
  `/` routing would have to coexist with the Swagger and actuator paths already
  mounted there. More invasive for no additional safety.
- **A single edge reverse proxy in front of everything.** Architecturally the
  most "correct" production shape, but it adds a tenth container and moves the
  entry point for Swagger, actuator, Postman and every existing runbook. A
  disproportionate change to solve a two-origin problem.

### Security note
Honouring `X-Forwarded-*` means a caller could in principle propose a different
origin. That is contained: Keycloak validates `redirect_uri` against the
registered list, so any value outside the two registered origins is rejected at
the authorization server. The registered list — not the header — is the trust
boundary, which is why it is kept to exact origins with no wildcard host.

### Environment portability
The nginx config is host-agnostic. The **only** environment-specific item is the
registered URI list, which is inherent to OAuth: a wildcard redirect list is an
open-redirect vulnerability, so each environment enumerates its origins
explicitly. For local development both origins ship in the realm and are
reconciled on every `up`.
