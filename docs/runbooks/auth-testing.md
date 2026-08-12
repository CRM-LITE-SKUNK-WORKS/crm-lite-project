# Runbook — Authentication Testing (local browser + Postman)

Last updated: 2026-07-20. Audience: developers and analysts who need to **verify**
the authentication/security behaviour by hand, without reading the code.

- What the system *promises* to the browser: [docs/api/authentication.md](../api/authentication.md)
- Why it is built this way: ADR-006..011 in [docs/architecture/adr/](../architecture/adr/)
- How to start the stack in general: [local-development.md](local-development.md)

This runbook is the *verification* companion to those: a scripted walkthrough
that ends with a pass/fail answer for every security guarantee we claim.

## 0. Ground rules (non-negotiable)

- Credentials are typed **only** on the Keycloak login page. No application
  login form exists and none may be added (ADR-006).
- **Never** use Direct Access Grant / ROPC / password grant to obtain a token —
  not even "just for a quick test". It is disabled on the `crm-bff` client by
  design; a test that needs it is testing the wrong thing.
- Never store real passwords, access tokens, refresh tokens or client secrets in
  the Postman collection/environment, in scripts, or in tickets.
- `ayilmaz` / `edemir` / `mkaya` with password `crm-dev` are **local-only dev
  fixtures** shipped in the realm import. They do not exist anywhere else.

## 1. Prerequisites

| Need | Check | Expected |
|---|---|---|
| Java 25 | `java -version` | `25.x` |
| Maven | `mvn -version` | 3.9.x — if `command not found`, see below |
| Podman | `podman --version` | 5.x |
| Podman machine | `podman machine list` | `LAST UP = Currently running` |

If `mvn` is not on PATH (common on IntelliJ-only workstations), use the bundled
binary and define it **once per terminal**:

```bash
MVN="/c/Users/<you>/AppData/Local/Programs/IntelliJ IDEA <version>/plugins/maven/lib/maven3/bin/mvn"
"$MVN" -version
```

All commands below assume Git Bash and the repository root as working directory
(`pwd` → `/c/.../crm-lite-project`).

## 2. Start the stack

### 2.1 Infrastructure

```bash
podman compose -p crm-lite -f infra/docker-compose.yml up -d postgres keycloak keycloak-init
podman ps
```

`-p crm-lite` is **mandatory**: without it the project name is derived from the
`infra/` directory and can attach another project's volumes. `postgres` and
`keycloak` must reach `Up ... (healthy)` before continuing; `keycloak-init` is a
one-shot that exits `0` and is expected to be gone from `podman ps`. Keycloak
imports the realm from `infra/keycloak/realm/crm-lite-realm.json` on first start.

**Name `keycloak-init` explicitly** — this is easy to get wrong and fails
silently. Nothing `depends_on` it (deliberately: a failure there must not stop
the stack), so `up -d postgres keycloak` does **not** start it. A bare
`up -d` (no service names) does, which is why the container's own comment says
"every up".

> **Do not reach for a bare `up -d` just to get `keycloak-init`.** It also starts
> the **`api-gateway` container, which publishes host port 8080** — and
> `GatewayBffIntegrationTest` binds that exact port (the committed realm's
> redirect URIs point there). `mvn verify` then fails with
> `PortInUseException: Port 8080 is already in use`, in every test of the class,
> behind a wall of `ApplicationContext failure threshold (1) exceeded` warnings
> that say nothing about the real cause. Either name the three services as above,
> or `podman stop api-gateway` before running the build.

That matters because `--import-realm` runs **only when the realm is absent**: on
an existing `keycloak_db`, every realm change — the login theme, the `:4200`
redirect URIs, and since 2026-08-12 the header's `titleCode` attribute and its
ID-token mapper — arrives *only* through `keycloak-init`. Confirm it landed:

```bash
podman logs keycloak-init | tail -1
# keycloak-init: titleCode attribute + mapper applied
```

No database reset and no Admin Console clicking. If that line is missing, the
stack still works — the affected settings just silently keep their old values
(e.g. the header shows the username with no job title).

**Already signed in? Log out and back in.** These fields ride in the **ID
token**, which is minted once at login and not re-issued by the transparent
access-token refresh, so a session that predates the mapper keeps answering
`"titleCode": null` until a fresh authentication.

