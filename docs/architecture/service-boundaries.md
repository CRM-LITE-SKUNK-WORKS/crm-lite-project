# Service Boundaries

```
                          ┌──────────────────────┐
        client ─────────► │  api-gateway :8080   │ (Spring Cloud Gateway WebMVC, permitAll — temporary)
                          └──┬────────┬──────┬───┘
              /api/customers/**  /api/cities/**  /api/lookups/**
                          ▼                      ▼
        ┌──────────────────────────┐   ┌──────────────────────┐
        │ customer-service :8082   │──►│ lookup-service :8083 │  GNL_ST / GNL_TP owner
        │ customer_db              │   │ lookup_db (ADR-002)  │
        │  customer / address /    │   └──────────────────────┘
        │  contact / lookup /      │──►┌──────────────────────┐
        │  mernis / common         │   │ mernis-stub :8084    │  fake KPS (KR-10, no DB)
        └──────────────────────────┘   └──────────────────────┘

        config-server :8888 (native/classpath config-repo) — everyone's config source
        discovery-server :8761 (Eureka) — service registry
        auth-service :8081 — empty skeleton (ADR-004 direction: Keycloak)
```

## Boundaries and rules

1. **Customer aggregate (ADR-001):** customer core + address + contact are internal
   packages of ONE deployable (`com.crm.customer.{customer,address,contact,lookup,mernis,common}`)
   sharing `customer_db`, because create must be one ACID transaction. They are not
   separate services and must not become ones without revisiting the consistency requirement.
2. **Shared catalogs (ADR-002):** GNL_ST/GNL_TP exist only in `lookup_db`. Consumers
   integrate through the REST API via a dedicated client boundary
   (`com.crm.customer.lookup`) — controllers/repositories never call it directly.
   customer_db stores contract-immutable central IDs; **no cross-database FKs**;
   reads/lifecycle filters are fully local; writes fail closed (503) when the catalog
   is unreachable and the value is uncached.
3. **MERNIS (KR-10):** identity verification is an external dependency behind
   `com.crm.customer.mernis.MernisClient`; verification failure or unavailability
   means the customer is NOT created.
4. **Future domains** (account, product, order/sale, auth): own services + own
   databases per the seed workbook. Until they exist, cross-domain behaviour is an
   explicit 501 or documented no-op TODO — never silently faked.
5. **Ports:** 8888 config, 8761 eureka, 8080 gateway, 8081 auth, 8082 customer,
   8083 lookup, 8084 mernis-stub.
