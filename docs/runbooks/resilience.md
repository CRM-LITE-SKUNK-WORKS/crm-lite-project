# Resilience runbook

Added 2026-08-06 (observability/resilience addendum). Resilience4j is applied
**only** to durable synchronous read boundaries — the integrations that remain
synchronous even after the SALE flow (order-service → product-service /
account-service writes, ADR-016 §5) moves to messaging. It is **not** a
general-purpose retry/circuit-breaker layer applied everywhere.

Preserved unchanged: Spring Cloud Gateway (no Zuul), the existing `RestClient`
adapters (no OpenFeign). Resilience4j is wired via
`io.github.resilience4j:resilience4j-spring-boot3` annotations
(`@CircuitBreaker`/`@Bulkhead`/`@Retry`) directly on the existing `Http*Client`
implementations — no new HTTP client abstraction.

## What got it, and why

| Boundary | Instance name | Where |
|---|---|---|
| Mernis/KPS verification | `mernis` | customer-service `HttpMernisClient#verify` |
| lookup-service reads | `lookup-service` | every service's `HttpLookupCatalogClient` (customer, account, product, order) |
| KR-02 account-number search (read) | `account-service-read` | customer-service `HttpAccountServiceClient#listAccounts`/`#fetchAccount` |
| KR-02 order-number search (read) | `order-service-read` | customer-service `HttpOrderServiceClient#fetchOrder` |
| customer/address validation (read) | `customer-service-read` | account-service and product-service `HttpCustomerServiceClient` |
| account product-ids (read) | `account-service-read` | product-service `HttpAccountServiceClient#fetchProductIds` |

**Mernis is a POST, protected anyway.** `POST /api/mernis/verify` has no side
effect — a pure verification check, not a mutation — so despite the HTTP verb
it is exactly the kind of "durable synchronous read" this addendum protects,
and the requirement names it explicitly.

## What deliberately did NOT get it

- **`HttpAccountServiceClient#passivateAccount`** (customer-service) — a
  `DELETE`, already documented as non-idempotent (a Passive account 409s on
  repeat). No `@CircuitBreaker`/`@Bulkhead`/`@Retry` at all, on principle: a
  "configured with no retry" instance sitting right next to a retried one on
  the same class is an easy thing to misconfigure later. It keeps its
  pre-existing `disableAutomaticRetries()` transport setting, unchanged.
- **order-service → account-service and order-service → product-service** —
  the entire SALE-write boundary (ADR-016 §5: precondition read, product
  create/confirm/compensate, the involvement write). This is the boundary
  named in the requirement as the one to leave alone, because it is
  **scheduled to move to messaging** — adding Resilience4j here now would be
  work thrown away by that migration, and worse, would risk masking exactly
  the kind of transport double-execution bug ADR-016 §5.3b already found and
  fixed (`disableAutomaticRetries()` stays the only protection there).
  `NoResilienceOnSaleWriteClientsTest` (order-service) asserts by reflection
  that neither `HttpAccountServiceClient` nor `HttpProductServiceClient` in
  order-service carries any of the three annotations, so a future change
  cannot silently add one without the test failing.
- order-service's `lookup-service` calls are the ONE exception inside
  order-service that DOES get the full treatment — lookup-service is not part
  of the SALE-write boundary being replaced, it is the shared catalog.

## Configuration order (per requirement)

For every protected boundary, in this order:

1. **Explicit connect timeout** (2s) **and read timeout** (5s) —
   `HttpClientConfig#resilientRequestFactory` (or the equivalently-named method
   per service), an `HttpComponentsClientHttpRequestFactory` built with an
   Apache HttpClient5 `RequestConfig`. Transport-level automatic retries are
   disabled on the SAME factory (`disableAutomaticRetries()`) — Resilience4j,
   not httpclient5's default retry strategy, owns the retry decision, for the
   identical reason ADR-016 §5.3b already established for the SALE-write
   clients.
2. **Bulkhead** — `maxConcurrentCalls: 10` (20 for `lookup-service`, the
   highest-traffic shared boundary).
3. **Circuit breaker** — count-based, window 10, minimum 5 calls, 50% failure
   threshold, 10s open state, 3 permitted half-open calls, automatic
   OPEN→HALF_OPEN transition. `recordExceptions` is scoped to the ONE
   converted `*UnavailableException` type each client already throws — a
   `HttpClientErrorException.NotFound` (returned as `Optional.empty()`, never
   thrown) never counts as a circuit-breaker failure.
4. **Retry** — `maxAttempts: 3`, `waitDuration: 200ms`, `retryExceptions`
   scoped the same way. Selective and read-only, per requirement 10/11.

Full property blocks: `resilience4j.*` in each service's config-repo YAML
(`account-service.yml`, `customer-service.yml`, `product-service.yml`,
`order-service.yml`).

## Fail-closed, never a fake fallback

**No `fallbackMethod` is used anywhere.** When the circuit is open
(`CallNotPermittedException`) or the bulkhead is full (`BulkheadFullException`),
the exception propagates exactly like a genuine connection failure — these two
exception types did not exist before this addendum and would otherwise fall
through to the generic `Exception` handler (`500 MSG-INTERNAL-ERROR`, the wrong
contract). Each of the four services' `GlobalExceptionHandler` gained one
handler mapping both to the SAME `503 MSG-SERVICE-UNAVAILABLE` (or, in
customer-service, `MSG-MERNIS-UNAVAILABLE` when
`CallNotPermittedException#getCausingCircuitBreakerName()` is `"mernis"`) the
service already produces for a real downstream outage. Required downstream
validation still fails closed — nothing is invented, nothing silently
"succeeds" because a breaker tripped.

## Metrics

`resilience4j-micrometer` is on the classpath of all four services with
protected boundaries, auto-binding circuit-breaker/bulkhead/retry state to the
SAME `MeterRegistry` Actuator exposes at `/actuator/prometheus` — no separate
wiring. Key series: `resilience4j_circuitbreaker_calls_seconds_count{kind=...}`,
`resilience4j_circuitbreaker_state`, `resilience4j_bulkhead_available_concurrent_calls`,
`resilience4j_retry_calls_seconds_count`. Each service's `management.endpoints.
web.exposure.include` also adds `circuitbreakers`/`circuitbreakerevents` for
direct Actuator inspection (`/actuator/circuitbreakers`,
`/actuator/circuitbreakerevents`) without needing Prometheus/Grafana running.
See the "Downstream HTTP failures" and "Circuit breaker state" panels in
`docs/runbooks/observability.md`'s dashboard.

## Tests

- `backend/customer-service/src/test/java/com/crm/customer/resilience/MernisResilienceTest.java`
  — against the REAL `HttpMernisClient` and a real embedded JDK `HttpServer`
  (deliberately not the full Spring context / Eureka+LoadBalancer stack, which
  has no role in what is being proven): a slow downstream trips the read
  timeout and fails closed; repeated failures open the circuit breaker, an
  open breaker refuses a call without reaching the server, and a manual
  transition to HALF_OPEN followed by healthy responses closes it again
  (half-open recovery); a safe read is retried and eventually succeeds,
  proving the server was actually called more than once; an unwrapped
  write-style call reaches the server exactly once even on failure (no
  transport or Resilience4j retry); a full bulkhead refuses a call without
  reaching the server.
- `backend/order-service/src/test/java/com/crm/order/resilience/NoResilienceOnSaleWriteClientsTest.java`
  — reflection guard proving the SALE-write clients carry none of the three
  annotations (see above).
