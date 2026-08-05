# Traceability Matrix — customer-service + authentication + account-service + product-service scope

Last updated: 2026-07-29 (**product-service implemented — read-only FR-PROD-01..02
slice**, plus account-service's `product-ids` read endpoint and customer-service's
internal address-resolution endpoint; deviations in
[document-delta.md](document-delta.md)). Prior: 2026-07-23 (**account-service
implemented** — FR-ACCT-01..04 + KR-11 per ADR-013/014). Prior: 2026-07-18
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
| **AC-CUST-01-03 / KR-01 Account & Order Number exact match** | `CustomerSpecifications.addChildRecordCriterion` — equality on `cust.customer_number` after the owning service resolved the number | IT `searchByAccountNumberReturnsOwningCustomer`, `searchByOrderNumberReturnsOwningCustomer`, `childRecordNumbersKeepLeadingZeros` |
| **AC-CUST-01-04 / KR-02 child record ⇒ owning customer, ONE row** | `CustomerServiceImpl.resolveAccountNumber` / `resolveOrderNumber` via `AccountServiceClient` / `OrderServiceClient` (ADR-013 §5, ADR-016 §3.2); resolution to a single customer number means no join and no fan-out | IT `duplicateChildRecordMatchesYieldOneCustomerRow` (totalElements = 1), `childRecordCriteriaJoinTheExistingOrExpression` |
| **KR-02 only LIVE child records resolve** | `AccountSummary.isActive` ("Active" — FR-ACCT-04 passivation is account-service's soft delete) / `OrderSummary.isInProgress` ("MIDLWARE" — CANCELLED is a compensated sale, ADR-016 §6) | IT `inactiveChildRecordsProduceNoResult`, `childRecordOfDeletedCustomerProducesNoResult` |
| **KR-02 unresolved number matches nothing (never browse)** | `ChildRecordCriterion.unmatched()` ⇒ `cb.disjunction()` (FALSE), deliberately not a dropped criterion | IT `unknownChildRecordNumberMatchesNothing` |
| **KR-02 fail closed on an owning-service outage** | `AccountServiceUnavailableException` / `OrderServiceUnavailableException` ⇒ 503 `MSG-SERVICE-UNAVAILABLE` | IT `childRecordOwnerOutageFailsClosed` |
| **KR-02 service-to-service auth (ADR-009/010)** | `accountRestClient` / `orderRestClient` with `BearerTokenPropagationInterceptor` — the user's token, no client credentials | UT `OutboundBearerPropagationTest.childRecordOwnerClientsPropagateBearer`; IT `directCallWithoutTokenIsUnauthorized` (unchanged) |
| AC-CUST-01-05 role display | `CustomerMapper` → `ROLE.role_name` | IT `createPersistsFullAggregate` ("Customer") |
| AC-CUST-01-07 numeric-only params (incl. Account & Order Number) | `@Pattern` on request params + type-mismatch handler; rejected at the controller, before any outbound call | `GlobalExceptionHandlerTest`, IT `nonNumericChildRecordNumbersAreRejectedByTheBackend` |
| AC-CUST-01-08 only active customers | `status_id = ACTV AND deleted_date IS NULL` (local) | IT `browseWithoutCriteriaListsAllActiveCustomers` (1003 invisible), `searchWordStartSemantics`, `seedDataLoaded` |
| AC-CUST-01-09 / KR-04 server-side paging + firstName→lastName sort | `PageRequest` + stable customerNumber tiebreak | IT `browseIsPaginated`, `browseWithoutCriteriaListsAllActiveCustomers` (order asserted) |
| KR-04 page size: default 15, only 15/30/50 (ADR-005 §Amendment) | `@AllowedPageSize` + `@Min(0)` on `GET /api/customers` | UT `AllowedPageSizeValidatorTest`, IT `browseIsPaginated`, `rejectsInvalidPaginationParameters` |
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
| ADR-013 §5 involvement read side (**NEW 2026-07-29**) | `GET /api/accounts/{n}/product-ids` → `200 [Long]`; non-deleted rows, involvement status NOT filtered (the ACTV-only filter stays exclusive to the AC-ACCT-04-03 guard); unknown number and the K-8 223 → 404 `MSG-ACCT-NOT-FOUND` | ACCT-IT `productIdsListsNonDeletedInvolvements`, `productIdsHidesUnknownAnd223` |

## Implemented (product-service — 2026-07-29, read-only FR-PROD-01..02 slice)

`PROD-IT` = `ProductServiceIntegrationTest` (product-service module; Testcontainers,
real PostgreSQL + HTTP, real crm-security-starter chain; the account-service and
customer-service clients are mocked at their interfaces only).

| Requirement | Implementation | Test |
|---|---|---|
| FR-PROD-01 list products of a billing account | `GET /api/products?accountNumber=` composing over account-service's `product-ids` (ADR-013 §5) + local `product_db` join; **no pagination** (FR defines none) | PROD-IT `listProductsOfAccount` |
| AC-PROD-01-01 expandable per-account sub-table | backend contract only (expand/collapse is UI); rows keyed by the account number | PROD-IT `listProductsOfAccount` |
| AC-PROD-01-02 no products → `MSG-PROD-NONE` | `200 []`; the info text is frontend-only, the key is never produced by the backend | PROD-IT `listEmptyAccount` |
| AC-PROD-01-03 columns Product ID/Name/Campaign Name/Campaign ID/Status | `ProductRowResponse`; `campaignId` = **public** `cmpg.campaign_code`; campaign-less product → `null` fields (`"-"` is UI); Status from `PROD.status_id`, both Active and Passive listed | PROD-IT `listProductsOfAccount` (campaign branch, passive fixture, response keys) |
| AC-PROD-01-04 Action column = view only | no `Action` field in the API; no cancellation endpoint exists | PROD-IT `listProductsOfAccount` (response keys asserted) |
| FR-PROD-02 / AC-PROD-02-01 detail modal (Offer Name, Offer ID, Spec ID, Campaign, Service Address) | `GET /api/products/{id}` → `ProductDetailResponse`; **child product shows its parent's** service address (`ProductBusinessRules.resolveEffectiveServiceAddressId`), resolved via customer-service | PROD-IT `detailOfChildProductShowsParentAddress`, `detailCampaignlessAndPassiveProducts` |
| Unknown product → 404 `MSG-PROD-NOT-FOUND` (documented **project addition**) | `ProductServiceImpl.getById`; non-numeric id → 400 | PROD-IT `detailValidation` |
| Service type derived through the spec | `PROD_SPEC.service_type_id` → GNL_TP 10/11/12 via `LookupContract.serviceTypeCode` (offers have no service-type column) | PROD-IT `offersCatalog`, `campaignsCatalog` |
| Read-only catalog (Offer Selection support for §2.7) | `GET /api/offers`, `GET /api/campaigns`; campaign `totalPrice` **derived** from member offers, `CMPG` stores no price | PROD-IT `offersCatalog`, `campaignsCatalog` |
| ADR-002 in product_db (no gnl tables/seeds/FKs) | V1 schema; central GNL_ST/GNL_TP IDs as external refs; **no lookup HTTP client** (read-only slice) | PROD-IT `schemaContainsOnlyProductTables` |
| ADR-013 §5 — `PROD` has no account/customer column | V1 schema; the account link is read exclusively through account-service's API | PROD-IT `prodHasNoAccountColumns` |
| Fail closed on upstream outage | account-service or customer-service unreachable → 503 `MSG-SERVICE-UNAVAILABLE` | PROD-IT `listFailsClosedWhenAccountServiceDown`, `detailFailsClosedWhenCustomerServiceDown` |
| ADR-009 zero trust | starter chain (401/403; only `/actuator/health` + `/v3/api-docs/**` anonymous) | PROD-IT `securityChecks` |
| Internal address resolution (customer-service, **NEW**) | `GET /api/addresses/{addressId}` → active address; soft-deleted/unknown → 404 `MSG-CUST-NOT-FOUND`; not gateway-routed | IT `internalAddressResolution` |

## Implemented (FR-SALE §2.7 — 2026-08-02, ADR-015/016)

`ORD-IT` = `OrderServiceIntegrationTest` (order-service; Testcontainers, real
PostgreSQL + HTTP, real crm-security-starter chain, real orchestration/KR-12
generator/persistence — the lookup, account and product clients are mocked at their
interfaces only, which is what makes "step 4 fails" testable at all).
`PROD-IT` = `ProductServiceIntegrationTest`, `ACCT-IT` = `AccountServiceIntegrationTest`.

| Requirement | Implementation | Test |
|---|---|---|
| FR-SALE-01 / AC-SALE-01-15 Submit Order | `POST /api/orders` — one atomic command carrying the whole basket; order created `MIDLWARE`, the status AC-SALE-01-15 describes and the only one a user sees | ORD-IT `submitCreatesOrder` |
| AC-SALE-01-01 products linked to the originating Billing Account | the orchestration's **commit point**: account-service's involvement command (ADR-013 §8) | ORD-IT `submitCreatesOrder`, ACCT-IT `involvementWriteLinksProducts` |
| FR-SALE-02 / AC-SALE-02-01 only an **Active** account may be sold into | enforced twice server-side: precondition (step 0) and again by account-service | ORD-IT `preconditionsRejectBeforeWriting`, `involvementWriteRefusedSurfacesAsConflict`; ACCT-IT `involvementWriteRejections` |
| AC-SALE-01-03/04/06/07/13/14 basket assembly, LBL-CLEAR/NEXT/PREVIOUS | **frontend/session state — no backend representation** (ADR-016 §2.6); the backend learns of the sale once, at Submit | — (by design) |
| AC-SALE-01-05 duplicate offer → `MSG-SALE-DUP-OFFER` | `BasketValidationRules.checkNoDuplicateOffers` (product-service) | PROD-IT `basketCompositionRules` |
| AC-SALE-01-08 all offers Active + exactly one INTERNET/RESOURCE/ACTIVATION | `BasketValidationRules` in **product-service** (ADR-015 §6 — only it knows offer status and service type); order-service relays the key unchanged | PROD-IT `basketCompositionRules`; ORD-IT `basketRejectionIsRelayedVerbatim` |
| AC-SALE-01-09 internet offer = main product, others its children | **derived from the service type**, never declared by the caller; only the main row stores `service_address_id` | PROD-IT `createProductsAsPending` |
| AC-SALE-01-10/17/20 characteristic fields per product | `GET /api/offers/{id}/characteristics` (name, dataType, mandatory) reached through the offer's spec | PROD-IT `offerCharacteristicSchema` |
| AC-SALE-01-11 service address for the main product | `serviceAddressId` in the request → the main `PROD` row only (children resolve it through the parent). **Validated to belong to the customer** who owns the billing account (ADR-015 §5.9) — the FR describes a picker, not a check, but an unvalidated id would let a sale attach another customer's address and FR-PROD-02 would display it | PROD-IT `createProductsAsPending`, `serviceAddressMustBelongToTheCustomer`, `createFailsClosedWhenCustomerServiceDown`; ORD-IT `submitCreatesOrder` (proves the customer number is taken from the account, not the request body) |
| AC-SALE-01-12 Total Amount + per-offer amounts | **snapshot** columns `cust_ord.total_amount` / `cust_ord_item.amount` (project addition, ADR-016 §2.4); total derived at creation | ORD-IT `submitCreatesOrder` |
| AC-SALE-01-12 "Order ID shown on the Submit screen" | shown **after** submission, from the 201 (ADR-016 §8.3) — reserving a number earlier would burn KR-12 values on abandoned sales and create the backend trace AC-SALE-01-16 forbids. **Flagged for analysts** | ORD-IT `submitCreatesOrder` |
| AC-SALE-01-16 an abandoned sale is never processed later | true **by construction**: no basket is ever persisted, so there is nothing to abandon | — (by design) |
| AC-SALE-01-18 mandatory characteristic blank → `MSG-VAL-CHAR-REQUIRED` | `CharacteristicValidationRules` (product-service) | PROD-IT `characteristicValidationRules`, `CharacteristicValidationRulesTest` |
| AC-SALE-01-19 value must match its data type → `MSG-VAL-CHAR-FORMAT` | same; BOOLEAN is strict and an unknown `data_type` is rejected rather than waved through | `CharacteristicValidationRulesTest` |
| AC-SALE-01-21 offer with no characteristics | `200 []` — a valid answer, not a 404; the block renders without fields | PROD-IT `offerCharacteristicSchema` |
| KR-12 Order Number (**project-proposed**, analyst sign-off pending) | `[T][YY][SSSSSS][C]`, Luhn, `order_number_seq`; immutable, never reused — including after a compensated sale | `OrderNumberFormatTest`, `LuhnCheckDigitTest`, ORD-IT `sequenceIsGaplessAcrossCompensations` |
| Sale orchestration across three databases without a distributed transaction | ADR-016 §5: local order write → products PNDG → attach → confirm → **involvement (commit point)**; every earlier failure compensated | ORD-IT `basketRejectionIsRelayedVerbatim`, `productServiceUnavailableAtCreate`, `confirmFailureCompensates`, `involvementWriteUnavailableCompensates`, `involvementWriteRefusedSurfacesAsConflict` |
| GNL_ST `CANCELLED` used only by system compensation; `WAIT` never used | ADR-016 §6; no user-facing cancel endpoint (KR-7 out of phase) | ORD-IT `cancelledOrderKeepsItsNumberAndStaysVisible` |
| ADR-002 in order_db (no gnl tables/seeds/FKs) + fail closed | V1 schema; catalog outage → 503, **nothing persisted** (statuses resolve before any write) | ORD-IT `schemaContainsOnlyOrderTables`, `writeFailsClosedWhenCatalogUnavailable` |
| ADR-009 zero trust | starter chain (401/403; only `/actuator/health` + `/v3/api-docs/**` anonymous) | ORD-IT `securityChecks` |

## Removed by the 16.07.2026 revision

| Item | Was | Now |
|---|---|---|
| Mandatory search criteria (old AC-CUST-01-01/02 API reading) | `checkAtLeastOneSearchCriterionExists` → 400 `MSG-SEARCH-CRITERIA-REQUIRED` | Rule deleted (ADR-005); criterion-less request = browse mode. LBL-SEARCH enable/disable stays a frontend rule |
| `MSG-NATID-VERIFY-FAILED` (project-specific key) | 400 on MERNIS rejection | Renamed to analyst key `MSG-CUST-NATID-VERIFICATION-FAILED` |
| `MSG-SERVICE-UNAVAILABLE` for MERNIS outages | 503 shared with catalog outages | MERNIS outages now `MSG-MERNIS-UNAVAILABLE`; `MSG-SERVICE-UNAVAILABLE` remains for lookup-catalog outages only |

## Deferred (future domains — documented TODOs, never silent)

| Requirement | Owner (planned) | Current behaviour |
|---|---|---|
| ~~KR-02 `accountNumber` search resolution~~ | — | ✅ **implemented 2026-08-05** — customer-service → account-service `GET /api/accounts/{n}`; see the Implemented section above |
| ~~KR-02 `orderNumber` search resolution~~ | — | ✅ **implemented 2026-08-05** — customer-service → order-service `GET /api/orders/{n}` |
| ~~AC-CUST-05-03 active-product delete guard~~ | — | ✅ **enforced 2026-08-05** — not by the upfront `checkCustomerHasNoActiveProducts` (still a no-op) but one layer deeper: a 409 from account-service's delete becomes 409 `MSG-CUST-HAS-PRODUCTS` |
| ~~AC-CUST-05-04 billing-account passivation on customer delete~~ | — | ✅ **implemented 2026-08-05** — account-service `GET /api/accounts?customerId=` + `DELETE /api/accounts/{n}` for every Active row, run BEFORE the local passivation |
| AC-ADDR-04-04 address in-use check (`MSG-ADDR-IN-USE`) | customer-service → account-service (**still open**; `cust_acct.address_id` now exists) | documented no-op |
| Product involvement **population** (`cust_acct_prod_invl` writes) | future order/sale flow via an account-service command/API or consumed event — **never direct account_db writes** (ADR-013 §5). The **read** side now exists (`product-ids`, see above) | seed/test rows only (V2 + V3); real, queried guard state |
| AC-AUTH-01-02/06/07/08/09 login-page UI details (button state, masking, 64-char cap) + LBL-LANGUAGE on the login screen | Keycloak **project theme** (future work) | standard Keycloak login page + built-in EN/TR i18n serve the flow today |
| ~~FR-ACCT-01..04 + KR-11~~ | — | ✅ **implemented 2026-07-23** (ADR-013/014) — see the account-service section above; this row was a stale leftover |
| ~~FR-PROD-01..02 (PROD_*, CMPG*, PROD_CATAL*)~~ | — | ✅ **implemented 2026-07-29** (read-only slice) — see the product-service section above |
| ~~FR-PROD product **write** side (creation/provisioning, characteristic values)~~ | — | ✅ **implemented 2026-08-02** (ADR-015 §5/§6): `POST /api/products` creates the installation as PNDG, `/confirm` and `/cancel` drive its lifecycle, `GET /api/offers/{id}/characteristics` serves the Product Configuration schema |
| ~~FR-SALE-01..02 (BSN_INTER, CUST_ORD, CUST_ORD_ITEM, basket validation MSG-SALE-*)~~ | — | ✅ **implemented 2026-08-02** (ADR-016): `order-service` (:8087, `order_db`) — see the FR-SALE section above |
| Product involvement **removal** | nobody — deliberately does not exist | KR-7 leaves product cancellation out of phase; no API removes a product from an account (ADR-013 §8.6) |
| Transfer / service-address-change flows (GNL_TP `TRANSFER`/`CANCEL`) | not assigned | not implemented — **no FR/AC covers them** although the mock UI offers them as account-row actions (ADR-016 §8.2). Analyst question, not a build task |
| FR-LANG-01 (TR/EN catalogs, **default EN**, AC-LANG-01-01, unchanged since 16.07.2026 through v8-2) | frontend + planned localization capability | backend returns language-neutral `messageKey`s |

## Known document conflicts (recorded, not silently resolved)

1. **Active-only NAT ID wording** in use-cases alternative step 4.5 — superseded by
   FR AC-CUST-03-12 (no qualifier) + ADR-003; conflict remains in the source document.
2. **"İçinde-geçen" (contains) matching note** on the draw.io FR-CUST-01 page —
   contradicts KR-01/AC-CUST-01-03 (word-start); KR-01 governs.
3. ~~**KR-04 default page size 15 (UI) vs API default 20 (ADR-005)**~~ — **CLOSED
   2026-07-29** via BUG-API-CUST-01-14/-16/-17/-18/-19: API default is 15 and only
   15/30/50 are accepted (400 otherwise, previously 500 for `size=0`/`page=-1`).
   ADR-005 §Amendment.
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
   list** ("aktif hesap listesinden kaldırılması" / Adım 5) — contradicts FR v8-2
   AC-ACCT-04-02 (deletion = passivation, stays visible as Passive, unchanged since
   v8-1). FR v8-2 governs; use-case wording not updated by this revision.
8. **draw.io ACCT-04 node still labeled "Hesabı aktif listeden kaldır"** — same
   conflict as #7; FR v8-2 AC-ACCT-04-02 governs.
9. **Entity/seed workbook `CUST_ACCT` sample `account_number` values don't satisfy
   KR-11** (`0101112900`, `0101112911`, `0101112915`, `0101112441` — wrong segment
   digit, no check digit) — flagged for analysts; account-service's real seed must
   use the KR-11 format instead.
