# Observability runbook

Added 2026-08-06 (observability/resilience addendum). Covers Actuator +
Prometheus metrics, structured JSON logging, and the opt-in local
Prometheus/Grafana/Loki/Alloy stack. For the Resilience4j boundaries these
metrics also cover, see [resilience.md](resilience.md).

## Metrics — Actuator + Micrometer Prometheus

Every deployable (`config-server`, `discovery-server`, `api-gateway`,
`customer-service`, `lookup-service`, `mernis-stub`, `account-service`,
`product-service`, `order-service`) now exposes `GET /actuator/prometheus`
alongside the existing `/actuator/health`.

**Internal-only, by construction, not by a new access-control layer**, for the
8 services with no host-published port (ADR-009): the endpoint is reachable
only from inside `crm-net`, exactly like every other endpoint they serve. On
the 5 services that also run `crm-security-starter` (customer/lookup/account/
product/order), `/actuator/prometheus` is added to `crm.security.permit-paths`
alongside `/actuator/health` — anonymous like health, not JWT-gated, because a
Prometheus scraper cannot hold a Keycloak session.

**api-gateway is the one exception** — it has a HOST-PUBLISHED port (`:8080`).
`/actuator/prometheus` is exposed there too (`management.endpoints.web.
exposure.include`) but is deliberately **left behind the existing `crm-user`
JWT requirement** in `SecurityConfig` rather than added to the permitAll list —
putting gateway JVM/HTTP metrics on the public internet edge would not be
"internal-only". Consequence: the Compose Prometheus (`infra/observability/
prometheus.yml`) does **not** scrape api-gateway. A dedicated internal-only
management port (`management.server.port`) would fix this properly and is
recorded as follow-up work — deferred here to avoid touching the gateway's
carefully-tuned BFF security chain (see PROJECTBRAIN §4.3) in this change.

`config-server`/`discovery-server`/`mernis-stub` carry no resource-server
security at all, so `/actuator/prometheus` is open the same way
`/actuator/health` already was — no change in security posture, just a new
endpoint.

## Structured JSON logging

New shared module **`backend/crm-observability-starter`** (parallel to
`crm-security-starter` — a separate module because it is depended on by
services that have NO security starter at all, e.g. `mernis-stub`,
`discovery-server`, `config-server`).

- `logback-json-base.xml`: a Logback fragment every service includes via
  `<include resource="logback-json-base.xml"/>` from its own
  `logback-spring.xml`. Uses `net.logstash.logback.encoder.LogstashEncoder`
  (still SLF4J + Logback — **no Log4j2** was added). Static fields `service`
  (`spring.application.name`) and `environment` (`crm.observability.
  environment`, default `local`, overridable via `CRM_OBSERVABILITY_ENVIRONMENT`).
- Every current SLF4J MDC entry is included automatically (LogstashEncoder's
  default behaviour) — no per-key wiring needed as new MDC keys appear.
- **Fields, "where available"** (`com.crm.observability.starter.MdcKeys`):
  - `service`, `environment` — always (static per-process fields, not MDC).
  - `correlationId` — always, from `CorrelationIdFilter` (below).
  - `traceId`, `spanId` — whenever `micrometer-tracing-bridge-brave` is
    resolving a trace (added to every service; auto-writes to MDC once on the
    classpath, no extra config — no exporter/collector is wired up, this is
    local MDC correlation only).
  - `orderNumber` — order-service only, set the moment the KR-12 number is
    assigned (`OrderPersistence#persistOrder`) so every log for the REST of
    that request (including compensation) carries it; cleared by
    `OrderController` in a `finally` block.
  - `sagaId`, `eventId` — **reserved, not populated by anything today.** These
    exist for the messaging-based SALE flow this observability work is
    explicitly preparing for (see [resilience.md](resilience.md)); nothing
    fabricates a saga/event system to fill them prematurely.
  - `exceptionType` — set immediately around the `log.error(...)` call in each
    service's `GlobalExceptionHandler#handleUnexpected` (the catch-all 500
    path) and cleared straight after, so it never leaks into unrelated logs.

### Sensitive-data masking

`SensitiveDataMaskingDecorator` (a `JsonGeneratorDecorator` wired into the
shared encoder) runs **every string value written to the log JSON** — the
message, MDC values, structured fields, all of it — through
`SensitiveDataMaskingRules`, which redacts (regex, defence in depth, not a
substitute for not logging secrets in application code):

| Pattern | Example matched |
|---|---|
| JWT | `eyJhbGci....` (3-segment base64url) |
| `Bearer <token>` | `Authorization: Bearer eyJ...` |
| `Cookie:` / `Set-Cookie:` header lines | header NAME kept, value masked |
| 11-digit National ID | `12345678901` (10-digit KR-11/KR-12 business numbers are NOT touched) |
| credential-shaped key/value pairs | `password=...`, `"clientSecret":"..."`, `client_secret=...` |

Tested end-to-end against a real `LogstashEncoder` in
`crm-observability-starter`'s `LogstashJsonMaskingIntegrationTest` (proves the
masking actually applies through logstash-logback-encoder's real JSON
generation path, not just the regex layer in isolation) and
`SensitiveDataMaskingRulesTest` (the regex rules themselves).

**Personal-data request bodies**: this project's application code never logs a
raw request body (verified by inspection, not by a regex — there is nothing to
mask because nothing is ever written). Do not introduce request/response body
logging (e.g. `logging.level.org.springframework.web=DEBUG`, HTTP client wire
logging) without re-checking this.

## Local stack: Prometheus + Grafana + Loki + Alloy

Opt-in Compose profile, never started by a plain `docker compose up`:

```bash
cd infra
docker compose --profile observability up -d
# or: podman compose --profile observability up -d
```

| Component | Port | Purpose |
|---|---|---|
| Prometheus | `:9090` | scrapes `/actuator/prometheus` on 8 services (`infra/observability/prometheus.yml`) |
| Loki | `:3100` | log storage |
| Grafana Alloy | — | discovers containers via the docker socket (read-only mount) and ships their stdout (JSON) to Loki |
| Grafana | `:3000` | dashboards, anonymous Viewer access locally (`admin`/`admin` for the provisioning-managed account) |

Grafana auto-provisions both datasources (`infra/observability/grafana/
provisioning/datasources`) and one dashboard, **"CRM Lite — Overview"**
(`infra/observability/grafana/dashboards/crm-lite-overview.json`), covering:
request rate, error rate (5xx), p95 latency, service health (`up`), JVM heap
memory, GC pause time, HikariCP pool usage (active/idle/pending), downstream
HTTP failures + circuit-breaker state (Resilience4j metrics — see
resilience.md), and order submit outcomes (`POST /api/orders` by status).

**Verified 2026-08-06**: started against this repo's compose stack — Prometheus
healthy and scraping all 8 configured targets (some showed 401/404 at
verification time because the RUNNING application containers were images built
before this addendum's code landed; rebuilding the images resolves this — not
re-run here to avoid restarting a stack that may be in active use). Grafana
healthy, both datasources and the dashboard auto-provisioned and visible via
its API. Loki started (brief "not ready" grace period on fresh start is
normal). Alloy started.

Tear down: `docker compose --profile observability down` (add `-v` to also
drop `prometheus-data`/`loki-data`/`grafana-data`).
