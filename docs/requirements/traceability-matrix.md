# Traceability Matrix — customer-service scope

FR/AC → implementation → automated test. Tests live in
`backend/customer-service/src/test/java` unless noted.
`IT` = `CustomerServiceIntegrationTest` (Testcontainers, real PostgreSQL + HTTP).

| Requirement | Implementation | Test |
|---|---|---|
| AC-CUST-01-01/02 (≥1 criterion) | `CustomerBusinessRules.checkAtLeastOneSearchCriterionExists` | `CustomerBusinessRulesTest`, IT `searchWithoutCriteriaRejected` |
| AC-CUST-01-03 / KR-01 word-start names | `CustomerSpecifications.wordStart` over First+Middle / Last | IT `searchWordStartSemantics`, IT `updateNationalityIdUniqueness` (post-update regression) |
| AC-CUST-01-03 GSM prefix | `CustomerSpecifications` contact join | IT `searchByGsmPrefix` |
| AC-CUST-01-03/04 exact + OR groups, customer-based distinct results | exact predicates, to-one joins (no fan-out) | IT `searchByExactCriteria` |
| AC-CUST-01-05 role display | `CustomerMapper` → `ROLE.role_name` | IT `createPersistsFullAggregate` ("Customer") |
| AC-CUST-01-07 numeric-only params | `@Pattern` on request params + type-mismatch handler | `GlobalExceptionHandlerTest` |
| AC-CUST-01-08 only active customers | `status_id = ACTV AND deleted_date IS NULL` (local) | IT `searchWordStartSemantics` (Caner invisible), `seedDataLoaded` |
| AC-CUST-01-09 / KR-04 paging + sort | `PageRequest` 20, firstName→lastName(+number tiebreak) | covered by search ITs (order asserted implicitly via single results) |
| AC-CUST-03-02..12 demographic validation | `DemographicRequest` (VR-NAME/VR-NATID), rules (birthdate/age) | `CustomerBusinessRulesTest`, IT Turkish-name create |
| AC-CUST-03-11 + ADR-003 NAT ID unique (global, permanent) | DB UNIQUE (all rows) + `IndividualRepository.existsByNationalityId` | IT `nationalityIdOfSoftDeletedCustomerStaysReserved`, `CustomerBusinessRulesTest` |
| AC-CUST-03-16..20 contact validation | `ContactMediumRequest` VR patterns | IT `contactMediumUpdate` (invalid mobile → 400) |
| AC-CUST-03-21 atomic aggregate create | `CustomerServiceImpl.create` (single `@Transactional`) | IT `createPersistsFullAggregate`, all rollback ITs |
| KR-10 MERNIS verify / fail closed | `MernisClient` before persist | IT `mernisRejectionLeavesNoPartialData`, `mernisUnavailableFailsClosed`; `MernisStubIntegrationTest` (mernis-stub module) |
| AC-CUST-04-04 update uniqueness excl. self | `existsByNationalityIdAndIdNot` | IT `updateNationalityIdUniqueness` |
| AC-CUST-05-04 aggregate soft delete + metadata | `CustomerServiceImpl.delete` + `passivate()` | IT `softDeletePassivatesAggregate` |
| AC-ADDR-02-01/04 cascading district, first=primary | `AddressBusinessRules`, `AddressServiceImpl.add` | `AddressBusinessRulesTest`, IT `districtMustBelongToCity`, `addressPrimaryInvariants` |
| AC-ADDR-04-01/02/03 delete guards | last/primary guards + soft delete | IT `addressPrimaryInvariants`, `AddressBusinessRulesTest` |
| AC-ADDR-05-01/02 primary switching | `AddressServiceImpl.setPrimary` + partial unique index | IT `addressPrimaryInvariants` |
| FR-CNTC-02 contact update | `ContactMediumServiceImpl.update` | IT `contactMediumUpdate` |
| ADR-002 no local GNL tables / no cross-DB FK | Flyway V1 (no gnl_*), external `*_id` columns | IT `schemaContainsNoLocalCatalogTables` |
| ADR-002 catalog validation / wrong domain / unavailable | `LookupCatalogService` | `LookupCatalogServiceTest`, IT `catalogUnavailableFailsClosed`, `unknownCatalogCodeRejected` |
| ADR-002 contract IDs seeded centrally | lookup-service Flyway V2 | `LookupServiceIntegrationTest` (lookup-service module) |
| Duplicate DB constraint → 409, never 500 | `DataIntegrityViolationException` handler | `GlobalExceptionHandlerTest` |

## Known document discrepancies (recorded, not silently resolved)

1. **Active-only NAT ID wording** in use-cases step 4.4 and draw.io FR-CUST-03 —
   superseded by ADR-003 / analyst decision of 2026-07-10.
2. **"İçinde-geçen" (contains) matching note** on the draw.io FR-CUST-01 page —
   contradicts KR-01/AC-CUST-01-03 (word-start); KR-01 governs.
3. **MERNIS step absent** from the use-case document — KR-10 (Final FR decision table) governs.
