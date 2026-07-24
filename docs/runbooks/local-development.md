# Runbook — Local Development

## Prerequisites
- JDK 25, Maven 3.9+ (both on PATH)
- Rancher Desktop (dockerd) or Podman — needed for PostgreSQL, **Keycloak** and
  for Testcontainers-based tests

## Build

```bash
mvn clean install -DskipTests     # fast build
mvn clean install                 # full build incl. Testcontainers tests (Docker/Podman required)
```

## Keycloak (identity authority — ADR-006)

| What | Value |
|---|---|
| Image (pinned) | `quay.io/keycloak/keycloak:26.3.4` |
| URL / canonical issuer | `http://localhost:8180` / `http://localhost:8180/realms/crm-lite` |
| Admin console | `http://localhost:8180` → user `admin` / `admin` (**local-only bootstrap value**) |
| Realm | `crm-lite` — imported automatically from `infra/keycloak/realm/crm-lite-realm.json` |
| Client | `crm-bff` — public client, Authorization Code + **PKCE S256**, Direct Grant DISABLED |
| Audience | `crm-api` (client-level audience mapper; resource servers require it) |
| Realm role | `crm-user` (KR-8 single operator role) |
| Dev users (enabled) | `ayilmaz`, `edemir` — password `crm-dev` |
| Dev user (disabled) | `mkaya` — the AC-AUTH-01-04 "passive user" fixture |
| Token/session (KR-9) | access token **5 min** · SSO idle **30 min** · SSO max **24 h** |
| Locales | English default; English + Turkish enabled (STANDARD Keycloak i18n — a custom project login theme is future work, not implemented) |
| Database | `keycloak_db` in the shared local PostgreSQL |

> ⚠️ All passwords above are deterministic local development fixtures. They are
> not secrets, must never be reused anywhere real, and no real credential or
> client secret exists in this repository (crm-bff is a public client).

Start Keycloak + PostgreSQL only (enough for IDE-run services):

```bash
podman compose -f infra/docker-compose.yml up -d postgres keycloak
#   Rancher Desktop: docker compose -f infra/docker-compose.yml up -d postgres keycloak
```

If `keycloak_db` is missing (volume created before the auth milestone), the init
script won't rerun on an existing volume — either `podman compose -f
infra/docker-compose.yml down -v` (⚠️ wipes ALL local data) or create it by hand:
`podman exec postgres psql -U crmlite -d crm_admin -c 'CREATE DATABASE keycloak_db;'`.

## Startup order (matters!)

Write operations in customer-service require the shared catalog owner
(**lookup-service**, ADR-002) and **mernis-stub** (KR-10); write operations in
account-service require **lookup-service** and **customer-service** (address
validation, ADR-013). Business APIs additionally require a **Keycloak login**
(401 without a session/token).

1. PostgreSQL + Keycloak 2. config-server 3. discovery-server 4. lookup-service
5. mernis-stub 6. api-gateway 7. customer-service 8. account-service

customer-service and account-service still *boot* and serve reads if their write
dependencies are down — but every create/update/delete will fail closed with 503
until they are up. That is intended behaviour, not a bug. Keycloak matters at
LOGIN time (and first JWKS fetch), not at service boot.

### Maven, one terminal each (repo root)

```bash
# 0) PostgreSQL + Keycloak (first volume init creates customer_db, lookup_db,
#    keycloak_db, account_db — older volumes: see docs/runbooks/database.md for
#    the one-line manual CREATE DATABASE account_db)
podman compose -f infra/docker-compose.yml up -d postgres keycloak

mvn -pl backend/config-server    spring-boot:run   # terminal 1
mvn -pl backend/discovery-server spring-boot:run   # terminal 2
mvn -pl backend/lookup-service   spring-boot:run   # terminal 3
mvn -pl backend/mernis-stub      spring-boot:run   # terminal 4
mvn -pl backend/api-gateway      spring-boot:run   # terminal 5
mvn -pl backend/customer-service spring-boot:run   # terminal 6
mvn -pl backend/account-service  spring-boot:run   # terminal 7
```

IntelliJ: run the same applications in the same order (Postgres/Keycloak still via compose).

### Full stack via Compose

```bash
podman compose -f infra/docker-compose.yml up --build
#   Rancher Desktop: docker compose -f infra/docker-compose.yml up --build
```

Healthchecks + `depends_on: service_healthy` enforce the boot order automatically.
**In the compose profile only the gateway (8080), Keycloak (8180), config (8888),
eureka (8761) and postgres (5432) publish host ports** — customer-service,
lookup-service, mernis-stub and account-service are reachable only inside
`crm-net` (ADR-009/010); all business traffic goes through the gateway.

## Smoke test (startup sequence verification)

```bash
# infrastructure health, in start order
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8888/actuator/health   # config
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8761/actuator/health   # discovery
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8180/realms/crm-lite   # keycloak realm
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/actuator/health   # gateway

# IDE-run topology only (ports on localhost; in compose these are internal):
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8083/actuator/health   # lookup
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8084/actuator/health   # mernis-stub
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8082/actuator/health   # customer
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8085/actuator/health   # account

