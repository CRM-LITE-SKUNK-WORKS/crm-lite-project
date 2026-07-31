# Analyst Source Document Delta

Reconciliation record: what changed in the analyst-approved source documents
(`docs/source/**`) between revisions, and how each change was handled in this
repository. The binary source documents themselves are never edited here.

## v8-1 Final, 23.07.2026 revision (current)

**Supersedes the 16.07.2026 v8 Final document** (see the section below for that
revision's own delta, which remains valid history). Confirmed via internal
document metadata, not filename alone:

| | `CRM_Lite_FR_AC_v8_Final.docx` | `CRM_Lite_FR_AC_v8-1_Final.docx` |
|---|---|---|
| `docProps/core.xml` revision | 29 | 37 |
| `dcterms:modified` | 2026-07-16T08:17:00Z | 2026-07-23T07:08:00Z |
| Header "Son güncelleme" line | 16.07.2026 | 23.07.2026 |

The v8-1 header's own change list scopes this revision to **KR-11, FR-ACCT-01-03,
FR-ACCT-01-04, FR-ACCT-04-02** only — all other FR/AC content is unchanged from the
16.07.2026 text (byte-diffed to confirm; the only other difference is trailing
whitespace on one unrelated FR-ADDR-04 line).

**Housekeeping note:** the working tree also contains an untracked duplicate,
`docs/source/requirements/CRM_Lite_FR_AC_v8-1_Final 1.docx` (note the " 1" suffix and
the missing extension separator), byte-identical in content to
`CRM_Lite_FR_AC_v8-1_Final.docx`. This reads as a duplicate download, not a
distinct analyst document; left as-is per "never edit/rename analyst source files"
— flagged here so it isn't mistaken for a competing revision later.

### Implementation record (2026-07-23, later the same day — account-service built)

The v8-1 ACCT scope below is now **implemented** in `backend/account-service`
under **ADR-013/ADR-014** (which govern; sprint decisions recorded in
`docs/architecture/account-service-decisions.md`). Points of record:

- **v8-1 supersedes v8** (confirmed from document metadata, table above);
  deletion = **passivation** — Passive accounts remain list-visible
  (AC-ACCT-01-03/04, AC-ACCT-04-02).
- **K-8 analyst decision (approved):** the use-case's automatic 223 Customer
  Account (steps 8–8.3) is implemented as a lazy, same-transaction side effect
  of the first 224 creation; one 223 per customer; never exposed via the API.
  This closes the "FR/AC is silent on the 223" gap in the FR text — the FR
  itself still does not mention it (flagged for a future FR revision).
- **Workbook deviations (workbook not edited):** `CUST_ACCT` seed
  `account_number` values regenerated to KR-11 (`1261000002`, `1261000010`,
  `1261000028`, `1261000036` — conflict #9 below); `customer_number` (public
  business number 1001) stored instead of the workbook's internal
  `customer_id`; the added `acct_number_seq` table (KR-11 needs sequence
  state the workbook does not define); the 223 seed row's `account_name` is
  the fixed K-8 constant "Customer Account" instead of the workbook's
  per-customer text.
- `acct_tp` is a **local account-domain catalog** (223/224) — NOT a shared
  GNL_ST/GNL_TP catalog; the GNL rules (ADR-002) are untouched, and
  `account_db` contains no gnl tables and no cross-database FKs.
- `cust_acct_prod_invl` is owned/written **only by account-service**; future
  product/order/sale services populate it via an account-service command/API
  or a consumed event — direct `account_db` access is prohibited (ADR-013 §5).
- customer-service is deliberately **unmodified**: its `accountNumber` 501,
  customer-delete guards and `MSG-ADDR-IN-USE` no-op convert to real
  account-service calls in a separate follow-up PR.
- Conflicts #7 and #8 below (use-case + draw.io still describing FR-ACCT-04 as
  list removal) **remain open on the analyst side**; the implementation follows
  FR v8-1 (passivation, stays visible).

### Implementation record (2026-07-29 — product-service built, read-only FR-PROD-01..02 slice)

`backend/product-service` (port 8086, `product_db`, `com.crm.product`) implements
FR §2.6 (FR-PROD-01, FR-PROD-02) plus a read-only offer/campaign catalog. **No
FR/AC text changed** — this section records the *workbook* deviations and the
project additions the FR does not name. Full contract:
[`docs/api/product-service.md`](../api/product-service.md).

