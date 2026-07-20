# ADR-006: Keycloak as the Sole Identity and Token Authority (Authorization Code + PKCE)

## Status
Accepted (2026-07-17) — implemented in the authentication/security milestone.
Partially supersedes ADR-004 (see the status note there).

## Context
FR-AUTH-01/02, KR-8 (single operator role) and KR-9 (session timeouts) require
authentication, but the repository had none: the gateway ran `permitAll`, the
auth-service module was an empty skeleton carrying JJWT/JPA dependencies that
implied a self-minted-JWT + local password table design, and the entity workbook
contains a `USERS` table with `password_hash` seeds. Current OAuth security best
practice (OAuth 2.0 Security BCP / OAuth 2.1) prohibits the Resource Owner
Password Credentials grant, including for first-party applications.

## Decision
1. **Keycloak is the only identity and token authority.** It owns user
   credentials, enabled/disabled state, authentication, sessions, refresh
   lifecycle, realm roles, token issuance and signing keys. Pinned image
   `quay.io/keycloak/keycloak:26.3.4`, realm **`crm-lite`**, committed as a realm
   import at `infra/keycloak/realm/crm-lite-realm.json`, backed by `keycloak_db`
   in the shared local PostgreSQL.
2. **Login uses the OpenID Connect Authorization Code flow with PKCE (S256).**
   Credentials are entered ONLY on the Keycloak login page (theme customization is
   future work — see the runbook). The application never sees, stores, hashes or
   validates a password.
3. **Rejected grant types:** Direct Access Grant / ROPC / password grant are
   disabled on the client (`directAccessGrantsEnabled: false` in the realm
   export) and must never be enabled — not in the app, not in tests, not in
   Postman. Implicit flow and service accounts are disabled likewise.
4. **No custom JWT issuer.** The JJWT dependencies were removed together with the
   auth-service module (ADR-007). No application code mints or signs tokens; a
   structural test/regression check keeps JJWT out of the dependency tree.
5. **Client `crm-bff` is a PUBLIC client with mandatory PKCE** and no client
   secret. Chosen so that no secret of any kind is committed to the repository
   (`config-repo` files are baked into a jar in plaintext — see PROJECTBRAIN
   §9.4). Real deployments should switch to a confidential client with a vaulted
   secret injected via environment; the Spring registration then only changes
   `client-authentication-method` and gains `client-secret`.
6. **Canonical issuer strategy.** The JWT `iss` claim is pinned via Keycloak's
   `KC_HOSTNAME` to the browser-facing URL (`http://localhost:8180/realms/crm-lite`
   locally) in EVERY topology. Services validate `iss` against that canonical
   value; JWKS (and the gateway's token endpoint) may be fetched over a
   compose-internal URL (`http://keycloak:8180/...`) via `crm.security.jwk-set-uri`
   / `crm.security.keycloak.internal-base-url` overrides. This removes the classic
   localhost-vs-container-DNS issuer mismatch that causes intermittent 401s.
7. **Audience strategy.** A client-level audience mapper on `crm-bff` stamps
   **`crm-api`** into every access token's `aud`; every resource server requires
   it (ADR-009). Per-service audiences are a possible future refinement.

## Consequences
- FR-AUTH-01's UI acceptance criteria (masking, disabled login button, 64-char
  limit, MSG-AUTH-INVALID-CRED) bind the Keycloak login page, not an Angular
  form. Keycloak's generic invalid-credential message already satisfies the
  "do not reveal user-exists/passive" wording of AC-AUTH-01-03/04/05.
- The workbook `USERS` table is not implemented anywhere (ADR-011).
- Local development gets deterministic, non-sensitive dev users (see the
  runbook); their passwords are local-only fixtures, never real credentials.