Never run `podman compose down -v` — it destroys the databases.

### 2.2 Services

One terminal per service, in this order (each blocks its terminal; wait for
`Started ...Application` before starting the next):

| # | Service | Command | Port |
|---|---|---|---|
| 1 | config-server | `"$MVN" -pl backend/config-server spring-boot:run` | 8888 |
| 2 | discovery-server | `"$MVN" -pl backend/discovery-server spring-boot:run` | 8761 |
| 3 | lookup-service | `"$MVN" -pl backend/lookup-service spring-boot:run` | 8083 |
| 4 | mernis-stub | `"$MVN" -pl backend/mernis-stub spring-boot:run` | 8084 |
| 5 | customer-service | `"$MVN" -pl backend/customer-service spring-boot:run` | 8082 |
| 6 | api-gateway | `"$MVN" -pl backend/api-gateway spring-boot:run` | 8080 |

Running each service in its own terminal is not cosmetic: it is the only place
you see that service's log when something fails (§7).

### 2.3 Health gate

```bash
curl -s -m 3 -w '  <- %{url_effective}\n' \
  http://localhost:8888/actuator/health http://localhost:8761/actuator/health \
  http://localhost:8082/actuator/health http://localhost:8083/actuator/health \
  http://localhost:8084/actuator/health http://localhost:8080/actuator/health
```

Six `"status":"UP"` lines. An empty line = that service is not running; a `DOWN`
= it is up but a dependency (usually the database) is not.

## 3. Verify the Keycloak configuration

Admin Console: <http://localhost:8180> → **Administration Console** → `admin` /
`admin` (disposable local bootstrap credentials from `infra/docker-compose.yml`).
Switch realm (top-left selector) to **crm-lite** before anything else.

| # | Where | Expected | Why it matters |
|---|---|---|---|
| 3.1 | Clients → `crm-bff` → Settings | Client authentication **Off** (public client) | No secret to leak; PKCE carries the security (ADR-006) |
| 3.2 | same → Capability config | Standard flow **on**; Direct access grants **off**; Implicit **off**; Service accounts **off** | Only Authorization Code is possible |
| 3.3 | same | PKCE Method **S256** | Authorization-code interception is prevented |
| 3.4 | same → Access settings | Valid redirect URI `http://localhost:8080/login/oauth2/code/keycloak` | The gateway owns the callback, not the SPA |
| 3.5 | Realm roles | `crm-user` exists | The single application role (ADR-009) |
| 3.6 | Users | `ayilmaz` enabled, `edemir` enabled, `mkaya` **disabled** | Fixtures for the positive and negative login tests |
| 3.7 | Realm settings → Tokens | Access Token Lifespan **5 minutes** | KR-9 |
| 3.8 | Realm settings → Sessions | SSO Session Idle **30 minutes**, SSO Session Max **24 hours** | KR-9 |
| 3.9 | Realm settings → Localization | Internationalization enabled; locales `en` + `tr`; default `en` | AC-AUTH language requirement (standard Keycloak i18n; **no custom project theme exists**) |

Scripted alternative (same facts, no clicking) — note `MSYS_NO_PATHCONV=1`,
without it Git Bash mangles the container-side `/opt/...` path:

```bash
MSYS_NO_PATHCONV=1 podman exec keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8180 --realm master --user admin --password admin
MSYS_NO_PATHCONV=1 podman exec keycloak /opt/keycloak/bin/kcadm.sh get clients \
  -r crm-lite --query clientId=crm-bff
```

## 4. Browser test matrix

Use a **fresh incognito/private window** (`Ctrl+Shift+N`). Closing that window
is the reliable way to drop *both* the gateway session and the Keycloak SSO
cookie between test runs.