**Workbook deviations (workbook NOT edited).** All four are fixtures needed to
make the AC branches testable; the invented prices in particular are **pending
analyst approval**:

| # | Deviation | Why | Where |
|---|---|---|---|
| P1 | **`PROD_OFR.product_offer_total_price` filled with invented values** — offer 1 `299.00`, offer 2 `149.00`, offer 3 `49.00` | The workbook leaves the column empty in all three rows, but AC-SALE-01-12 requires per-offer "tutar" and a "Total Amount". Something had to be seeded to make the §2.7 screens buildable | `product-service` `V2__seed_product_data.sql` — **analyst approval pending** |
| P2 | **Campaign fixture:** `PROD` rows 1 and 2 carry `campaign_id = 1` (`CMP-ADSL-01`) | Every workbook `PROD` row has `campaign_id` empty, so AC-PROD-01-03's "show campaign information when the product was bought within a campaign" branch was untestable. Product 3 deliberately stays campaign-less so the `"-"` branch is covered too | same file |
| P3 | **Passive product fixture:** new `PROD` row 4 (`ADSL 8MB Legacy`), `status_id = 2` (PASV) with `deleted_date`/`deleted_by` set | Every workbook product is `ACTV`, leaving AC-PROD-01-03's Status "pasif" branch untestable. Follows the full soft-delete invariant rather than a status-only row | same file |
| P4 | **Involvement rows for products 3 and 4** added to `cust_acct_prod_invl` (account 2 = `1261000010`) | The workbook links only products 1 and 2 to an account, leaving product 3 (ADSL Activation) dangling. ⚠️ These rows belong to `account_db`, so they are seeded by **account-service's own `V3__seed_activation_involvement.sql`** — product-service never writes `account_db` (ADR-013 §5). Product 4's involvement is PASV-but-not-deleted, so the product stays listed while the AC-ACCT-04-03 delete guard (ACTV-only) is unaffected | `account-service` `V3__seed_activation_involvement.sql` |

Accounts `1261000028` and `1261000036` are left product-less on purpose — they are
the `MSG-PROD-NONE` (AC-PROD-01-02) fixtures.

**Project additions the FR/AC catalog does not name:**

- **`MSG-PROD-NOT-FOUND` (404)** for an unknown product id on
  `GET /api/products/{id}`. The analyst catalog names no such outcome because
  FR-PROD-02 is always reached from a row the user just saw. `MSG-PROD-NONE`
  stays **frontend-only** — an account with no products is `200 []`.
- **`GET /api/accounts/{accountNumber}/product-ids`** on account-service: the
  single public *reading* point of the `cust_acct_prod_invl` projection
  (ADR-013 §5 read side). Involvement **writes** remain unimplemented.
- **`GET /api/addresses/{addressId}`** on customer-service (internal, not
  gateway-routed): product-service holds only the bare FK-less
  `prod.service_address_id`, and the public address API is customer-scoped. A
  read-only addition — customer-service's documented 501/no-op TODOs are
  untouched.

**Interpretation decisions where the FR is silent** (recorded so they are not
mistaken for requirements):

- `PROD_SPEC.is_dev` and `PROD.transaction_id` are kept schema-faithful and
  nullable, unused by any endpoint — no FR/AC defines their meaning.
- **No pagination** on `GET /api/products`: FR-PROD-01 states no pagination rule
  (unlike KR-04 for customers), so the full list is returned.
- **Campaign price is derived** (sum of member offers); `CMPG` gets no price
  column, because the workbook defines none.
- **No product-number generation rule** was invented — the public product
  identifier is `prod.id`. KR-11-style business numbers exist only for accounts.
- The characteristic model (`PROD_SPEC_CHAR`, `PROD_SPEC_CHAR_USE`,
  `PROD_CHAR_VAL`) is created and seeded but has **no endpoint** in this slice:
  it is consumed by the §2.7 Product Configuration screens.

**ADR debt (explicitly owed, not silently skipped):** **ADR-015** (product
boundary) and a **read-side clause on ADR-013** are outstanding, and
`docs/architecture/service-boundaries.md`'s "analyst/architecture sign-off is
still missing" warning continues to apply to the product domain.

