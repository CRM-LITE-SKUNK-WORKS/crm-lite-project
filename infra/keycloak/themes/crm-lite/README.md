# CRM Lite — Keycloak `crm-lite` login theme

Reproduces the analyst **"Login v2"** mock (Etiya EDS Lite design system) as a
Keycloak **login** theme. This is the correct home for the FR-AUTH-01 login UI:
by architecture decision the login page is Keycloak's, not an Angular/in-app form
(**ADR-006**: credentials only on the Keycloak page; ROPC/Direct Grant disabled;
no token ever reaches the browser — ADR-007/008). The open item recorded in
`docs/requirements/document-delta.md` **#6** ("future Keycloak project theme") is
what this folder delivers.

## Layout

```
themes/crm-lite/
  README.md
  login/
    theme.properties            # parent=keycloak, locales=en,tr
    login.ftl                   # standalone template matching the mock
    messages/
      messages_en.properties    # theme copy (English, default)
      messages_tr.properties    # theme copy (Turkish)  — UTF-8
    resources/
      css/login.css             # reconstructed EDS Lite styling
      js/login.js               # password toggle + disable-until-filled (UX only)
      img/etiya-logo.svg        # exact logo extracted from the mock
      img/favicon.svg
```

Only `login.ftl` is overridden; every other page (error, etc.) falls back to the
stock `keycloak` theme. Registration/reset are disabled in the realm, so those
pages never render.

## How it maps the mock to real authentication

| Mock (prototype) | This theme (real Keycloak) |
|---|---|
| Hardcoded `salesperson/etiya2026`, JS view-swap on submit | Form posts to `${url.loginAction}`; Keycloak authenticates (Auth Code + PKCE) |
| Demo 3-attempt lockout | Keycloak's own brute-force settings (realm) |
| "Demo credentials" hint line | **Removed** — never print credentials on a real login page |
| Inline error text | Keycloak's localized, non-revealing message (`message.summary`, AC-AUTH-01-03/04/05) |
| Username / Password fields | `name="username"` / `name="password"` (the names Keycloak expects) |

FR-AUTH-01 UI criteria carried here: password masking (default) + reveal toggle,
`maxlength="64"` on both fields, login button disabled while a field is empty
(progressive enhancement — server still validates), EN/TR language switch.

## Applying the theme (automatic — no manual step)

`docker-compose.yml` mounts `./keycloak/themes` into the Keycloak container and the
realm import sets `"loginTheme": "crm-lite"`. That import alone is not enough:
**`--import-realm` only imports on first start** (when the realm is absent from
`keycloak_db`), so a developer whose `keycloak_db` predates this theme keeps an empty
`realm.login_theme` and still gets Keycloak's stock login page.

The **`keycloak-init`** service in `docker-compose.yml` closes that gap. It is a
one-shot container that waits for Keycloak to report healthy, then writes
`loginTheme=crm-lite` on the realm via `kcadm.sh` and exits. It runs on every
`podman compose up`, is idempotent (rewrites the same value), and touches only the
`crm-lite` realm — `master` / the Admin Console login page are left alone. Nothing
`depends_on` it, so if it ever fails the rest of the stack still comes up.

So a plain `podman compose up` is all that is required, on a fresh `keycloak_db` or
an existing one.

**Manual fallback** (only if `keycloak-init` is unavailable or failed): Admin Console
→ `http://localhost:8180` → realm `crm-lite` → **Realm settings → Themes → Login
theme → `crm-lite`** → Save. Do **not** reach for `podman compose down -v` to force a
re-import — it destroys all DBs (see `docs/runbooks/auth-testing.md`).

## Iterating against the mock

`start-dev` disables theme caching, so the loop is fast:

1. Start the stack (see `docs/runbooks/auth-testing.md` §2) — or just `postgres` +
   `keycloak`.
2. Open `http://localhost:8080/oauth2/authorization/keycloak` (or, to see the page
   directly, the account/login URL for the `crm-lite` realm).
3. Edit files under `login/` → **refresh the browser** (no restart).
4. Compare to the mock's Login v2 screen; adjust `login.css`.

## Reconstruction notes (verify against the mock)

The mock is fully tokenized; most values were recovered exactly from the design's
inline token fallbacks and the logo asset. A few values could not be extracted from
the tokenized bundle and are best-fit — all marked `(assumed)` in `login.css`:
primary-button hover/active shades, focus-ring color, placeholder color, hover
border, and disabled colors. The primary button base color is the Etiya brand orange
`#F58220` (from the logo). The typeface is **Inter**; this theme uses an Inter →
system fallback stack rather than bundling Inter's woff2 files — say the word to
self-host the exact Inter weights for full typographic fidelity.
