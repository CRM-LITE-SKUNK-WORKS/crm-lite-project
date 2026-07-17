# ADR-005: Customer List and Filter Contract (GET /api/customers)

## Status
Accepted (2026-07-16) — implements the FR/AC v8 Final revision of 16.07.2026
(AC-CUST-01-00, KR-04, message catalog update). Extends, does not replace, the
KR-01 search semantics already recorded in ADR-001..003-era documentation.

## Context
The 16.07.2026 revision of `CRM_Lite_FR_AC_v8_Final.docx` added **AC-CUST-01-00**:
after login, the main page lists **all customers**, sorted A-Z by customer name.
Until now `GET /api/customers` rejected requests without at least one search
criterion (400 `MSG-SEARCH-CRITERIA-REQUIRED`) and returned a slim
`Page<CustomerSearchResponse>` row (`customerId, firstName, middleName, lastName,
role, nationalityId`).

Two contract problems followed:

1. The frontend cannot render the post-login "all customers" page against an
   endpoint that refuses criterion-less requests.
2. The team decided the list rows must carry **exactly the same field contract** as
   the singular detail endpoint `GET /api/customers/{customerNumber}`, so the UI can
   render list and detail from one model and no second row-DTO needs maintaining.

## Decision
1. **One endpoint, two modes.** `GET /api/customers` stays the single canonical
   customer read-list endpoint (no `/search` alias — that removal stands). With
   **no filter parameters** it is the *browse* mode; with parameters it is the
   *filter* mode. Both modes share pagination, sorting, the active-only rule and the
   response shape, so no second endpoint or alias is introduced.
2. **Browse mode** returns every **ACTIVE, non-deleted** customer
   (`status_id = ACTV AND deleted_date IS NULL`), server-side paginated. "All
   customers" means the whole active list is reachable page by page — there is **no
   unbounded all-rows response**.
3. **Response type** is `Page<CustomerDetailResponse>` in both modes. Every row
   carries exactly the current singular-detail contract:
   `customerNumber, firstName, middleName, lastName, fatherName, motherName,
   birthDate, gender, nationalityId, role, status`. No address/contact collections
   are added, because the singular detail endpoint has none (exact equivalence rule).
4. **Pagination defaults:** `page=0`, `size=20`. **Sort:** `firstName ASC`,
   then `lastName ASC`, then `customerNumber ASC` as a stable tiebreak so pages never
   shuffle equal names.
5. **Filters keep the analyst-approved KR-01 semantics** (unchanged): `firstName` =
   word-start match over First + Middle combined, `lastName` = word-start over Last
   Name, both = AND; `gsmNumber` = mobile-phone prefix; `nationalityId` /
   `customerId` (business customer number) = exact; filled criterion groups OR-ed;
   `accountNumber`/`orderNumber` remain **501 `MSG-FEATURE-NOT-IMPLEMENTED`** until
   the account/order domains exist.
6. **Mandatory-criteria rule removed.** `checkAtLeastOneSearchCriterionExists` is
   deleted, its tests removed, and the project-specific key
   `MSG-SEARCH-CRITERIA-REQUIRED` is retired (no other endpoint used it). The UI-level
   rule "LBL-SEARCH is disabled while all filter fields are empty" (AC-CUST-01-02)
   remains a **frontend** behaviour; the API itself now serves the browse mode instead.
7. **`CustomerSearchResponse` deleted.** No released consumer existed; the mapper's
   `toSearchResponse` went with it.
8. **N+1 protection.** The list specification fetch-joins the to-one detail graph
   (customer → partyRole → role/party → individual/contactMedium) for row queries and
   keeps plain joins for the count query. All joins are to-one, so fetch joins cannot
   fan out rows and pagination stays correct.

## Compatibility consequences
- **Breaking for any client of the old row shape:** the business identifier field in
  list rows is now `customerNumber` (was `customerId` in `CustomerSearchResponse`),
  and rows carry the full demographic set. There are no released consumers (no
  frontend yet), so the break is accepted without a transition period.
- A criterion-less request now returns **200 + page 0 of the active list** instead of
  400 — clients relying on the rejection (none known) must adapt.
- The **query parameter** `customerId` keeps its name (public search field naming
  follows the screens); only the response field changed.
- The singular detail endpoint `GET /api/customers/{customerNumber}` is byte-for-byte
  unchanged.

## Recorded discrepancy — KR-04 default page size
KR-04 (v8 Final, 16.07.2026) describes the **UI**: default Per Page **15**, options
15/30/50, changeable under the results table. The API default chosen here is
**20** (team decision for this backend contract iteration). This is not silently
reconciled: the frontend must pass `size=15|30|50` explicitly per KR-04, and the API
accepts any positive `size`. If the analysts want the API default itself to be 15,
that is a one-line change; the open question is tracked in
`docs/requirements/traceability-matrix.md` and PROJECTBRAIN "Open conflicts".

## Consequences
- Frontend can implement AC-CUST-01-00 (post-login all-customer list) directly.
- One DTO to maintain for customer reads; list and detail can never drift apart.
- Browse-mode pages are heavier than the old slim rows (5 extra scalar fields per
  row) — negligible at this data scale; revisit only if row counts explode.
- Tests cover: browse returns all active customers, soft-deleted exclusion, A-Z
  ordering, full detail fields per row, pagination, unchanged filter semantics,
  unchanged singular detail, absent `/search` alias.
