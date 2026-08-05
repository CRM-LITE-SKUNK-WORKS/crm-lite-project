# Service Boundaries

Last updated: 2026-08-05 (**FR/AC v8-2 (03.08.2026) reconciliation** — reviewed against
the prior v8-1 baseline; AC-SALE-02-01 wording simplified to a single Active-account
condition and the three SALE basket messages (MSG-SALE-NO-INTERNET/-RESOURCE/-ACTIVATION)
rewritten as explicit error conditions, both already matching this document's own
wording — **no service-boundary or behavioral change**.)
Prior: 2026-08-02 (**FR-SALE §2.7 implemented** — `order-service` (:8087,
`order_db`) orchestrates the sale across a product-service **write slice** and a new
account-service **involvement command**; ADR-015 and ADR-016 written, ADR-013 amended
with §3.6/§7/§8. **The ADR debt recorded below since 2026-07-29 is discharged.**)
Prior: 2026-07-29 (product-service — read-only FR-PROD-01..02 slice, ADR debt owed).
Prior: 2026-07-23 (**account-service implemented** — FR-ACCT-01..04
+ KR-11, ADR-013/014; same-day earlier state: v8-1 reconciliation, documentation
only). Prior: 2026-07-18 (authentication/security milestone — ADR-006..011).

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
   FR-SALE §2.7 sale orchestration (ADR-016) — synchronous REST via Eureka only:

        order-service :8087 ──(0) GET  /api/accounts/{n}          ─► account-service :8085
        order_db      ──(1) local write: BSN_INTER + CUST_ORD + ITEM   (MIDLWARE)
        (bsn_inter /  ──(2) POST /api/products            ─► product-service :8086  (PNDG)
         cust_ord /   ──(3) local write: product_id + amount snapshots
         cust_ord_item──(4) POST /api/products/confirm    ─► product-service  (PNDG → ACTV)
         + seq)       ──(5) POST /api/accounts/{n}/product-involvements ─► account-service
                             ▲ THE COMMIT POINT — nothing follows it, so nothing undoes it

        Any failure before (5): the products are discarded and CUST_ORD becomes
        CANCELLED. No distributed transaction, no broker, no saga framework.

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
   **`cust_acct_prod_invl` is written ONLY by account-service.** That promise is now
   discharged in both directions (ADR-013 §7/§8): `GET /api/accounts/{n}/product-ids`
   is the projection's single public *reading* point (product-service's FR-PROD-01
   composition), and `POST /api/accounts/{n}/product-involvements` its single
   *writing* point (order-service's commit point) — bulk, idempotent per
   (account, product), and refused on a Passive account (409 `MSG-ACCT-NOT-ACTIVE`,
   the server-side half of AC-SALE-02-01). No other service writes `account_db`, and
   **no involvement-delete command exists**: nothing in FR-SALE or KR-7 removes a
   product from an account. The representation also gained `customerNumber`
   (ADR-013 §3.6) — a public business number, additive.
7. **Products (read-only slice, 2026-07-29):** `product-service` (port 8086,
   `product_db`) owns the ten PROD/catalog workbook tables. `PROD` deliberately
   has **no customer or account column**: the product ↔ billing-account link
   lives only in `account_db.cust_acct_prod_invl`, so `GET /api/products` is a
   **composition** over account-service's `GET /api/accounts/{n}/product-ids`
   (direct via Eureka with the user's token, ADR-010 — never through the gateway,
   never a direct `account_db` read/write). `prod.service_address_id` is an
   FK-less reference into `customer_db`, resolved through customer-service's
   internal `GET /api/addresses/{addressId}`. Service type is derived through
   `PROD_SPEC.service_type_id` (central GNL_TP); the public campaign identifier
   is `cmpg.campaign_code`, never the internal id. No lookup HTTP client exists
   here — the slice is read-only, so only the `LookupContract` constants are used
   (ADR-002 otherwise unchanged: no local catalog tables or seeds). Upstream
   unavailability fails closed (503 `MSG-SERVICE-UNAVAILABLE`).
8. **Sale orchestration (ADR-015/016, 2026-08-02):** `order-service` (port 8087,
   `order_db`) owns `bsn_inter`, `cust_ord`, `cust_ord_item` and `order_number_seq`,
   and is the only writer of them. The sale spans **three databases with no
   distributed transaction**, so it is ordered around a single **commit point**:
   local order write (MIDLWARE) → products created **PNDG** in product-service →
   product ids + amount snapshots attached locally → products confirmed to ACTV →
   **account-service's involvement command**, which is what makes the sale visible
   (FR-PROD-01 is involvement-driven). Every failure *before* the commit point
   discards the never-committed products and marks the order `CANCELLED`; nothing
   follows it, so nothing ever needs undoing — which is why no involvement-delete
   command exists (ADR-013 §8.6). All hops are synchronous REST via Eureka with the
   user's token (ADR-010); **there is no message broker and none is being added**.
   Transport-level retries are disabled: `POST /api/products` is not idempotent
   (ADR-016 §5.3b).
   **Basket and characteristic validation live in product-service** (ADR-015 §6) —
   they are `PROD_OFR`/`PROD_SPEC` questions; order-service forwards the basket
   verbatim and relays the upstream's `MSG-SALE-*` / `MSG-VAL-CHAR-*` keys unchanged.
   **No basket is ever persisted** (AC-SALE-01-16 holds by construction), and
   `KR-12` (order number) is a **project-proposed** rule awaiting analyst sign-off.
9. **Future domains** (see roadmap below): own services + own databases per the seed
   workbook. Until they exist, cross-domain behaviour is an explicit 501 or a
   documented no-op TODO — never silently faked.
10. **Ports:** 8888 config, 8761 eureka, 8080 gateway (BFF), 8180 keycloak,
    8082 customer, 8083 lookup, 8084 mernis-stub, 8085 account, 8086 product,
    8087 order (8082–8087 host-visible only in the IDE-run topology; compose
    keeps them internal).

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
| product-service | ✅ **Implemented — read slice (2026-07-29) + FR-SALE write slice (2026-08-02, ADR-015)** | **Read side:** FR-PROD-01..02 §2.6 + read-only catalog: `product_db` (prod_spec/prod_ofr/cmpg/cmpg_prod_ofr/prod/prod_spec_char/prod_spec_char_use/prod_char_val/prod_catal/prod_catal_prod_ofr), port 8086, gateway routes `/api/products/**`, `/api/offers/**`, `/api/campaigns/**`, zero-trust resource server, Swagger in the unified gateway UI. `GET /api/products` composes over account-service's `product-ids` endpoint — `PROD` carries NO account/customer column and this service never touches `account_db` (ADR-013 §5/§7). **Write side (2026-08-02, ADR-015 §5/§6):** `POST /api/products` creates a whole installation as **PNDG** in one local transaction (main/child derived from the INTERNET service type), `/confirm` promotes it to ACTV, `/cancel` is the PNDG-only compensation; `GET /api/offers/{id}/characteristics` serves the Product Configuration schema. The full `LookupCatalogClient` boundary was built **before** the first write (ADR-015 §4.1 — ADR-002 fail-closed). Basket composition (AC-SALE-01-05/08) and characteristic validation (AC-SALE-01-18/19) live here, not in order-service. PNDG rows are invisible to FR-PROD-01/02. Testcontainers IT (`ProductServiceIntegrationTest`, 24 tests) + `CharacteristicValidationRulesTest`, `ProductBusinessRulesTest`. **The ADR debt is discharged** — see `docs/api/product-service.md` |
| order-service | ✅ **Implemented (2026-08-02)** | FR-SALE-01..02 §2.7 per **ADR-016**: `order_db` (bsn_inter/cust_ord/cust_ord_item/order_number_seq), port 8087, gateway route `/api/orders/**`, zero-trust resource server, Swagger in the unified gateway UI. Two endpoints only — Submit Order and order detail; **no basket table, no order-cancel endpoint, no order list**. Orchestrates across product-service and account-service with compensations instead of a distributed transaction. Unit (`OrderNumberFormatTest`, `LuhnCheckDigitTest`) + Testcontainers IT (`OrderServiceIntegrationTest`, 15 tests covering every compensation path). **KR-12 awaits analyst sign-off** |

Planned ≠ approved: the localization ownership above is the team's working assumption
from the seed workbook; analyst or architecture sign-off is still missing and must
happen before it is created. The account, product and order boundaries are now
architecture-approved (**ADR-013/014/015/016**) — but two of their *contents* still
await the analyst: the invented offer prices (document-delta P1/P5) and **KR-12**
(ADR-016 §4).
