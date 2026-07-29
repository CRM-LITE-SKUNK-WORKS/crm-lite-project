# Traceability Matrix — customer-service + authentication + account-service scope

Last updated: 2026-07-23 (**account-service implemented** — FR-ACCT-01..04 + KR-11
per ADR-013/014; the same date's earlier documentation-only v8-1 reconciliation is
in [document-delta.md](document-delta.md)). Prior: 2026-07-18
(authentication/security milestone, ADR-006..011).
FR/AC → implementation → automated test. Tests live in
`backend/customer-service/src/test/java` unless noted.
`IT` = `CustomerServiceIntegrationTest` (Testcontainers, real PostgreSQL + HTTP).
`GW-IT` = `GatewayBffIntegrationTest` (api-gateway module; real Keycloak via
Testcontainers, committed realm import).
`ACCT-IT` = `AccountServiceIntegrationTest` (account-service module; Testcontainers,
real PostgreSQL + HTTP, real crm-security-starter chain).

## Implemented

| Requirement | Implementation | Test |
|---|---|---|
| **AC-CUST-01-00 browse all active customers, A-Z (NEW)** | ADR-005: criterion-less `GET /api/customers`, `CustomerSpecifications` browse mode | IT `browseWithoutCriteriaListsAllActiveCustomers`, `browseIsPaginated` |
| ADR-005 list rows = full detail contract | `Page<CustomerDetailResponse>`; `CustomerSearchResponse` deleted | IT `listRowsExposeFullDetailContract` |
| AC-CUST-01-03 / KR-01 word-start names | `CustomerSpecifications.wordStart` over First+Middle / Last | IT `searchWordStartSemantics`, IT `updateNationalityIdUniqueness` (post-update regression) |
| AC-CUST-01-03 GSM prefix | `CustomerSpecifications` contact join | IT `searchByGsmPrefix` |
| AC-CUST-01-03/04 exact + OR groups, customer-based distinct results | exact predicates, to-one joins (no fan-out) | IT `searchByExactCriteria` |
| AC-CUST-01-05 role display | `CustomerMapper` → `ROLE.role_name` | IT `createPersistsFullAggregate` ("Customer") |
| AC-CUST-01-07 numeric-only params | `@Pattern` on request params + type-mismatch handler | `GlobalExceptionHandlerTest` |
| AC-CUST-01-08 only active customers | `status_id = ACTV AND deleted_date IS NULL` (local) | IT `browseWithoutCriteriaListsAllActiveCustomers` (1003 invisible), `searchWordStartSemantics`, `seedDataLoaded` |
| AC-CUST-01-09 / KR-04 server-side paging + firstName→lastName sort | `PageRequest` + stable customerNumber tiebreak | IT `browseIsPaginated`, `browseWithoutCriteriaListsAllActiveCustomers` (order asserted) |
| AC-CUST-03-02..13 demographic validation | `DemographicRequest` (VR-NAME/VR-NATID), rules (birthdate/age) | `CustomerBusinessRulesTest`, IT Turkish-name create |
| AC-CUST-03-06 / KR-10 MERNIS keys | `MSG-CUST-NATID-VERIFICATION-FAILED` (400), `MSG-MERNIS-UNAVAILABLE` (503), fail closed pre-persist | IT `mernisRejectionLeavesNoPartialData`, `mernisUnavailableFailsClosed`; `GlobalExceptionHandlerTest`; `MernisStubIntegrationTest` (mernis-stub module) |
| AC-CUST-03-12 + ADR-003 NAT ID unique (global, permanent) | DB UNIQUE (all rows) + `IndividualRepository.existsByNationalityId`; surfaced pre-submit by `GET /api/customers/nationality-id-availability` (ADR-005 §Addendum) | IT `nationalityIdOfSoftDeletedCustomerStaysReserved`, `nationalityIdAvailabilityCoversSoftDeletedHolders`, `nationalityIdAvailabilityDoesNotShadowDetail`, `CustomerBusinessRulesTest` |
| AC-CUST-03-17..21 contact validation | `ContactMediumRequest` VR patterns | IT `contactMediumUpdate` (invalid mobile → 400) |
| AC-CUST-03-22 atomic aggregate create | `CustomerServiceImpl.create` (single `@Transactional`) | IT `createPersistsFullAggregate`, all rollback ITs |
| AC-CUST-04-04 update uniqueness excl. self | `existsByNationalityIdAndIdNot` | IT `updateNationalityIdUniqueness` |
| AC-CUST-05-04 aggregate soft delete + metadata | `CustomerServiceImpl.delete` + `passivate()` | IT `softDeletePassivatesAggregate` |
| AC-ADDR-02-01/04 cascading district, first=primary | `AddressBusinessRules`, `AddressServiceImpl.add` | `AddressBusinessRulesTest`, IT `districtMustBelongToCity`, `addressPrimaryInvariants` |
| AC-ADDR-04-01/02/03 delete guards (backend side of the confirm flow) | last/primary guards + soft delete | IT `addressPrimaryInvariants`, `AddressBusinessRulesTest` |
| AC-ADDR-05-01/02 primary switching | `AddressServiceImpl.setPrimary` + partial unique index | IT `addressPrimaryInvariants` |
| FR-CNTC-02 contact update | `ContactMediumServiceImpl.update` | IT `contactMediumUpdate` |
| No `/api/customers/search` alias (removal stands) | single canonical endpoint | IT `unsupportedAndRemovedEndpoints` |
| ADR-002 no local GNL tables / no cross-DB FK | Flyway V1 (no gnl_*), external `*_id` columns | IT `schemaContainsNoLocalCatalogTables` |
| ADR-002 catalog validation / wrong domain / unavailable | `LookupCatalogService` | `LookupCatalogServiceTest`, IT `catalogUnavailableFailsClosed`, `unknownCatalogCodeRejected` |
| ADR-002 contract IDs seeded centrally | lookup-service Flyway V2 | `LookupServiceIntegrationTest` (lookup-service module) |
| Duplicate DB constraint → 409, never 500 | `DataIntegrityViolationException` handler | `GlobalExceptionHandlerTest` |
| **AC-AUTH-01-01 login → app (Auth Code + PKCE)** | Gateway BFF `oauth2Login`, Keycloak realm `crm-lite` (ADR-006/007) | GW-IT `loginEstablishesSession` (asserts the PKCE `code_challenge` too) |
| AC-AUTH-01-03/05 unknown user / wrong password | Keycloak login page (generic error, no session) | GW-IT `invalidAndDisabledUsersGetNoSession` |
| AC-AUTH-01-04 passive user indistinguishable | Keycloak disabled user (`mkaya` fixture, ADR-011) | GW-IT `invalidAndDisabledUsersGetNoSession` |
| AC-AUTH-02-01/02 logout + back-button | CSRF-protected `POST /logout`, RP-initiated Keycloak logout, dead session ⇒ 401 | GW-IT `logoutEndsLocalAndKeycloakSession` |
| KR-8 single `crm-user` role, explicit on every endpoint | gateway route rules + starter default chain (ADR-009) | GW-IT `tokenRelayPassesBearerAndStripsCookies` (role in relayed JWT); IT `tokenWithoutRoleIsForbidden`; lookup IT `directAccessRequiresRole` |
| KR-9 expiry/refresh (5m token; 30m idle / 24h max are config) | realm import + gateway session timeout; transparent refresh at the relay | GW-IT `expiredAccessTokenIsRefreshed` (shortened-lifespan realm) |
| Zero-trust direct-service protection (ADR-009) | crm-security-starter resource-server chain in customer/lookup | IT `requestWithoutTokenIsRejected`, `malformedTokenRejectedHealthPublic`; lookup IT `directAccessRequiresRole` |
| CSRF on unsafe browser operations (ADR-008) | XSRF-TOKEN/X-XSRF-TOKEN at the gateway only | GW-IT `csrfProtectsUnsafeProxiedRequests` |
| No token exposure to the browser (ADR-007) | server-side token custody + TokenRelay | GW-IT `loginEstablishesSession` (cookie/body assertions), `anonymousHandling` |
| Audit `*_by` = Keycloak `sub`; seeds stay `system` (ADR-004) | `CurrentActorProvider` + starter `JwtAuditorAware` | IT `auditColumnsCarryKeycloakSubject`, `createPersistsFullAggregate`, `softDeletePassivatesAggregate` |
| User-token propagation to lookup; token-free MERNIS (ADR-010) | `HttpClientConfig` interceptor wiring | `OutboundBearerPropagationTest` |
| No USERS/password table anywhere (ADR-011) | no such Flyway migration exists in any service | IT `schemaContainsNoLocalCatalogTables` covers customer_db tables; structural review — no automated dedicated test (workbook conflict recorded below) |

