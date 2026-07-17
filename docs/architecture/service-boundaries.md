# Service Boundaries

Last updated: 2026-07-16.

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
        │  mernis / common         │   │ mernis-stub :8084    │  fake KPS (KR-10, no DB, NOT on the gateway)
        └──────────────────────────┘   └──────────────────────┘

        config-server :8888 (native/classpath config-repo) — everyone's config source
        discovery-server :8761 (Eureka) — service registry
        auth-service :8081 — empty skeleton (ADR-004 direction: Keycloak)
```

## Boundaries and rules

1. **Customer aggregate (ADR-001):** customer core + address + contact are internal
   packages of ONE deployable (`com.crm.customer.{customer,address,contact,lookup,mernis,common}`)
   sharing `customer_db`, because create must be one ACID transaction. They are not
   separate services and must not become ones without revisiting the consistency
   requirement. **address-service and contact-service must NOT exist as separate
   deployables.**
2. **Shared catalogs (ADR-002):** GNL_ST/GNL_TP exist only in `lookup_db`. Consumers
   integrate through the REST API via a dedicated client boundary
   (`com.crm.customer.lookup`) — controllers/repositories never call it directly.
   customer_db stores contract-immutable central IDs; **no cross-database FKs**;
   reads/lifecycle filters are fully local; writes fail closed (503) when the catalog
   is unreachable and the value is uncached.
3. **MERNIS (KR-10):** identity verification is an external dependency behind
   `com.crm.customer.mernis.MernisClient`; verification failure (400
   `MSG-CUST-NATID-VERIFICATION-FAILED`) or unavailability (503
   `MSG-MERNIS-UNAVAILABLE`) means the customer is NOT created. mernis-stub is not
   routed through the gateway.
4. **Customer list contract (ADR-005):** `GET /api/customers` is the single
   browse-and-filter endpoint returning `Page<CustomerDetailResponse>`; no `/search`
   alias, no separate list endpoint.
5. **Future domains** (see roadmap below): own services + own databases per the seed
   workbook. Until they exist, cross-domain behaviour is an explicit 501 or a
   documented no-op TODO — never silently faked.
6. **Ports:** 8888 config, 8761 eureka, 8080 gateway, 8081 auth, 8082 customer,
   8083 lookup, 8084 mernis-stub.

## Service roadmap and current status (honest, evidence-based)

| Service | Status | Evidence / notes |
|---|---|---|
| config-server | ✅ Implemented | native/classpath config-repo (`src/main/resources/config-repo/*.yml`); rebuild+restart needed per config change (accepted trade-off) |
| discovery-server | ✅ Implemented | Eureka server, port 8761 |
| api-gateway | ✅ Implemented (routing) | WebMVC routes for customers/cities/lookups + auth placeholder; **security is temporary permitAll until the Keycloak milestone** |
| lookup-service | ✅ Implemented | central GNL_ST/GNL_TP owner (ADR-002), Flyway-seeded contract IDs, Testcontainers IT |
| customer-service | ✅ Implemented | full customer/address/contact aggregate (ADR-001), ADR-005 list contract; unit + Testcontainers integration suite (see build results in PROJECTBRAIN §8) |
| mernis-stub | ✅ Implemented | fake KPS/MERNIS (KR-10), deterministic, no DB, not gateway-exposed |
| address-service | 🚫 Must NOT exist | internal customer-service module under ADR-001 |
| contact-service | 🚫 Must NOT exist | internal customer-service module under ADR-001 |
| auth / security milestone | ⏭️ **Next planned work** | Direction: Keycloak (ADR-004). The milestone is the authentication/security **architecture and implementation** — not necessarily filling in the existing auth-service skeleton; whether that skeleton survives, becomes a thin facade, or is replaced is part of the milestone's design work. Do NOT describe auth-service as completed |
| localization-service | 🗓️ Planned | Required by FR-LANG if the architecture keeps a central label/message catalog; **default language is now English** (16.07.2026). Backend already returns language-neutral `messageKey`s. Not started |
| account-service | 🗓️ Planned (probable owner) | ACCT_TP + CUST_ACCT (billing accounts, auto Customer Account); boundary not analyst-final |
| product-service | 🗓️ Planned (probable owner) | PROD_SPEC/PROD_OFR/PROD/CMPG*/PROD_CATAL* (product/catalog/campaign/product-instance); final boundary may combine product+catalog scope for this project — not analyst-final |
| order-service | 🗓️ Planned (probable owner) | BSN_INTER, CUST_ORD, CUST_ORD_ITEM + sale orchestration (FR-SALE); not analyst-final |

Planned ≠ approved: the account/product/order/localization ownership above is the
team's working assumption from the seed workbook; analyst or architecture sign-off is
still missing and must happen before any of them is created.