### Implementation record (2026-07-31 — local dev/demo seed fixture expansion, no FR/AC text changed)

Forward-only migrations only: `customer-service V3`, `product-service V3`,
`account-service V4`. **No previously applied migration was edited.** This is a
test-data **volume** expansion for local development/demo purposes, not a new
requirement deviation — full fixture-by-fixture detail (customer/account/product
tables, offers, campaigns, cross-service id contract) is in
[`docs/testing/seed-fixture-catalog.md`](../testing/seed-fixture-catalog.md);
only the deviations from analyst source material are recorded here, extending the
same open conflicts already logged above (P1/#10):

| # | Deviation | Why | Where |
|---|---|---|---|
| P5 | **More invented offer prices**: Fiber 100MB `399.00`, Fiber 500MB `599.00`, Fiber 1000MB `799.00`, Fiber Wi-Fi 6 Modem `249.00`, Fiber Activation `79.00`, ADSL 16MB `349.00`, ADSL 24MB `379.00` | Same root cause as P1 — the workbook/analyst document names no prices, and a compact demo dataset needs more than 3 offers to exercise the campaign/basket screens. All project-added, relatively ordered (slower/basic < faster/premium; device priced separately; activation below the recurring main offer) | `product-service` `V3__expand_product_test_fixtures.sql` — **analyst approval pending, same as P1** |
| P6 | **3 more active campaigns** (`CMP-FIBER-01`, `CMP-FIBER-02`, `CMP-ADSL-02`), each a main-internet + resource + activation triple, future `activation_end_date` | AC-PROD-01-03/campaign screens need more than one campaign to demo meaningfully; no expired or passive campaign fixture was added (explicitly out of scope) | same file |
| P7 | **13 more product instances** (ids 5-17) across 6 new customers' billing accounts, including 2 independent product families sharing one billing account and a 2nd main+children family | Demonstrates FR-PROD-01/02 branches (multi-family accounts, non-primary service address resolution) that the V2 seed's single family couldn't cover | same file |
| P8 | **8 more customers** (1004-1011) covering middle-name search, shared last names, GSM-prefix grouping, multi-address customers with exactly one primary, and a fully populated contact record | AC-CUST-01 search branches (middle name, GSM prefix, multiple addresses) had only 2-3 active customers to test against | `customer-service` `V3__expand_customer_test_fixtures.sql` |
| P9 | **14 more accounts** (K-8 223 + Billing Accounts) for 6 of the new customers, including a customer with 2 Billing Accounts, a Billing Account with no product involvement, and account numbers contiguously continuing the V2 KR-11 sequence (100004-100017) | FR-ACCT-01 "multiple Billing Accounts per customer" and "Billing Account with no products" branches only had one seeded example (customer 1001) to test against | `account-service` `V4__expand_account_test_fixtures.sql` |

None of P5-P9 changes the account-number or Luhn algorithm, the campaign-total
derivation rule, or any other production business logic — they are additional
rows only. An IPTV product family was considered and **not added**: it would
require a new shared `GNL_TP` `SERVICE_TYPE` row and a change to
`LookupContract.serviceTypeCode()` in product-service, which is out of scope for
a fixture-volume expansion and does not fit AC-SALE-01-08's three-service-type
basket rule — flagged here for analysts, not built.

### Accepted changes (documented 23.07.2026 morning; since implemented — see the implementation record above)

| # | Change | Source | Action taken |
|---|---|---|---|
| 1 | **KR-11 (new): Account Number format.** 10-digit numeric string `[T][YY][SSSSSS][C]` — `T` = segment digit, fixed `1` for this phase (individual customers only; other values reserved for future phases); `YY` = last two digits of the account's creation year; `SSSSSS` = per-segment, per-year sequence starting at `100000`; `C` = check digit, calculation method left to technical design. Assigned number is immutable and never reused, even after the account is passivated. | FR v8-1 §2.5, new KR-11 block | Documented as the target contract for account-service (below, roadmap). No code exists yet to implement against — customer-service has no ACCT tables (ADR-001 scope) |
| 2 | **AC-ACCT-01-03 (new):** the billing-account list shows **both** Active and Passive accounts for the customer; status shown in an Account Status column | FR v8-1 §2.5 FR-ACCT-01 | Documented; account-service requirement |
| 3 | **AC-ACCT-01-04 (new):** list is sorted **Active first, Passive second**; within each status, ascending by **Account Number** | FR v8-1 §2.5 FR-ACCT-01 | Documented; account-service requirement |
| 4 | **AC-ACCT-02-03 wording:** create-account flow now explicitly cross-references KR-11 for the auto-assigned, unique Account Number | FR v8-1 §2.5 FR-ACCT-02 | No behavioural change versus the already-specified "unique, auto-assigned Account Number" (FR-ACCT-02); AC-ACCT-02-02 (Account Name + Billing Address required, address creation reuses FR-ADDR-02) is unchanged |
| 5 | **AC-ACCT-04-02 wording changed:** was "hesap aktif fatura hesabı listesinde gösterilmemeli" (account no longer shown in the active list); now **"hesabın statüsü Passive'e çekilmeli, listede Passive statüsüyle görünmeye devam etmeli"** (account status becomes Passive, stays visible in the list with Passive status). Still blocked by an active-product link (`MSG-ACCT-HAS-PRODUCTS`); still shows `MSG-ACCT-DELETED` on success | FR v8-1 §2.5 FR-ACCT-04 | **Deletion = passivation, not removal.** Documented as the binding account-service contract; supersedes the "removed from active list" reading below |

### Open conflicts / superseded wording introduced or restated by this revision

| # | Conflict | Where | Status |
|---|---|---|---|
| 7 | **Use-case doc still describes FR-ACCT-04 as list removal**, not passivation: "Beklenen Çıktı" says "aktif ürün bağlantısı bulunmayan fatura hesabının **aktif hesap listesinden kaldırılması**"; Ana Senaryo Adım 5 says the system "**fatura hesabını aktif hesap listesinden kaldırır**". Both contradict FR v8-1 AC-ACCT-04-02 (passivation, stays visible as Passive) | Use-case doc, "Fatura Hesabı Silme (FR-ACCT-04)" | **FR v8-1 AC-ACCT-04-02 governs** (passivation). Use-case wording is superseded and not updated by this revision — flagged for analysts |
| 8 | **Draw.io ACCT-04 delete-flow node still labeled "Hesabı aktif listeden kaldır"** (remove account from active list) | `CRMLite_Diagrams_Final.drawio`, ACCT-04 flow | Same conflict as #7 — FR v8-1 AC-ACCT-04-02 governs; diagram not updated by this revision — flagged for analysts |
| 10 | **Workbook `PROD_OFR` rows carry no price, but AC-SALE-01-12 requires amounts** (see deviation P1 above; extended by P5's 7 additional invented offer prices, 2026-07-31). Invented fixture values are in the product-service seed; the analyst document names no prices anywhere | `CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx`, `PROD_OFR` sheet vs FR §2.7 AC-SALE-01-12 | Workbook not edited. **Awaiting analyst-approved price list**; until then all seeded prices (299/149/49 plus the P5 additions) are explicitly provisional |
| 11 | **FR §2.6 never mentions how a product links to a billing account**, yet FR-PROD-01 lists products *per account*. The workbook answers it only via `CUST_ACCT_PROD_INVL` (in `account_db`) — `PROD` has no account column | FR v8-1 §2.6 vs workbook `PROD` / `CUST_ACCT_PROD_INVL` sheets | Resolved architecturally, not by inventing a column: product-service **composes** over account-service's `product-ids` endpoint (ADR-013 §5). Flagged for analysts as an FR gap; **ADR-015 owed** |
| 9 | **Entity/Seed workbook `CUST_ACCT` seed rows use legacy `account_number` values that do not satisfy KR-11**: `0101112900`, `0101112911`, `0101112915`, `0101112441` — all start with segment digit `0`, not the fixed `1` required for this phase, and carry no verifiable check digit. `docs/api/customer-service.md`'s `accountNumber=0101112900` curl example (501 smoke test) also happens to reuse one of these legacy values as an illustrative query param — harmless there (the endpoint is unimplemented and returns 501 regardless of the value's format), but not a valid seed once account-service is built | `CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx`, `CUST_ACCT` sheet | Workbook not edited. When account-service's Flyway seed is authored, these sample account numbers must be regenerated to the KR-11 format, not copied verbatim — flagged for analysts |

### Service roadmap impact

`account-service` moves from a generic "planned, boundary not analyst-final" entry
to the **next approved Sprint domain** in `PROJECTBRAIN.md` and
`docs/architecture/service-boundaries.md`, pending account-specific ADRs (KR-11
Account Number contract, FR-ACCT-01..04 API/DB shape). **No ADR-013/014 created in
this pass** — the account decisions above are documented, not yet architecturally
approved; `product-service`/`order-service` remain future work, unaffected by this
revision; the auth/security milestone remains complete and out of scope here.

---

## v8 Final, 16.07.2026 revision (history)

Reconciliation record: what changed in the analyst-approved source documents
(`docs/source/**`) versus the previously reconciled state (2026-07-11), and how each
change was handled in this repository. The binary source documents themselves are
never edited here.

Documents inspected (extracted content, not just filenames):

- `docs/source/requirements/CRM_Lite_FR_AC_v8_Final.docx` — header states
  "Son güncelleme: 16.07.2026" and lists the touched items: KR-04; FR-CUST-01 /
  AC-CUST-01-00; FR-CUST-03 / AC-CUST-03-06; AC-CUST-03-11; FR-ADDR-04 (general);
  message catalog (MERNIS error messages).
- `docs/source/use-cases/CRM_Lite_Kullanim_Senaryolari_Final.docx`
- `docs/source/data-model/CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx` — **unchanged**
  versus the implemented workbook schema/seed (verified sheet by sheet).
- `docs/source/diagrams/CRMLite_Diagrams_Final.drawio` — 24 pages; FR-CUST-01 page now
  contains the "Login sonrası ana sayfada tüm müşterileri listele" note.

## Accepted changes (implemented and/or documented)

| # | Change | Source | Action taken |
|---|---|---|---|
| 1 | **AC-CUST-01-00 (new):** after login the main page lists ALL customers, sorted A-Z by name | FR v8 §2.2; use case FR-CUST-01 step 1; draw.io FR-CUST-01 | `GET /api/customers` now serves a criterion-less **browse mode** returning all active customers, paginated, firstName→lastName→customerNumber ASC (**ADR-005**) |
| 2 | **Search criteria no longer mandatory** for the API list endpoint | Consequence of AC-CUST-01-00 | `checkAtLeastOneSearchCriterionExists` removed, its tests removed, `MSG-SEARCH-CRITERIA-REQUIRED` retired. AC-CUST-01-02 ("LBL-SEARCH disabled while all fields empty") remains a frontend behaviour |
| 3 | **List rows = full detail contract** | Team API decision recorded with the FR change | `Page<CustomerDetailResponse>`; `CustomerSearchResponse` deleted (ADR-005) |
| 4 | **MERNIS message keys (AC-CUST-03-06 + catalog):** `MSG-MERNIS-UNAVAILABLE` (KPS unreachable), `MSG-CUST-NATID-VERIFICATION-FAILED` (verification failed) | FR v8 message catalog | Implementation renamed: `MSG-NATID-VERIFY-FAILED` → `MSG-CUST-NATID-VERIFICATION-FAILED`; MERNIS outages now return `MSG-MERNIS-UNAVAILABLE` instead of the generic `MSG-SERVICE-UNAVAILABLE` (which remains for shared-catalog outages, ADR-002). Tests + docs updated |
| 5 | **Default application language is English** (AC-LANG-01-01; use case FR-LANG-01 step 3) | FR v8 §2.8 | Documented in requirements + roadmap (localization is future frontend/catalog work; backend already returns language-neutral `messageKey`s). No backend code change needed |
| 6 | **KR-04:** default page size **15**, user-selectable Per Page **15/30/50**, server-side pagination, firstName→lastName sort | FR v8 §1 | Sorting + server-side pagination already implemented. **Fully reconciled 2026-07-29:** the API default is now **15** and only 15/30/50 are accepted (400 otherwise) — ADR-005 §Amendment; the old "API default stays 20" position is withdrawn |
| 7 | **AC-CUST-03-11/12 renumbering:** VR-NATID format rule is now AC-CUST-03-11; duplicate-NATID rule is AC-CUST-03-12 | FR v8 §2.2 | Doc references updated (ADR-003 context cites the old numbering; a note was added there) |
| 8 | **FR-ADDR-04 confirmation flow:** MSG-ADDR-DELETE-CONFIRM modal with LBL-YES/LBL-NO before in-use check and deletion | FR v8 §2.3 | Frontend interaction; backend operation/status behaviour documented in `docs/api/customer-service.md` §K |
| 9 | **ACCT/PROD/SALE/LANG requirement groups** fully specified (billing accounts incl. auto Customer Account creation in use case FR-ACCT-02 step 8, product listing/detail, sale basket validation rules incl. MSG-SALE-* catalog, session rules KR-8/KR-9) | FR v8 §2.5–2.8 + use cases | Captured as planned future work in the service roadmap (PROJECTBRAIN §9, `docs/architecture/service-boundaries.md`); intentionally NOT implemented in this task |

## Open conflicts / superseded wording (recorded, unresolved by analysts)

| # | Conflict | Where | Status |
|---|---|---|---|
| 1 | Nationality ID uniqueness: FR AC-CUST-03-12 says globally unique (**no active qualifier**); the use-case document alternative step 4.5 still says "eşleşen **aktif** bir müşteri" | Use-case doc FR-CUST-03 | **ADR-003 (permanent global uniqueness) stands.** Use-case wording is superseded; recorded in ADR-003, traceability matrix and functional-requirements.md |
| 2 | Name matching: draw.io FR-CUST-01 note still says "içinde-geçen" (contains, case-insensitive) | draw.io FR-CUST-01 page | KR-01 (word-start) governs; diagram note superseded |
| 3 | ~~KR-04 default page size 15 (UI) vs API default 20 (ADR-005 team decision)~~ | FR v8 KR-04 vs ADR-005 | ✅ **CLOSED 2026-07-29 in favour of KR-04.** The analysts closed it as five API defects (BUG-API-CUST-01-14/-16/-17/-18/-19): API default 15, only 15/30/50 accepted, everything else 400 (`size=0`/`page=-1` were 500s). ADR-005 §Amendment; frontend per-page options moved 20/50/100 → 15/30/50 |
| 4 | Use-case FR-CUST-03 has two steps numbered "Adım 4.5" (MERNIS unavailable + duplicate NATID) | Use-case doc | Editorial defect in the source document; no behavioural ambiguity — flagged for the analysts |
| 5 | **Workbook `USERS (Sistem Kullanicisi)` table (`username`, `password_hash`, argon2id seed placeholders) vs Keycloak as sole credential store** | Entity workbook; FR AC-AUTH-01-03 "USERS tablosunda" | **Not implemented, by decision (2026-07-17 auth milestone):** no application password table may exist; Keycloak owns credentials/enabled-state. Seed usernames mirrored as Keycloak dev users (`ayilmaz`/`edemir` enabled, `mkaya` disabled). Recorded in **ADR-011** — awaiting analyst sign-off, workbook not edited |
| 6 | **FR-AUTH-01 UI acceptance criteria assume an in-app login form** (button state, masking, 64-char cap, MSG-AUTH-INVALID-CRED, LBL-LANGUAGE on login) | FR v8 §2.1 + §2.8 | Credentials are entered on the **Keycloak login page** (ADR-006; ROPC/Direct Grant prohibited). The generic-error behaviour of AC-AUTH-01-03/04/05 is satisfied natively; the remaining UI/i18n details bind a future Keycloak **project theme** (standard Keycloak EN/TR i18n active today). Flagged for analyst acknowledgement |

## Verified as unchanged

- Entity/seed workbook: all sheets match the implemented `customer_db`/`lookup_db`
  schema and seeds (GNL_ST/GNL_TP contract IDs, ROLE/CITY/DISTRICT/USERS/PARTY/IND/
  PARTY_ROLE/CUST/ADDR/CNTC_MEDIUM, future ACCT_*/PROD_*/CMPG*/BSN_INTER/CUST_ORD*).
- KR-01 matching semantics, VR-* validation formats, KR-05 gender values, KR-10
  verification rule (except the message-key names above).
- FR-ADDR/FR-CNTC acceptance criteria implemented earlier remain valid.