## Implemented (account-service — 2026-07-23, ADR-013/014)

| Requirement | Implementation | Test |
|---|---|---|
| AC-ACCT-01-01 no accounts → no table | `GET /api/accounts?customerId=` → `200 []` (empty state is frontend) | ACCT-IT `listValidation` |
| AC-ACCT-01-02 columns, **224 only** | 224-filtered repository query; `Action` is UI-only, absent from the API | ACCT-IT `seedDataLoaded` (response keys asserted) |
| AC-ACCT-01-03 Active + Passive visible | passivated rows keep serving in list/detail | ACCT-IT `deletePassivates` |
| AC-ACCT-01-04 Active first, then Number ASC | `ORDER BY status_id, account_number` (contract ACTV=1 < PASV=2) | ACCT-IT `deletePassivates` (order asserted), `seedDataLoaded` |
| AC-ACCT-02-02 name+address required; address from the customer's list | bean validation + active-address check via customer-service | ACCT-IT `createValidation`; `AccountBusinessRulesTest` |
| AC-ACCT-02-03 / KR-11 auto unique number | `AccountNumberGenerator` (Clock + upsert sequence + Luhn) | ACCT-IT `firstBillingAccountCreates223`, `sequence*`; `AccountNumberFormatTest`, `LuhnCheckDigitTest` |
| **K-8** lazy 223 in the same transaction | `AccountServiceImpl.create` + V1 partial unique index | ACCT-IT `firstBillingAccountCreates223`, `secondBillingAccountDoesNotDuplicate223`, `createIsAtomicAcrossThe223And224` |
| AC-ACCT-03-01/02 immutable type/number; name+address editable | strict DTOs (`@JsonAnySetter` capture) → 400 `MSG-ACCT-IMMUTABLE-FIELD` | ACCT-IT `updateFlows`; `AccountBusinessRulesTest` |
| AC-ACCT-04-02 delete = passivation, stays visible | `passivate(PASV)` + full audit metadata; never physical | ACCT-IT `deletePassivates`, `passiveAccountMutationsRejected` |
| AC-ACCT-04-03 active product blocks delete | local `cust_acct_prod_invl` guard → 409 `MSG-ACCT-HAS-PRODUCTS` | ACCT-IT `deleteBlockedByActiveProducts`; `AccountBusinessRulesTest` |
| KR-11 permanence (never reused) | UNIQUE over all rows; sequence never rewinds | ACCT-IT `numberNeverReusedAfterPassivation`, `duplicateNumberRaceMapsTo409` |
| ADR-002 in account_db (no gnl tables/FKs) | V1 schema; central IDs as external refs | ACCT-IT `schemaContainsOnlyAccountTables` |
| ADR-009/010 zero trust + token propagation | starter chain; both outbound RestClients propagate the user token | ACCT-IT `securityChecks`; `OutboundBearerPropagationTest` (account-service module) |

