# FE-ADR-005: Authentication Integration — BFF Cookie Session; No Login Form in Angular

## Status
Accepted (2026-07-23) — implements the browser contract in
`docs/api/authentication.md`, governed by backend **ADR-006, ADR-007, ADR-008,
ADR-009**.

## Context
The authentication milestone (2026-07-17) made `api-gateway` a full BFF:
Authorization Code + PKCE against Keycloak, tokens held **server-side only** in
a session-bound `OAuth2AuthorizedClientService`, and a browser that receives
nothing but `JSESSIONID` (HttpOnly) and `XSRF-TOKEN` (readable).

The analyst source document (FR-AUTH-01) predates this architecture and still
describes an in-application login form. That conflict is already recorded on the
backend side as `document-delta.md` open conflict **#6** and traceability-matrix
conflict **#6**, both resolved in favour of ADR-006. The Keycloak project theme
that carries the analyst's "Login v2" design was completed and committed
(`infra/keycloak/themes/crm-lite/`, realm `"loginTheme": "crm-lite"`, applied on
every `up` by the `keycloak-init` container).

## Decision

### 1. Session state has exactly one source: `GET /api/session/me`
```json
{"authenticated": true, "username": "ayilmaz",
 "subject": "<keycloak-sub-uuid>", "roles": ["crm-user", "..."],
 "fullName": "Ali Yilmaz", "titleCode": "SALES_REP"}
```
`fullName` and `titleCode` were added on **2026-08-12** (see §Amendment) and are
**nullable**; everything else is unchanged.
It is called at application startup and after every login/logout transition. It
serves two purposes at once — it reports the principal **and** causes the
gateway to (re)issue the `XSRF-TOKEN` cookie.

The frontend never infers "logged in" from any other signal: not from the
presence of a cookie (the session cookie is HttpOnly and unreadable), not from a
cached flag, not from a successful unrelated request.

