# Entity Catalog — ownership map

Source: `docs/source/data-model/CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx`.
Physical names are lowercase; logical workbook names in caps.

## Central shared catalogs — owned by lookup-service (`lookup_db`, ADR-002)

| Workbook | Table | Contents |
|---|---|---|
| GNL_ST | `gnl_st` | Status catalog. Contract IDs: 1=ACTV, 2=PASV (GENERAL); 3=WAIT, 4=MIDLWARE, 5=CANCELLED (ORDER); 6=PNDG (PROD) |
| GNL_TP | `gnl_tp` | Type catalog. Contract IDs: 1=MALE, 2=FEMALE (GENDER); 3=INDV, 4=ORG (PARTY_TYPE); 5–12 CATALOG/BSN_INTER/SERVICE types |

These IDs are **immutable contract data** — lookup-service seeds them explicitly and
must never renumber them. Consumers store them as external references with **no FK**.

## customer-service (`customer_db`)

| Workbook | Table | Notes |
|---|---|---|
| ROLE | `role` | Local lookup; seed 1 = "Customer" |
| CITY | `city` | Local reference; seed Istanbul, Ankara (V2) + 8 more (V3 fixture expansion): Izmir, Bursa, Antalya, Adana, Konya, Gaziantep, Mersin, Kayseri — 10 total |
| DISTRICT | `district` | FK → city; seed Kadikoy, Besiktas, Cankaya (V2) + 21 more (V3 fixture expansion), 2-4 per city — 24 total |
| PARTY | `party` | `party_type_id` → central GNL_TP (INDV) |
| IND | `ind` | `gender_id` → central GNL_TP; `nationality_id` UNIQUE over ALL rows (ADR-003) |
| PARTY_ROLE | `party_role` | partial unique (party_id, role_id) among non-deleted |
| CUST | `cust` | `customer_number` = public business ID (sequence, starts 1001); internal `id` never exposed |
| ADDR | `addr` | multiple per party; one active primary (partial unique index) |
| CNTC_MEDIUM | `cntc_medium` | one row per party this phase (UNIQUE party_id) |

## Other domains (future services — NOT in customer_db)

ACCT_TP, CUST_ACCT (account) · PROD_* , CMPG* , PROD_CATAL* (product)
· BSN_INTER, CUST_ORD, CUST_ORD_ITEM (order/sale). Their `status_id`/type references will
also resolve to the central catalogs.

**USERS is deliberately NOT a future service table:** credentials/enabled-state
are owned by Keycloak; no application password table may exist (ADR-011 —
recorded workbook supersession, analyst sign-off pending).

## Common column conventions

Every business table: `created_date TIMESTAMPTZ NOT NULL`, `created_by VARCHAR(64) NOT NULL`,
`updated_date`, `updated_by`, `deleted_date`, `deleted_by`, plus `status_id BIGINT NOT NULL`
(external catalog reference). Active row invariant: `status_id = 1 (ACTV) AND deleted_date IS NULL`.
`*_by` columns hold `system` until authentication exists (sized for a Keycloak `sub`, ADR-004).