| # | Action | Expected result |
|---|---|---|
| 4.1 | Open `http://localhost:8080/api/session/me` while anonymous | `401` JSON with `"messageKey": "MSG-AUTH-UNAUTHORIZED"` |
| 4.2 | Open `http://localhost:8080/oauth2/authorization/keycloak` | Redirect to Keycloak (`localhost:8180`) login page. The authorize URL contains `code_challenge_method=S256` |
| 4.3 | Sign in as `ayilmaz` / `crm-dev` | Redirect back to the gateway; session JSON with `"authenticated": true`, `"username": "ayilmaz"`, a `subject` UUID, `"roles": ["crm-user"]`, plus `"fullName": "Ali Yilmaz"` and `"titleCode": "SALES_REP"` — a CODE, which the SPA localizes to "Sales Representative" / "Satış Temsilcisi" (both `null` if `keycloak-init` has not run against this database — see §2.1) |
| 4.4 | `GET /api/session/me` | Same payload. **No token of any kind in the body** |
| 4.5 | DevTools (`F12`) → Application → Cookies → `http://localhost:8080` | Exactly two cookies: `JSESSIONID` (**HttpOnly ✓**, SameSite=Lax) and `XSRF-TOKEN` (readable by design). Neither value starts with `eyJ` |
| 4.6 | Application → Local Storage and Session Storage | **Both empty** — nothing to steal via XSS |
| 4.7 | Console: `fetch('/api/customers/1001',{method:'DELETE'}).then(r=>console.log(r.status))` | `403` — CSRF rejected (unsafe method without the header) |
| 4.8 | Repeat 4.7 with header `X-XSRF-TOKEN` set to the `XSRF-TOKEN` cookie value | Passes CSRF; the business outcome (`204`/`404`) then depends on the data |
| 4.9 | Logout — see §4.1 below | Session invalidated |
| 4.10 | After logout, `GET /api/session/me` | `401 MSG-AUTH-UNAUTHORIZED` (also on browser Back) |
| 4.11 | New incognito window → login as `mkaya` / `crm-dev` | Rejected on the Keycloak page ("Invalid username or password" — Keycloak deliberately does not reveal that the account is disabled). No application session |

Chrome/Edge blocks the first paste into the Console until you type
`allow pasting` and press Enter. That is a browser safety feature, not an
application problem.

### 4.1 How to log out correctly

`POST /logout` is CSRF-protected, so typing `/logout` in the address bar does
nothing (correctly). Two ways to trigger it:

**What the application does (and what the Angular shell does):** the sidenav's
sign-out button first opens a Yes/No confirmation dialog — nothing is sent
until the user answers Yes. On confirmation: an XHR POST carrying the CSRF
token, whose `200` JSON response is `{"logoutUrl": "..."}` — Keycloak's
`end_session` endpoint. The app then does `window.location.replace(logoutUrl)`,
a **real top-level navigation**, which is what actually ends the **Keycloak
SSO** session (an XHR cannot reliably drive that cross-origin redirect chain
itself). `replace` rather than `assign`, so the signed-in screen is not left in
the history for the Back button to restore from the bfcache. Keycloak then
sends the browser to `/oauth2/authorization/keycloak`, which starts a fresh
authorization request and — the SSO session now gone — lands **directly on the
sign-in form**; there is no intermediate application page. Angular's
`HttpClient` attaches `X-XSRF-TOKEN` automatically for same-origin requests;
for a plain HTML form the server-rendered `_csrf` value must be used (not the
raw cookie — see Trap 1).

**What you can do from the DevTools console — header-based `fetch`, then a
manual navigation to complete SSO logout too:**

```javascript
fetch('/logout', {method:'POST', headers:{'X-XSRF-TOKEN':
  document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).split('=')[1]}})
  .then(r=>r.json())
  .then(({logoutUrl})=>{console.log('navigating to', logoutUrl); window.location.replace(logoutUrl);});
```

Skipping the final `window.location.replace` (just running the `fetch`, as in
older versions of this snippet) ends only the application session — see Trap 2.

> **Trap 1 — the `_csrf` form parameter does not accept the raw cookie value.**
> The gateway uses the SPA CSRF handler: a token arriving in the **`X-XSRF-TOKEN`
> header** is read as-is, while a token arriving in the **`_csrf` request
> parameter** is expected to be the XOR-masked render value. Posting the raw
> cookie value as `_csrf` therefore fails with
> `403 MSG-AUTH-CSRF-REJECTED — Invalid CSRF Token 'null'`. Always use the header.