### 2. Login is a full-page redirect the frontend merely triggers
```
window.location.replace('/oauth2/authorization/keycloak')
```
It is **not** an `HttpClient` call and **not** an XHR. `replace` rather than
`assign`: the entry being left is one the guard has just refused (or on which a
request has just 401'd), so keeping it in the history would only give the Back
button a screen that immediately redirects again. The gateway redirects to
Keycloak, Keycloak authenticates, Keycloak redirects to
`/login/oauth2/code/keycloak` — **a gateway-owned path**. Angular never sees the
authorization code, never handles the callback, and has no route for it.

### 3. Logout is confirmed, then a CSRF-protected POST and a client-driven redirect
The sidenav's sign-out button only **asks**: it opens the shared
`ConfirmDialog` (`MSG-AUTH-LOGOUT-CONFIRM`, Yes/No). Nothing is destroyed until
the user answers Yes — the request below is not sent before that.

On confirmation: `POST /logout` with the `X-XSRF-TOKEN` header and no body, as
an `HttpClient` call (an XHR — the only way to attach the header; a
navigational form-POST cannot carry a valid CSRF token here, see §5). The
gateway invalidates the session synchronously and responds `200` with
`{"logoutUrl": "..."}` — Keycloak's `end_session` endpoint. The frontend then
does `window.location.replace(logoutUrl)`: a real top-level navigation, because
an XHR cannot reliably drive that cross-origin redirect chain to actually clear
Keycloak's SSO cookie (it can stall, neither completing nor erroring).
`replace`, not `assign`, because the entry being left renders customer data —
pushed, it would stay in the history and come back from the browser's
back/forward cache on Back.

Keycloak then redirects to the gateway's `/oauth2/authorization/keycloak`,
which starts a fresh authorization request and — the SSO session now gone —
lands the user **directly on Keycloak's sign-in form**. There is deliberately
no application-side "you have been signed out" page: the user already
confirmed the intent, so an interstitial would add a click and nothing else.

If the POST never answers (a bounded 3 s timeout), the frontend reloads `/`
rather than claiming a sign-out it cannot confirm: the gateway session may
still be alive, and the reload shows whichever is true.

### 4. 401 / 403 are distinguished by `messageKey`, never by status alone
| Status | `messageKey` | Frontend behaviour |
|---|---|---|
| 401 | `MSG-AUTH-UNAUTHORIZED` | Start the login redirect (§2) |
| 403 | `MSG-AUTH-CSRF-REJECTED` | Re-fetch `/api/session/me`, retry the request **once**, then surface the error |
| 403 | `MSG-AUTH-FORBIDDEN` | Permission error; **no retry**, no redirect |

Treating every 403 as a CSRF problem would produce an infinite retry loop
against a genuine authorization denial. `docs/api/authentication.md` created two
distinct keys precisely so this distinction is possible.

### 5. CSRF handling is Angular's default and nothing else
See FE-ADR-004 §5. No manual cookie reading, no custom header names, no
interceptor that writes `X-XSRF-TOKEN`.

---

## 🚫 PROHIBITIONS — absolute, not negotiable

These are not style preferences. Violating any of them breaks a security
property the backend spent an entire milestone establishing.

### P1. No username/password form is written in Angular. Ever.
No login component, no login route, no credential input, no "remember me", no
password field of any kind. Credentials are typed **only** on the Keycloak login
page. ADR-006 §2: *"Credentials are entered ONLY on the Keycloak login page."*

### P2. ROPC / Direct Access Grant / password grant is forbidden
The frontend never sends credentials to any token endpoint. ADR-006 §3 disables
the grant on the client (`directAccessGrantsEnabled: false`) and states it
*"must never be enabled — not in the app, not in tests, not in Postman."* That
prohibition extends to the frontend without exception, including for automated
tests and local convenience scripts.

### P3. No token is ever stored, read, parsed or logged
There is no access token, refresh token or ID token in the browser to begin with
— ADR-007 §2 and ADR-008 §3 guarantee it, and `GatewayBffIntegrationTest`
asserts it. Therefore:
- **No** `localStorage` / `sessionStorage` token storage.
- **No** JWT decoding library, and no hand-rolled `atob(token.split('.')[1])`.
- **No** `Authorization: Bearer` header is ever set by frontend code. The
  gateway's `TokenRelay` filter attaches it downstream, after the browser is out
  of the picture.
- Roles come from `/api/session/me`, never from a decoded token.

### P4. The mock's Login v2 screen is NOT ported to Angular
`docs/frontend/mock-ui-analysis.md` §6.1 documents it as visual reference only.
It has already been implemented — as the Keycloak theme
`infra/keycloak/themes/crm-lite/login/`, which reproduces the mock layout, the
64-character caps, the disabled-until-filled submit button, the show/hide toggle
and the TR/EN switcher. Re-implementing it in Angular would create a second,
non-functional login surface.

### P5. `PasswordInput` is not implemented in `shared/ui/`
The mock's EDS `PasswordInput` component is used in exactly two places: the
login screen (P4 — Keycloak's now) and a Product Configuration characteristic
field (out of scope per FE-ADR-013). It therefore has **no in-scope consumer**
and is excluded from the component set in FE-ADR-011 §d. If FR-PROD is ever
implemented, that is when the component gets written — not before.

### P6. Route guards are a UX affordance, never a security boundary
A guard may redirect an unauthenticated user to login for a smoother experience,
but it protects nothing. Every endpoint independently enforces the `crm-user`
role at the gateway **and** at the resource server (ADR-009 §2). Frontend code
must never assume a guard has made an API call safe.

---

## Consequences
- The frontend has **no** authentication code in the traditional sense: no token
  refresh logic, no expiry timers, no credential handling. Access-token refresh
  is transparent and server-side (ADR-007 §3).
- Session expiry surfaces as an ordinary `401` on the next API call (idle 30 min
  / absolute 24 h, KR-9). The 401 handler in §4 is the entire expiry strategy.
- A gateway restart logs everyone out (ADR-007 accepted trade-off: in-memory
  sessions). The frontend treats this identically to any other 401 — no special
  case.
- **Deployment coupling:** because login relies on a redirect chain, the
  frontend's origin and the realm's registered redirect URI must agree.
  Resolved 2026-07-23 — see FE-ADR-004 §Addendum: both `:4200` and `:8080` are
  registered on `crm-bff` and re-applied on every `up` by `keycloak-init`.
- > ⚠️ **Superseded (2026-08-12) — see §Amendment.** The probe now also carries
  > `fullName` and `title`, and the header renders both. The bullet is kept as
  > accepted on 2026-07-23; its premise ("no backend source exists") is what
  > changed, not its reasoning.

  `/api/session/me` exposes only `username`, `subject` and `roles`. The mock
  header shows `"Mobility · Resp. Sales Rep."`, for which **no backend source
  exists**. Decided 2026-07-23: **that text is dropped**; the header shows the
  avatar plus the real `username` from the session probe. No Keycloak user
  attribute is added and no field is added to the probe — inventing a display
  value for data the system does not hold is exactly what FE-ADR-013 §a
  forbids. If organizational data is genuinely required later, ADR-011 §4
  already prescribes how it must be designed.

---

## Amendment (2026-08-12): the header shows the real name and title; the probe grows two fields

### Status
Accepted (2026-08-12, instructor-approved). Supersedes the last bullet of
§Consequences and extends the §1 payload. Everything else in this ADR — the
five prohibitions in particular — stands untouched: no token is involved, no
claim is decoded in the browser, and the two new fields arrive the same way
`username` and `roles` always have, through the session probe.

### Context
The 2026-07-23 decision dropped the mock's `"Mobility · Resp. Sales Rep."` line
on a premise that was true then: `/api/session/me` had nothing to render it
from, and FE-ADR-013 §a forbids inventing a display value. The premise was the
*absence of a source*, not a judgement that the line was undesirable — the same
shape as every other scope decision in this project, and the same way out of it:
give it a real source.

The source is now real. Keycloak already holds the fixtures' first and last
names, and a **`title` user attribute** is a native user-model field — not a
value the frontend makes up.

### 1. The probe carries `fullName` and `titleCode`
`SessionController.SessionResponse` gains both. `fullName` is
`OidcUser.getFullName()` (the standard `name` claim, already delivered by the
`profile` scope the gateway requests — no realm change was needed for it);
`titleCode` is `getClaimAsString("titleCode")`, fed by a new `crm-bff` protocol
mapper (`user-title-code-in-id-token`) reading the `titleCode` user attribute.
Both land in the **ID token**, which is what the BFF session is built from;
neither is added to the access token, so no resource server's contract changes.

### 1a. The title travels as a CODE, and the catalogue owns the words
`titleCode` carries `SALES_REP`, not `"Sales Representative"`. The frontend
resolves it to `UI-TITLE-SALES-REP`, so the header's title switches with TR/EN
like every other word on screen — this is the arrangement `displayGender` already
uses for the wire value `"Male"` (scope §2.7), applied to a second field.

**Why not store the localized text in Keycloak** (an obvious alternative — a
second `titleTr` attribute, a second mapper, a second response field):
- An identity provider is a source of identity *facts*, not of display copy. It
  has no tooling for translations — no review, no missing-translation report, no
  fallback rule — while the catalogue has all three plus `i18n.spec.ts`.
- It scales per *language*, not per *word*: a third language would mean another
  attribute, another mapper, another response field, another frontend branch, and
  another value hand-typed on every user. In the catalogue it is one more column.
- Two copies of one fact drift. Someone updates the title and forgets the Turkish
  one, and nothing notices, because the English screen still looks right.
- Renaming a title becomes a data migration across users instead of a one-line
  catalogue edit.

**The condition this rests on**, stated so a future reader can tell when it stops
holding: the title set is a closed list this project governs. If titles ever
arrive as free text from a federated HR source (LDAP `title`, SCIM), the code
assumption is wrong and the honest answer is to display them untranslated rather
than to fake an enumeration. The attribute is deliberately named `titleCode`, not
`title`, so such a federated free-text `title` can arrive later without colliding
with this one and without any consumer mistaking a code for display text.

**An unknown code renders raw.** `Shell.title` reads `I18nService.dictionary()`
instead of `translate()`, because `translate()` answers `UI-ERROR-GENERIC` for an
unknown key — a title the catalogue has not learned yet would surface as
"Something went wrong" in the header. Falling back to the code itself is honest,
obviously untranslated, and asserted in `shell.spec.ts`.

### 2. Both fields are nullable, by design and in practice
A principal that is not an `OidcUser` has neither; a realm that has not been
reconciled has no `titleCode`. The frontend types them optional-and-nullable, the
header falls back to `username` for the name, and **omits the title segment
entirely** — along with its divider — when there is no title. That last part is
FE-ADR-013 §a still doing its job: the decision that changed is "there is no
source", not "invent one".

The title sits where `mock-ui-analysis.md` §4.1 puts it: its own segment to the
left of the avatar, not a second line under the name. §4.1's `max-width: 120px`
is deliberately not reproduced — that number was measured on the mock's own
sample string and clipped the real value on every screen (recorded in scope
§2.20).

### 3. Realm reconciliation is part of the change, not a follow-up
`--import-realm` only runs when the realm is absent, so the committed realm file
alone would reach nobody who already has a `keycloak_db`. Both paths are
therefore covered and both are verified:
- the **committed realm** (`infra/keycloak/realm/crm-lite-realm.json`) carries
  the attribute on all three fixtures, the mapper, **and** a declarative user
  profile with `unmanagedAttributePolicy: ENABLED` — Keycloak 26.3's user
  profile silently DROPS an attribute that is neither declared nor
  unmanaged-enabled, so without that policy the import would look like it
  worked and produce no claim. `GatewayBffIntegrationTest` asserts both fields
  on a container built from this file alone, which is what keeps the file
  honest;
- `keycloak-init` re-applies the policy, the three attributes and the mapper on
  every `up` (mapper creation guarded by a lookup — a second `create` answers
  `409` and would break its `&&` chain).

### Consequences
- The header matches the mock's information design without any invented data,
  and it is fully bilingual — the last untranslated string on the chrome is gone.
- Adding a job title is a catalogue entry plus a Keycloak attribute value; it
  touches no Java and no contract.
- `docs/frontend/scope-and-conflicts.md` §2.20 is rewritten, and
  `mock-ui-analysis.md` §9.7 closes.
- One more thing the realm import owns; a developer who never reset their
  database gets it from `keycloak-init` and can confirm it in one line of its
  log (`keycloak-init: title attribute + mapper applied`).
