# Analyst Source Document Delta — v8 Final, 16.07.2026 revision

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
| 6 | **KR-04:** default page size **15**, user-selectable Per Page **15/30/50**, server-side pagination, firstName→lastName sort | FR v8 §1 | Sorting + server-side pagination already implemented. **API default stays 20** per the team's ADR-005 decision — recorded as an open conflict (below), not silently reconciled |
| 7 | **AC-CUST-03-11/12 renumbering:** VR-NATID format rule is now AC-CUST-03-11; duplicate-NATID rule is AC-CUST-03-12 | FR v8 §2.2 | Doc references updated (ADR-003 context cites the old numbering; a note was added there) |
| 8 | **FR-ADDR-04 confirmation flow:** MSG-ADDR-DELETE-CONFIRM modal with LBL-YES/LBL-NO before in-use check and deletion | FR v8 §2.3 | Frontend interaction; backend operation/status behaviour documented in `docs/api/customer-service.md` §K |
| 9 | **ACCT/PROD/SALE/LANG requirement groups** fully specified (billing accounts incl. auto Customer Account creation in use case FR-ACCT-02 step 8, product listing/detail, sale basket validation rules incl. MSG-SALE-* catalog, session rules KR-8/KR-9) | FR v8 §2.5–2.8 + use cases | Captured as planned future work in the service roadmap (PROJECTBRAIN §9, `docs/architecture/service-boundaries.md`); intentionally NOT implemented in this task |

## Open conflicts / superseded wording (recorded, unresolved by analysts)

| # | Conflict | Where | Status |
|---|---|---|---|
| 1 | Nationality ID uniqueness: FR AC-CUST-03-12 says globally unique (**no active qualifier**); the use-case document alternative step 4.5 still says "eşleşen **aktif** bir müşteri" | Use-case doc FR-CUST-03 | **ADR-003 (permanent global uniqueness) stands.** Use-case wording is superseded; recorded in ADR-003, traceability matrix and functional-requirements.md |
| 2 | Name matching: draw.io FR-CUST-01 note still says "içinde-geçen" (contains, case-insensitive) | draw.io FR-CUST-01 page | KR-01 (word-start) governs; diagram note superseded |
| 3 | KR-04 default page size 15 (UI) vs API default 20 (ADR-005 team decision) | FR v8 KR-04 vs ADR-005 | **Open item**: frontend must send `size=15|30|50` explicitly; flip the API default to 15 if analysts require it — one-line change, tracked in ADR-005 |
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