## Removed by the 16.07.2026 revision

| Item | Was | Now |
|---|---|---|
| Mandatory search criteria (old AC-CUST-01-01/02 API reading) | `checkAtLeastOneSearchCriterionExists` → 400 `MSG-SEARCH-CRITERIA-REQUIRED` | Rule deleted (ADR-005); criterion-less request = browse mode. LBL-SEARCH enable/disable stays a frontend rule |
| `MSG-NATID-VERIFY-FAILED` (project-specific key) | 400 on MERNIS rejection | Renamed to analyst key `MSG-CUST-NATID-VERIFICATION-FAILED` |
| `MSG-SERVICE-UNAVAILABLE` for MERNIS outages | 503 shared with catalog outages | MERNIS outages now `MSG-MERNIS-UNAVAILABLE`; `MSG-SERVICE-UNAVAILABLE` remains for lookup-catalog outages only |

## Deferred (future domains — documented TODOs, never silent)

| Requirement | Owner (planned) | Current behaviour |
|---|---|---|
| KR-02 `accountNumber` search resolution | customer-service → account-service (**follow-up PR**; account-service exists now, customer-service deliberately untouched this sprint) | 501 `MSG-FEATURE-NOT-IMPLEMENTED` |
| KR-02 `orderNumber` search resolution | order-service (not started) | 501 `MSG-FEATURE-NOT-IMPLEMENTED` |
| AC-CUST-05-03 active-product delete guard | customer-service → account/product (same follow-up PR) | documented no-op |
| AC-CUST-05-04 billing-account passivation on customer delete | customer-service → account-service (same follow-up PR) | documented no-op (local aggregate is passivated) |
| AC-ADDR-04-04 address in-use check (`MSG-ADDR-IN-USE`) | customer-service → account-service (same follow-up PR; `cust_acct.address_id` now exists) | documented no-op |
| Product involvement population (`cust_acct_prod_invl`) | future product/order services via an account-service command/API or consumed event — **never direct account_db writes** (ADR-013 §5) | seed/test rows only; real, queried guard state |
| AC-AUTH-01-02/06/07/08/09 login-page UI details (button state, masking, 64-char cap) + LBL-LANGUAGE on the login screen | Keycloak **project theme** (future work) | standard Keycloak login page + built-in EN/TR i18n serve the flow today |
| FR-ACCT-01..04 + KR-11 (ACCT_TP, CUST_ACCT; Account Number `[T][YY][SSSSSS][C]`; Active+Passive list; delete = passivation) | **planned account-service — next approved Sprint domain**, account-specific ADRs pending | not implemented; contract documented 23.07.2026 (FR v8-1) |
| FR-PROD-01..02 (PROD_*, CMPG*, PROD_CATAL*) | planned product-service | not implemented |
| FR-SALE-01..02 (BSN_INTER, CUST_ORD, CUST_ORD_ITEM, basket validation MSG-SALE-*) | planned order-service | not implemented |
| FR-LANG-01 (TR/EN catalogs, **default EN** per 16.07.2026) | frontend + planned localization capability | backend returns language-neutral `messageKey`s |