# security is ON: anonymous business API -> 401 MSG-AUTH-UNAUTHORIZED
curl -sS -w "\nHTTP Status: %{http_code}\n" -H "Accept: application/json" \
  "http://localhost:8080/api/customers"
curl -sS -w "\nHTTP Status: %{http_code}\n" -H "Accept: application/json" \
  "http://localhost:8080/api/accounts?customerId=1001"

# login: open http://localhost:8080/api/session/me in a BROWSER -> Keycloak login
# (ayilmaz / crm-dev) -> back to a JSON session summary. Business flows via
# Postman: see docs/postman/README.md (Authorization Code + PKCE helper).
```

Direct service checks (`:8082/:8083`) with a token require a real Keycloak JWT —
normal clients never need this; the gateway relays tokens automatically after login.

Full endpoint-by-endpoint catalogs: `docs/api/customer-service.md`,
`docs/api/account-service.md`, `docs/api/shared-lookup-service.md`,
`docs/api/mernis-stub.md`, **`docs/api/authentication.md`** (login/session/CSRF/
logout contract). Importable Postman collection: `docs/postman/`.

## MERNIS stub behaviour (deterministic, no real personal data)

Any syntactically valid 11-digit id verifies **unless deny-listed**. Default deny list:
`99999999999` (config `mernis.stub.denied-ids` in `config-repo/mernis-stub.yml`).
Use it to test the KR-10 rejection path locally. The stub deliberately has no
authentication and no CRM-realm awareness (ADR-010) — it simulates an EXTERNAL
system and is not reachable from outside the compose network.

## Tests

```bash
mvn -pl backend/crm-security-starter test   # security starter units (no Docker)
mvn -pl backend/api-gateway      test       # BFF E2E vs real Keycloak (Docker/Podman required)
mvn -pl backend/customer-service test       # 62 tests (IT needs Docker/Podman)
mvn -pl backend/lookup-service   test       # Docker required
mvn -pl backend/mernis-stub      test       # no Docker needed
mvn -pl backend/account-service  test       # 41 tests (IT needs Docker/Podman)
```

- Docker/Podman down ⇒ the Testcontainers classes fail with "Could not find a valid
  Docker environment"; start Rancher Desktop / the Podman machine and rerun.
- The surefire config pins `-Dapi.version=1.44` because Testcontainers 1.21.3's
  shaded docker-java otherwise probes Docker 29+ engines with the too-old API v1.32.
- The gateway E2E suite binds port **8080** (the committed realm's redirect URI) —
  don't run it while a local gateway instance is up.
- Full Docker/Testcontainers troubleshooting (Rancher Desktop engine selection,
  `~/.testcontainers.properties`, env var overrides, diagnostic script, exact
  troubleshooting order): see [`docs/runbooks/testcontainers.md`](testcontainers.md).

## Ports

8888 config · 8761 eureka · 8080 gateway (BFF) · 8180 keycloak · 8082 customer ·
8083 lookup · 8084 mernis-stub · 8085 account · 5432 postgres
(8082/8083/8084/8085 are host-visible only in the IDE-run topology.)

## Troubleshooting — issuer / JWKS / 401s (ADR-006 §6)

- **Symptom: valid login but services return 401 "iss claim is not valid".**
  The token's `iss` must EXACTLY equal `http://localhost:8180/realms/crm-lite`.
  Keycloak pins it via `KC_HOSTNAME` in compose — don't remove that env var, and
  don't call Keycloak's authorization endpoint via a different host than the
  canonical one.
- **Containers can't fetch JWKS.** Inside `crm-net`, `localhost` is the container
  itself. Services use `CRM_SECURITY_JWKSETURI=http://keycloak:8180/...` and the
  gateway uses `CRM_SECURITY_KEYCLOAK_INTERNALBASEURL=http://keycloak:8180`
  (already set in compose) — issuer validation stays on the canonical localhost
  value by design. If you add a service, copy those two patterns.
- **Login page loops / cookie errors in the browser.** Clear cookies for
  localhost:8080 and localhost:8180; the Keycloak dev cookies are `Secure` and
  browsers exempt localhost — non-browser HTTP clients (curl/JDK) do NOT, which
  is why scripted logins must strip the Secure flag (see the gateway E2E test).
- **`Failed to obtain/validate tokens` at the gateway right after `up`.** Keycloak
  may still be importing the realm; wait for its healthcheck (`podman ps` shows
  `healthy`) and retry the login.

## Gotchas (inherited project knowledge — see PROJECTBRAIN §5/§6)

- The gateway is Spring Cloud Gateway **WebMVC** — do not copy WebFlux examples
  (`TokenRelay` filter and `SecurityFilterChain` here are the servlet variants).
- Lombok needs explicit `annotationProcessorPaths` on JDK 25.
- Spring Boot 4 splits autoconfig into per-tech modules (`spring-boot-flyway`,
  `spring-boot-data-jpa-test`, …) — a starter on the classpath is not always enough.
- Send Turkish characters to curl via stdin heredocs, never `-d '...'` argv.
