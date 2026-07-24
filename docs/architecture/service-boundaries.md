# Service Boundaries

Last updated: 2026-07-23 (**account-service implemented** — FR-ACCT-01..04 + KR-11,
ADR-013/014; same-day earlier state: v8-1 reconciliation, documentation only).
Prior: 2026-07-18 (authentication/security milestone — ADR-006..011).

```
   browser ──► api-gateway :8080 ◄──────── login redirect ────► Keycloak :8180
   (session   (Spring Cloud Gateway WebMVC = BFF: oauth2Login    realm crm-lite
    cookie)    + PKCE, CSRF, TokenRelay — ADR-007/008)           (keycloak_db, ADR-006)
                          └──┬────────┬──────┬───
              /api/customers/**  /api/cities/**  /api/lookups/**   (Authorization: Bearer)
                          ▼                      ▼
        ┌──────────────────────────┐   ┌──────────────────────┐
        │ customer-service :8082   │──►│ lookup-service :8083 │  GNL_ST / GNL_TP owner
        │ customer_db              │   │ lookup_db (ADR-002)  │  (JWT resource server,
        │  customer / address /    │   └──────────────────────┘   user token propagated
        │  contact / lookup /      │──►┌──────────────────────┐   — ADR-009/010)
        │  mernis / common         │   │ mernis-stub :8084    │  fake KPS (KR-10, no DB,
        │  (JWT resource server)   │   │ (NO CRM token,       │   NOT on the gateway)
        └──────────────────────────┘   │  ADR-010)            │
                                       └──────────────────────┘
        config-server :8888 (native/classpath config-repo) — everyone's config source
        discovery-server :8761 (Eureka) — service registry
        crm-security-starter — shared resource-server security module (library, not a runtime; ADR-009)
        auth-service — REMOVED (ADR-007; the gateway is the BFF, Keycloak the authority)
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
5. **Authentication (ADR-006..011):** Keycloak (`:8180`, realm `crm-lite`) is the
   sole identity/token authority; the gateway is the only browser edge (session +
   CSRF cookies live nowhere else); customer-service and lookup-service are
   zero-trust JWT resource servers (signature/issuer/audience/`crm-user` role)
   via the shared `crm-security-starter`; the user token is propagated to
   lookup-service, never to mernis-stub; audit `*_by` columns carry the Keycloak
   `sub`. There is no application USERS/password table (ADR-011) and no
   auth-service (removed, ADR-007).
6. **Billing accounts (ADR-013/014):** `account-service` (port 8085, `account_db`)
   owns `acct_tp` (local 223/224 catalog — NOT a shared GNL catalog), `cust_acct`,
   `cust_acct_prod_invl` and `acct_number_seq`. `customer_number` (public business
   number) and `address_id` are FK-less external references; addresses are
   validated through customer-service's public API with the user's token
   propagated (ADR-010 addendum). KR-11 numbers are VARCHAR(10), Luhn-checked,
   immutable, never reused. Delete = passivation; Passive rows stay list-visible.
   The K-8 automatic 223 Customer Account is a same-transaction side effect of
   the first 224 and is never exposed by the API.
   **`cust_acct_prod_invl` is written ONLY by account-service** — future
   product/order/sale services integrate via an account-service command/API or a
   consumed event, never by writing `account_db` directly.
7. **Future domains** (see roadmap below): own services + own databases per the seed
   workbook. Until they exist, cross-domain behaviour is an explicit 501 or a
   documented no-op TODO — never silently faked.
8. **Ports:** 8888 config, 8761 eureka, 8080 gateway (BFF), 8180 keycloak,
   8082 customer, 8083 lookup, 8084 mernis-stub, 8085 account
   (8082/8083/8084/8085 host-visible only in the IDE-run topology; compose keeps
   them internal).

## Service roadmap and current status (honest, evidence-based)

| Service | Status | Evidence / notes |
|---|---|---|
| config-server | ✅ Implemented | native/classpath config-repo (`src/main/resources/config-repo/*.yml`); rebuild+restart needed per config change (accepted trade-off) |
| discovery-server | ✅ Implemented | Eureka server, port 8761 |
| api-gateway | ✅ Implemented (routing + **BFF security**) | WebMVC routes for customers/cities/lookups with `TokenRelay` + cookie stripping; Authorization Code + PKCE login, session/CSRF ownership, `/api/session/me`, RP-initiated logout (ADR-007/008). permitAll is GONE |
| keycloak (infra) | ✅ Implemented | Pinned 26.3.4, realm `crm-lite` committed as import, client `crm-bff` (public + PKCE), role `crm-user`, KR-9 session settings, dev users ayilmaz/edemir (+ disabled mkaya) — ADR-006/011 |
| crm-security-starter | ✅ Implemented | Shared resource-server defaults: JWT sig/iss/aud validation, realm-role mapping, 401/403 contract, `AuditorAware` (JWT sub), bearer propagation (ADR-009). A library module, not a deployable |
| lookup-service | ✅ Implemented | central GNL_ST/GNL_TP owner (ADR-002), Flyway-seeded contract IDs, Testcontainers IT |
| customer-service | ✅ Implemented | full customer/address/contact aggregate (ADR-001), ADR-005 list contract; unit + Testcontainers integration suite (see build results in PROJECTBRAIN §8) |
| mernis-stub | ✅ Implemented | fake KPS/MERNIS (KR-10), deterministic, no DB, not gateway-exposed |
| address-service | 🚫 Must NOT exist | internal customer-service module under ADR-001 |
| contact-service | 🚫 Must NOT exist | internal customer-service module under ADR-001 |
| auth / security milestone | ✅ **Implemented (2026-07-17)** | Keycloak sole authority + gateway BFF + zero-trust resource servers + JWT-sub audit (ADR-006..011). The auth-service skeleton was REMOVED (ADR-007) — it must not come back; a future profile store, if ever needed, is a new sub-keyed service per ADR-011 |
| localization-service | 🗓️ Planned | Required by FR-LANG if the architecture keeps a central label/message catalog; **default language is now English** (16.07.2026). Backend already returns language-neutral `messageKey`s. Not started |
| account-service | ✅ **Implemented (2026-07-23)** | FR-ACCT-01..04 + KR-11 per **ADR-013/014**: `account_db` (acct_tp/cust_acct/cust_acct_prod_invl/acct_number_seq), gateway route `/api/accounts/**`, zero-trust resource server, K-8 lazy 223, delete = passivation (stays list-visible). Unit + Testcontainers IT suite (`AccountServiceIntegrationTest`); Swagger in the unified gateway UI. customer-service's account-related 501/no-ops convert in a separate follow-up PR |
| product-service | 🗓️ Planned (probable owner) | PROD_SPEC/PROD_OFR/PROD/CMPG*/PROD_CATAL* (product/catalog/campaign/product-instance); final boundary may combine product+catalog scope for this project — not analyst-final |
| order-service | 🗓️ Planned (probable owner) | BSN_INTER, CUST_ORD, CUST_ORD_ITEM + sale orchestration (FR-SALE); not analyst-final |

Planned ≠ approved: the account/product/order/localization ownership above is the
team's working assumption from the seed workbook; analyst or architecture sign-off is
still missing and must happen before any of them is created.