## Known document conflicts (recorded, not silently resolved)

1. **Active-only NAT ID wording** in use-cases alternative step 4.5 — superseded by
   FR AC-CUST-03-12 (no qualifier) + ADR-003; conflict remains in the source document.
2. **"İçinde-geçen" (contains) matching note** on the draw.io FR-CUST-01 page —
   contradicts KR-01/AC-CUST-01-03 (word-start); KR-01 governs.
3. **KR-04 default page size 15 (UI) vs API default 20 (ADR-005)** — open item;
   frontend passes `size` explicitly.
4. **Use-case FR-CUST-03 duplicate step number "Adım 4.5"** — editorial defect,
   flagged for analysts.
5. **Workbook USERS table (`username`/`password_hash`) vs Keycloak ownership** —
   the table is deliberately NOT implemented; Keycloak owns credentials and
   enabled/disabled state. Seed usernames live on as Keycloak dev users
   (`mkaya` disabled). Recorded in **ADR-011** (analyst sign-off pending) and
   [document-delta.md](document-delta.md).
6. **FR-AUTH-01 wording assumes an in-app login form** — superseded by
   ADR-006: credentials are entered on the (themable) Keycloak login page; the
   AC-AUTH-01 UI criteria bind that page, not an Angular form.
7. **Use-case doc FR-ACCT-04 still describes deletion as removal from the active
   list** ("aktif hesap listesinden kaldırılması" / Adım 5) — contradicts FR v8-1
   AC-ACCT-04-02 (deletion = passivation, stays visible as Passive). FR v8-1 governs;
   use-case wording not updated by this revision.
8. **draw.io ACCT-04 node still labeled "Hesabı aktif listeden kaldır"** — same
   conflict as #7; FR v8-1 AC-ACCT-04-02 governs.
9. **Entity/seed workbook `CUST_ACCT` sample `account_number` values don't satisfy
   KR-11** (`0101112900`, `0101112911`, `0101112915`, `0101112441` — wrong segment
   digit, no check digit) — flagged for analysts; account-service's real seed must
   use the KR-11 format instead.
