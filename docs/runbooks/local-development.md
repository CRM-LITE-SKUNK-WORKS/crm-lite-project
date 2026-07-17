# Runbook — Local Development

## Prerequisites
- JDK 25, Maven 3.9+ (both on PATH)
- Rancher Desktop (dockerd) or Podman — needed for PostgreSQL and for
  Testcontainers-based tests

## Build

```bash
mvn clean install -DskipTests     # fast build
mvn clean install                 # full build incl. Testcontainers tests (Docker required)
```

## Startup order (matters!)

Write operations in customer-service require the shared catalog owner
(**lookup-service**, ADR-002) and **mernis-stub** (KR-10). Start order:

1. PostgreSQL 2. config-server 3. discovery-server 4. lookup-service
5. mernis-stub 6. api-gateway 7. customer-service

customer-service still *boots* and serves reads if 4/5 are down — but every create/
update/delete will fail closed with 503 until they are up. That is intended behaviour,
not a bug.

### Maven, one terminal each (repo root)

```bash
# 0) PostgreSQL (creates customer_db AND lookup_db on first volume init)
docker compose -f infra/docker-compose.yml up -d postgres
#   podman compose -f infra/docker-compose.yml up -d postgres

mvn -pl backend/config-server    spring-boot:run   # terminal 1
mvn -pl backend/discovery-server spring-boot:run   # terminal 2
mvn -pl backend/lookup-service   spring-boot:run   # terminal 3
mvn -pl backend/mernis-stub      spring-boot:run   # terminal 4
mvn -pl backend/api-gateway      spring-boot:run   # terminal 5
mvn -pl backend/customer-service spring-boot:run   # terminal 6
```

IntelliJ: run the same applications in the same order (Postgres still via compose).

### Full stack via Compose

```bash
docker compose -f infra/docker-compose.yml up --build
#   podman compose -f infra/docker-compose.yml up --build
```

Healthchecks + `depends_on: service_healthy` enforce the boot order automatically.

## Smoke test (startup sequence verification)

Run section A/B of `docs/api/customer-service.md`'s curl sequence. Quick version
(every request prints its HTTP status):

```bash
# infrastructure health, in start order
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8888/actuator/health   # config
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8761/actuator/health   # discovery
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8083/actuator/health   # lookup
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8084/actuator/health   # mernis-stub
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/actuator/health   # gateway
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8082/actuator/health   # customer

# catalog up + seeded
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8083/api/lookups/statuses/ACTV

# fake MERNIS answers
curl -sS -X POST "http://localhost:8084/api/mernis/verify" \
  -H "Content-Type: application/json; charset=utf-8" \
  -w "\nHTTP Status: %{http_code}\n" \
  --data-binary @- <<'JSON'
{"nationalityId":"12345678901","firstName":"A","lastName":"B","birthDate":"1990-01-01"}
JSON

# gateway -> customer-service: browse mode (ADR-005) and a filter
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers"
curl -sS -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/customers?firstName=Ali"
```

Full endpoint-by-endpoint catalogs: `docs/api/customer-service.md`,
`docs/api/shared-lookup-service.md`, `docs/api/mernis-stub.md`. Importable Postman
collection: `docs/postman/`.

## MERNIS stub behaviour (deterministic, no real personal data)

Any syntactically valid 11-digit id verifies **unless deny-listed**. Default deny list:
`99999999999` (config `mernis.stub.denied-ids` in `config-repo/mernis-stub.yml`).
Use it to test the KR-10 rejection path locally.

## Tests

```bash
mvn -pl backend/customer-service test    # 56 tests (21 need Docker/Testcontainers)
mvn -pl backend/lookup-service   test    # Docker required
mvn -pl backend/mernis-stub      test    # no Docker needed
```

- Docker down ⇒ the Testcontainers classes fail with "Could not find a valid Docker
  environment"; start Rancher Desktop and rerun.
- The surefire config pins `-Dapi.version=1.44` because Testcontainers 1.21.3's
  shaded docker-java otherwise probes Docker 29+ engines with the too-old API v1.32.

## Ports

8888 config · 8761 eureka · 8080 gateway · 8081 auth (skeleton) · 8082 customer ·
8083 lookup · 8084 mernis-stub · 5432 postgres

## Gotchas (inherited project knowledge — see PROJECTBRAIN §5/§6)

- The gateway is Spring Cloud Gateway **WebMVC** — do not copy WebFlux examples.
- Lombok needs explicit `annotationProcessorPaths` on JDK 25.
- Spring Boot 4 splits autoconfig into per-tech modules (`spring-boot-flyway`,
  `spring-boot-data-jpa-test`, …) — a starter on the classpath is not always enough.
- Send Turkish characters to curl via stdin heredocs, never `-d '...'` argv.
