# Traceability Matrix — customer-service scope

Last updated: 2026-07-16 (FR/AC v8 Final 16.07.2026 revision — see
[document-delta.md](document-delta.md)).
FR/AC → implementation → automated test. Tests live in
`backend/customer-service/src/test/java` unless noted.
`IT` = `CustomerServiceIntegrationTest` (Testcontainers, real PostgreSQL + HTTP).

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
| AC-CUST-03-12 + ADR-003 NAT ID unique (global, permanent) | DB UNIQUE (all rows) + `IndividualRepository.existsByNationalityId` | IT `nationalityIdOfSoftDeletedCustomerStaysReserved`, `CustomerBusinessRulesTest` |
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

## Removed by the 16.07.2026 revision

| Item | Was | Now |
|---|---|---|
| Mandatory search criteria (old AC-CUST-01-01/02 API reading) | `checkAtLeastOneSearchCriterionExists` → 400 `MSG-SEARCH-CRITERIA-REQUIRED` | Rule deleted (ADR-005); criterion-less request = browse mode. LBL-SEARCH enable/disable stays a frontend rule |
| `MSG-NATID-VERIFY-FAILED` (project-specific key) | 400 on MERNIS rejection | Renamed to analyst key `MSG-CUST-NATID-VERIFICATION-FAILED` |
| `MSG-SERVICE-UNAVAILABLE` for MERNIS outages | 503 shared with catalog outages | MERNIS outages now `MSG-MERNIS-UNAVAILABLE`; `MSG-SERVICE-UNAVAILABLE` remains for lookup-catalog outages only |

## Deferred (future domains — documented TODOs, never silent)

| Requirement | Owner (planned) | Current behaviour |
|---|---|---|
| KR-02 `accountNumber`/`orderNumber` search resolution | account-service / order-service | 501 `MSG-FEATURE-NOT-IMPLEMENTED` |
| AC-CUST-05-03 active-product delete guard | product/account domains | documented no-op |
| AC-CUST-05-04 billing-account passivation | account-service | documented no-op (local aggregate is passivated) |
| AC-ADDR-04-04 address in-use check (`MSG-ADDR-IN-USE`) | account/order domains | documented no-op |
| FR-AUTH-01/02, KR-8, KR-9 | **next milestone** — authentication/security architecture (ADR-004 direction) | gateway `permitAll`, auth-service skeleton |
| FR-ACCT-01..04 (ACCT_TP, CUST_ACCT; auto Customer Account on first billing account) | planned account-service | not implemented |
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