> **Trap 2 — a bare `fetch` logout ends the application session but not the
> Keycloak SSO session.** The gateway's `200` JSON response carries `logoutUrl`
> but doesn't navigate anywhere by itself — nothing about a plain `fetch()`
> call visits Keycloak. Symptom: `/api/session/me` returns 401 (local logout
> worked), yet re-entering `/oauth2/authorization/keycloak` logs you straight
> back in **without a password prompt**. Fix: follow up with
> `window.location.replace(logoutUrl)` as shown above (this is exactly what the
> real app does). To force a clean slate for testing without that, close the
> incognito window.

> **Note — the `id_token_hint` in the logout redirect.** That redirect URL
> contains an **ID token** (`eyJ...`). This does not violate "no tokens in the
> browser": it is the OIDC-standard RP-initiated logout parameter, it is not an
> access or refresh token, it is never stored, and it cannot call the CRM APIs
> (its audience is `crm-bff`; the resource servers require `crm-api`).

### 4.2 Role enforcement (no-role user)

`crm-user` is what separates "logged in" from "allowed". To prove it, create a
temporary user **without** the role via the Admin Console (Users → Add user;
give it email/first/last name, otherwise Keycloak forces an update-profile
action, and set a password on the Credentials tab), then:

| Action | Expected |
|---|---|
| Log in as the temp user | Succeeds — a session exists |
| `GET /api/session/me` | `200`, `roles` **without** `crm-user` |
| `GET /api/customers` | `403` with `"messageKey": "MSG-AUTH-FORBIDDEN"` |

Delete the temp user afterwards. **Do not** add it to the committed realm
export — the export is a fixture, not a scratchpad.

## 5. Zero-trust check on the domain services

The domain services do not trust the gateway; they validate the JWT themselves
(signature, issuer, audience `crm-api`, role `crm-user` — ADR-009). With the
services running on the host:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8082/api/customers
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8083/api/lookups
curl -s -o /dev/null -w '%{http_code}\n' -H 'Authorization: Bearer not-a-real-token' \
  http://localhost:8082/api/customers
```

All three must print `401`. The bodies carry the same
`MSG-AUTH-UNAUTHORIZED` contract as the gateway. In the compose profile these
ports are not published to the host at all — do **not** publish them merely to
run this check in an environment where they are closed.

`mernis-stub` (8084) has no gateway route and receives **no** token by design
(ADR-010): it is an external-simulator boundary, not a CRM resource server.

## 6. Postman

The collection lives in [docs/postman/](../postman/); its README covers folder
ordering and dynamic variables. This section covers only authentication.

### 6.1 Why Postman needs a manual step

The gateway is a **cookie-session BFF**. It does not accept
`Authorization: Bearer`, and Postman cannot run Authorization Code + PKCE
against a public client in a way that lands the resulting session in the
gateway's cookie jar. Since Direct Grant is (correctly) disabled, there is no
programmatic shortcut. So: authenticate in a **browser**, then hand the two
cookies to Postman.

### 6.2 Setup

1. **Import** `CRM-Lite.postman_collection.json` and
   `CRM-Lite.local.postman_environment.json` — drag the files into the Import
   dialog or pick them with the file chooser. Pasting a file *path* into the raw
   text box produces `Incorrect format, please input the right format to import`.
2. Select the environment **CRM Lite — Local** (top-right selector). If you skip
   this, every request fails with
   `getaddrinfo ENOTFOUND {{gatewaybaseurl}}` — the variable never resolved.
3. In a browser, log in (§4.2–4.3) and copy the `JSESSIONID` and `XSRF-TOKEN`
   values from DevTools → Application → Cookies → `http://localhost:8080`.
4. Postman → open any request → **Cookies** (under the Send button) →
   **Add domain** `localhost` → add two cookies, pasting **only the value** —
   no angle brackets, no quotes:

   ```text
   JSESSIONID=<paste-value-here>; Path=/
   XSRF-TOKEN=<paste-value-here>; Path=/
   ```

   The `<...>` above is a placeholder. Literally including `<` and `>` yields a
   session id the gateway cannot match, and every request returns `401`.
5. Environments → **CRM Lite — Local** → set `xsrfToken` (**CURRENT VALUE**
   column) to the `XSRF-TOKEN` value → **Save**. Write requests send it as the
   `X-XSRF-TOKEN` header; without it they fail with `403 MSG-AUTH-CSRF-REJECTED`.

### 6.3 Verification inside Postman

