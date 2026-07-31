# Seed Fixture Catalog

Source-of-truth catalog for every Flyway-seeded local dev/demo fixture across
`customer-service`, `account-service` and `product-service`, following the V2/V3
baseline seed (customers 1001-1003, accounts 1261000002/10/28/36, products 1-4,
offers 1-3, campaign CMP-ADSL-01) plus its forward-only fixture expansion:

| Service | Migration | Adds |
|---|---|---|
| customer-service | `V3__expand_customer_test_fixtures.sql` | customers 1004-1011 |
| product-service | `V3__expand_product_test_fixtures.sql` | Fiber family, 2 more ADSL offers, 3 more campaigns, products 5-17 |
| account-service | `V4__expand_account_test_fixtures.sql` | accounts 5-18 (customers 1004-1008, 1011), involvements 5-17 |

lookup-service is untouched — no new shared `GNL_ST`/`GNL_TP` rows were needed.

No previously applied migration (V1/V2 in any service, V3 in account-service) was
modified. This is a forward-only expansion.

---

## Scope notes (read this before using any number below)

- **Prices are project-added development fixtures, not analyst-approved commercial
  tariff data.** They extend the same open conflict already recorded for the V2
  ADSL prices in `docs/requirements/document-delta.md` (#10) — no production
  pricing decision is implied by any value in this document.
- **Expired-campaign behavior is out of scope.** No expired campaign fixtures
  were added; all campaigns use a future `activation_end_date`.
- **Passive-campaign behavior is out of scope.** No new passive campaign
  fixtures were added, and no passive-campaign visibility/cart behavior was
  implemented. Only active, non-deleted campaign listing and the existing
  sales/cart flow are targeted.
- The existing passive-product fixture (`prod` id 4, "ADSL 8MB Legacy") and its
  account-service involvement row (V2/V3) are preserved unchanged and were not
  expanded as part of this scope.
- An IPTV product family was considered and **not added**: the approved
  `SERVICE_TYPE` lookup contract (GNL_TP) only defines INTERNET(10)/RESOURCE(11)/
  ACTIVATION(12), and `LookupContract.serviceTypeCode()` in product-service hard-codes
  exactly those three. Adding IPTV would require a new shared lookup row and a
  production code change, and it does not fit AC-SALE-01-08's three-service-type
  basket rule. It is reported here as blocked pending analyst approval, not built.

---

## Customers (customer-service, `customer_db`)

| Customer # | Synthetic name | State | Scenario | Address / contact notes |
|---|---|---|---|---|
| 1001 | Ali Yildiz | Active | baseline (V2) | 2 addresses (1 primary, 1 secondary); full contact minus fax |
| 1002 | Zeynep Nur Demir | Active | baseline (V2), middle name | 1 address; mobile only |
| 1003 | Caner Sahin | **Soft-deleted** | baseline (V2) — permanently invisible, NAT-ID reserved | 0 active addresses (passivated) |
| 1004 | Ayse Gul Kaya | Active | middle name; **fully populated contact** (email+home+mobile+fax) | 1 address (Istanbul/Kadikoy, primary) |
| 1005 | Mehmet Yilmaz | Active | shared last name with 1006; home_phone/fax NULL | 2 addresses: Istanbul/Besiktas (primary), Ankara/Cankaya (secondary) |
| 1006 | Elif Yilmaz | Active | shared last name with 1005; GSM prefix 0533 (shares with 1002) | 2 addresses: Istanbul/Kadikoy (primary), Istanbul/Uskudar (secondary) |
| 1007 | Ali Kemal Ozturk | Active | middle-name search (`firstName=Kemal`); GSM prefix 0532 | 3 addresses: Istanbul/Kadikoy (primary), Istanbul/Besiktas, Ankara/Cankaya |
| 1008 | Fatma Nur Sahin | Active | middle name `Nur` (2nd match with 1002); shares last name `Sahin` with soft-deleted 1003 | 1 address (Istanbul/Besiktas, primary) |
| 1009 | Burak Demir | Active | **no accounts** | 2 addresses: Istanbul/Kadikoy (primary), **Izmir/Konak** (secondary) |
| 1010 | Selin Aydin | Active | Ankara-only; **no accounts** | 1 address (Ankara/Cankaya, primary) |
| 1011 | Kerem Ali Toprak | Active | `firstName=Ali` matches both 1001 (first name) and 1011 (middle name); GSM prefix 0532 | 1 address (Istanbul/Kadikoy, primary) |

**GSM prefix grouping** (search fixture): `0532` groups 1001/1007/1011 (3 rows);
`0533` groups 1002/1006 (2 rows).

**City/district catalog** (customer-service V2 + V3 fixture expansion, locally owned —
not a shared GNL catalog, ADR-002 unaffected): grown from 2 cities/3 districts to
**10 cities / 24 districts**, covering Turkey's next most populous cities so address
forms/dropdowns have realistic variety to develop against:

| City id | Name | District ids (name) |
|---|---|---|
| 1 | Istanbul | 1 (Kadikoy), 2 (Besiktas), 4 (Uskudar), 6 (Sisli) |
| 2 | Ankara | 3 (Cankaya), 7 (Kecioren), 8 (Yenimahalle) |
| 3 | Izmir | 5 (Konak), 9 (Bornova), 10 (Karsiyaka) |
| 4 | Bursa | 11 (Osmangazi), 12 (Nilufer) |
| 5 | Antalya | 13 (Muratpasa), 14 (Konyaalti) |
| 6 | Adana | 15 (Seyhan), 16 (Yuregir) |
| 7 | Konya | 17 (Selcuklu), 18 (Meram) |
| 8 | Gaziantep | 19 (Sahinbey), 20 (Sehitkamil) |
| 9 | Mersin | 21 (Akdeniz), 22 (Yenisehir) |
| 10 | Kayseri | 23 (Melikgazi), 24 (Kocasinan) |

These are **reference/catalog rows**, not all individually tied to a seeded customer
address — the same pattern as the shared `GNL_ST`/`GNL_TP` catalogs, where a contract
row doesn't need an active business-row reference to earn its place. Only Uskudar
(id 4) and Konak (id 5) are actually exercised by a customer address (customer 1006's
2nd address and customer 1009's 2nd address, respectively, both non-primary) — no
existing address row referenced by product-service's `service_address_id` or
account-service's `address_id` fixtures was touched.

**Nationality IDs**: synthetic 11-digit values `10000001004`..`10000001011` (one per
customer 1004-1011), encoding the customer's own business number for traceability —
never a real TCKN. They pass the MERNIS stub unchanged (the stub is purely
syntactic: any `^[0-9]{11}$` value not on the deny list `99999999999` verifies —
see `MernisVerifyController`).

---

## Accounts (account-service, `account_db`)

`cust_acct` ids 1-4 (customer 1001) are the V2 baseline. V4 adds ids 5-18.

| `cust_acct` id | Account number | Owning customer | Type | State | Scenario |
|---|---|---|---|---|---|
| 1 | 1261000002 | 1001 | 223 | Active | K-8, never listed (baseline) |
| 2 | 1261000010 | 1001 | 224 | Active | 4 product involvements (baseline) |
| 3 | 1261000028 | 1001 | 224 | Active | MSG-PROD-NONE fixture (baseline) |
| 4 | 1261000036 | 1001 | 224 | Active | involvement-free until runtime tests passivate it |
| 5 | 1261000044 | 1004 | 223 | Active | K-8, never listed |
| 6 | 1261000051 | 1004 | 224 | Active | Fiber 100 family (3 products, CMP-FIBER-01) |
| 7 | 1261000069 | 1004 | 224 | Active | 2nd Billing Account for 1004 (**multiple-BA scenario**); 1 campaign-less product |
| 8 | 1261000077 | 1005 | 223 | Active | K-8, never listed |
| 9 | 1261000085 | 1005 | 224 | Active | **parent + 2 children** (CMP-FIBER-02) |
| 10 | 1261000093 | 1006 | 223 | Active | K-8, never listed |
| 11 | 1261000101 | 1006 | 224 | Active | single campaign-less product |
| 12 | 1261000119 | 1007 | 223 | Active | K-8, never listed |
| 13 | 1261000127 | 1007 | 224 | Active | **two independent product families**, two different service addresses |
| 14 | 1261000135 | 1007 | 224 | Active | **Billing Account with NO product involvement** |
| 15 | 1261000143 | 1008 | 223 | Active | K-8, never listed |
| 16 | 1261000150 | 1008 | 224 | Active | single campaign-less product |
| 17 | 1261000168 | 1011 | 223 | Active | K-8, never listed |
| 18 | 1261000176 | 1011 | 224 | Active | **Billing Account with NO product involvement** |

Customers 1009 and 1010 deliberately have **no accounts at all** (K-8 only fires on
a customer's first Billing Account creation).

### Account-number validation method (ADR-014)

Every V4 number was derived from, and verified against, the **production**
`AccountNumberGenerator.format(int segment, int year, int sequenceValue)` /
`LuhnCheckDigit` algorithm — not hand-typed. Format:
`[T=1][YY=26][SSSSSS][Luhn check digit]`, contiguously continuing the V2 seed's
`acct_number_seq(segment=1, seq_year=2026)` from `next_value = 100004` through
`100017` (14 numbers, one per row above, in table order).

This derivation is captured as a permanent regression test:
[`AccountNumberFormatTest.v4FixtureAccountNumbersAreValidAndDistinct`](../../backend/account-service/src/test/java/com/crm/account/account/number/AccountNumberFormatTest.java)
re-derives all 14 numbers from `AccountNumberGenerator.format(1, 2026, seq)` for
`seq` 100004..100017, asserts each against `LuhnCheckDigit.isValid`, asserts
pairwise distinctness, and asserts the next free sequence value is 100018 — the
exact value the V4 migration's `GREATEST(next_value, 100018)` advances to.

`acct_number_seq(1, 2026).next_value` after V4 = **100018** (never decreased —
`UPDATE ... SET next_value = GREATEST(next_value, 100018)`).

---

## Products (product-service, `product_db`)

`prod` ids 1-4 are the V2 baseline (ADSL 8MB family + the passive "ADSL 8MB Legacy"
fixture, preserved unchanged). V3 adds ids 5-17.

| `prod` id | Name | Parent | Associated billing account | Campaign code | State | Scenario |
|---|---|---|---|---|---|---|
| 1 | ADSL 8MB | — | 1261000010 | CMP-ADSL-01 | Active | baseline main product, own service address |
| 2 | ADSL Data Modem | 1 | 1261000010 | CMP-ADSL-01 | Active | baseline child, resolves parent's address |
| 3 | ADSL Activation | 1 | 1261000010 | — | Active | baseline child, campaign-less on the instance |
| 4 | ADSL 8MB Legacy | — | 1261000010 | — | **Passive** | baseline passive-product fixture (unchanged, not expanded) |
| 5 | Fiber 100MB | — | 1261000051 | CMP-FIBER-01 | Active | main, own service address (addr 4) |
| 6 | Fiber Wi-Fi 6 Modem | 5 | 1261000051 | CMP-FIBER-01 | Active | child, resolves parent's address |
| 7 | Fiber Activation | 5 | 1261000051 | — | Active | child, campaign-less on the instance |
| 8 | ADSL 24MB | — | 1261000069 | — | Active | standalone, campaign-less (2nd BA for 1004) |
| 9 | Fiber 500MB | — | 1261000085 | CMP-FIBER-02 | Active | main, own service address (addr 5); **parent + 2 children family** |
| 10 | Fiber Wi-Fi 6 Modem | 9 | 1261000085 | CMP-FIBER-02 | Active | child |
| 11 | Fiber Activation | 9 | 1261000085 | — | Active | child, campaign-less on the instance |
| 12 | ADSL 16MB | — | 1261000101 | — | Active | standalone, campaign-less |
| 13 | Fiber 1000MB | — | 1261000127 | — | Active | standalone family A, own address (addr 8) |
| 14 | ADSL 16MB | — | 1261000127 | CMP-ADSL-02 | Active | main of family B, own address (addr 9, non-primary) |
| 15 | ADSL Data Modem | 14 | 1261000127 | CMP-ADSL-02 | Active | child of family B, resolves addr 9 |
| 16 | ADSL Activation | 14 | 1261000127 | — | Active | child of family B, campaign-less on the instance |
| 17 | Fiber 100MB | — | 1261000150 | — | Active | standalone, campaign-less (campaign-eligible offer bought outside a campaign) |

Products 13/14/15/16 together demonstrate **one Billing Account with multiple
independent product families**. Products 6/7 and 10/11 together with 5/9
demonstrate **a main product with its own service address, and children
inheriting/resolving it** at two different depths of the account portfolio.

---

## Offers and campaigns (product-service, `product_db`)

### Offers (`prod_ofr`), id-ordered — 10 active total

| id | Name | Service type | Price (project-added fixture) |
|---|---|---|---|
| 1 | ADSL 8MB Offer | INTERNET | 299.00 |
| 2 | ADSL Data Modem Offer | RESOURCE | 149.00 |
| 3 | ADSL Activation Offer | ACTIVATION | 49.00 |
| 4 | Fiber 100MB Offer | INTERNET | 399.00 |
| 5 | Fiber 500MB Offer | INTERNET | 599.00 |
| 6 | Fiber 1000MB Offer | INTERNET | 799.00 |
| 7 | Fiber Wi-Fi 6 Modem Offer | RESOURCE | 249.00 |
| 8 | Fiber Activation Offer | ACTIVATION | 79.00 |
| 9 | ADSL 16MB Offer | INTERNET | 349.00 |
| 10 | ADSL 24MB Offer | INTERNET | 379.00 |

Relative positioning (all prices are project-added dev fixtures, not commercial
data): slower/basic internet < faster/premium internet; device/resource offer
priced separately from the internet offer; activation priced below any main
recurring internet offer. Offers 6 and 10 are **campaign-less** (available for
standalone purchase, e.g. product 13 and product 8).

### Campaigns (`cmpg`), id-ordered — 4 active total

| Campaign code | Name | Members (main first) | Total (DERIVED from active members, never stored) |
|---|---|---|---|
| CMP-ADSL-01 | ADSL Hosgeldin Kampanyasi | 1 (main), 2, 3 | 299.00 + 149.00 + 49.00 = **497.00** |
| CMP-FIBER-01 | Fiber Baslangic Kampanyasi | 4 (main), 7, 8 | 399.00 + 249.00 + 79.00 = **727.00** |
| CMP-FIBER-02 | Fiber Hizli Kampanya | 5 (main), 7, 8 | 599.00 + 249.00 + 79.00 = **927.00** |
| CMP-ADSL-02 | ADSL Hiz Yukseltme Kampanyasi | 9 (main), 2, 3 | 349.00 + 149.00 + 49.00 = **547.00** |

`cmpg` has no price column by design (ADR/schema decision predating this
expansion) — `CatalogMapper.toCampaignResponse` sums the active members' offer
prices. All four campaigns have a future `activation_end_date`
(`2026-12-31` / `2027-06-30`) — no expired-campaign fixture exists.

---

## Cross-service fixture contract

These are **references used as test-fixture contracts only** — there are no
cross-database foreign keys anywhere; every service's Flyway migration writes
solely to its own owning database.

- **Customer/address ids referenced externally**: account-service's `cust_acct.address_id`
  and product-service's `prod.service_address_id` both point at customer-service's
  `addr.id` (customer_db) by plain numeric value, e.g. `addr` ids 4/5/7/8/9/11/14 for the
  new customers. No FK; resolved at read time via
  `GET /api/customers/{customerNumber}/addresses` (account-service) or the internal
  `GET /api/addresses/{addressId}` (product-service).
- **Product ids referenced by account involvement rows**: `cust_acct_prod_invl.product_id`
  in account_db points at product-service's `prod.id` (product_db) by plain numeric
  value (ids 5-17 for the V4 expansion). No FK; account-service is the **only** writer
  of this table (ADR: `cust_acct_prod_invl` owned exclusively by account-service) —
  product-service's own V3 migration never touches `account_db`.
- **Account numbers referenced by product-list scenarios**: product-service's
  `GET /api/products?accountNumber=...` composes over account-service's
  `GET /api/accounts/{accountNumber}/product-ids` using the KR-11 account number
  as the join key — never account-service's internal `cust_acct.id`.

---

## Test coverage index

| Area | Test |
|---|---|
| Account-number derivation/Luhn/distinctness | `AccountNumberFormatTest.v4FixtureAccountNumbersAreValidAndDistinct` |
| Multiple Billing Accounts for one customer | `AccountServiceIntegrationTest.customer1004HasMultipleBillingAccounts` |
| Empty / multi-family product-ids | `AccountServiceIntegrationTest.v4FixtureProductIdsCoverage` |
| Customer search: shared last name, middle name, GSM grouping, soft-delete | `CustomerServiceIntegrationTest.v3FixtureSearchCoverage` |
| Address primary invariant on new customers | `CustomerServiceIntegrationTest.v3FixtureAddressInvariant` |
| Product list: multi-family account, empty account | `ProductServiceIntegrationTest.listMultipleIndependentFamiliesOnOneAccount`, `.listEmptyForV4NoProductsAccount` |
| Product detail: child resolves non-primary parent address | `ProductServiceIntegrationTest.detailOfChildProductResolvesNonPrimaryParentAddress` |
| Several active campaigns + derived totals | `ProductServiceIntegrationTest.campaignsCatalog` |
| Service-address resolution through a multi-level parent chain (unit) | `ProductBusinessRulesTest.multiLevelChainResolvesToMainAddress` |