| Request | Expected |
|---|---|
| **09 - Authentication** → *Session probe* (before adding cookies) | `401 MSG-AUTH-UNAUTHORIZED` |
| *Session probe* (after adding cookies) | `200` with `"username": "ayilmaz"` |
| **00 - Health** folder | all `UP` |
| Any read folder (01/02/03) | `200` |
| A create/update/delete request with `xsrfToken` **unset** | `403 MSG-AUTH-CSRF-REJECTED` |
| The same request with `xsrfToken` set | proceeds to the business outcome (`201`/`200`/`204`) |
| **09** → *Start login (browser flow)* | assertion that the redirect carries `code_challenge_method=S256` |

Sessions idle out after 30 minutes: when previously-working requests start
returning `401`, re-do steps 3–5 with a fresh browser login. Nothing is broken.

Before running a customer **create**, set `nationalityId` to a fresh, unused
11-digit value — used ids are reserved permanently, even after a soft delete
(ADR-003).

## 7. Troubleshooting

Diagnose in this order: *which command failed* → *which service* → *that
service's log*.

| Symptom | Cause | Fix |
|---|---|---|
| `Port 8080/8888/... was already in use` | An earlier run is still alive | `netstat -ano \| grep ':8888' \| grep LISTENING` → `tasklist \| grep <pid>`. If it is the same service, reuse it; otherwise stop it deliberately (`taskkill //F //PID <pid>` — the double slash prevents Git Bash path mangling) |
| Keycloak container restarts / DB auth errors | The postgres volume predates `keycloak_db`; init scripts only run on an empty volume | `podman exec postgres psql -U crmlite -d crm_admin -c 'CREATE DATABASE keycloak_db;'` then restart the Keycloak container. Verify with `... -c "SELECT datname FROM pg_database WHERE datname='keycloak_db';"` |
| Postgres starts with the wrong data / `role "crmlite" does not exist` | Compose ran without `-p crm-lite` and attached a foreign project's volume | Stop (**without** `-v`) and start again with `-p crm-lite` |
| `401` on every gateway request | No session, expired session (30 min idle), or malformed cookie in Postman | Re-login in the browser and refresh the Postman cookies |
| `403 MSG-AUTH-CSRF-REJECTED` | Missing/incorrect CSRF echo, or the token was sent as the `_csrf` parameter instead of the header | Send `X-XSRF-TOKEN`; re-fetch `/api/session/me` to get a fresh token, retry once |
| `403 MSG-AUTH-FORBIDDEN` | The session is valid but lacks `crm-user` | Assign the realm role in Keycloak |
| Logged back in without a password prompt | Keycloak SSO session still alive (see Trap 2) | Close the incognito window, or perform a full-page logout |
| `401` from a direct service call | Working as designed (ADR-009) | Route through the gateway |
| curl/JDK scripted login loses the Keycloak session | Keycloak marks its auth cookies `Secure` even over plain http; browsers exempt `localhost`, other clients do not | Strip the `Secure` flag in the client (see `GatewayBffIntegrationTest.Browser`), or test in a real browser |
| kcadm: `/opt/keycloak/...: No such file` | Git Bash rewrote the container path | Prefix the command with `MSYS_NO_PATHCONV=1` |

Where to read logs: each service prints to the terminal that started it. If a
service was started outside your terminals (a leftover background process), you
cannot see its log — stop it and restart it in a terminal you own.

## 8. Automated coverage (what you do not have to test by hand)

`mvn clean verify` from the repository root runs all of it (92 tests as of the
auth milestone; integration tests need Docker/Podman):

| Suite | Covers |
|---|---|
| `crm-security-starter` (16) | Audience/issuer validation, role converter, auditor, 401/403 body contract, bearer propagation |
| `GatewayBffIntegrationTest` (7) | Full Authorization Code + PKCE login against a real Keycloak container, token relay with cookie stripping, transparent access-token refresh, CSRF, anonymous handling, disabled user, logout |
| `customer-service` (62) | Includes token-less rejection, role-less rejection, malformed token, and audit columns carrying the Keycloak `sub` |
| `lookup-service` (4) | Direct access requires the role |

Use this runbook for the parts a test suite cannot assert on your behalf:
Keycloak console configuration, real browser cookie/storage inspection, and
Postman ergonomics.
