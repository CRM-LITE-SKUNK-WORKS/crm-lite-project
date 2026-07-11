# ADR-003: Nationality ID Uniqueness

## Status
Accepted (2026-07-10) — supersedes the earlier "active-only" interpretation and the
schema decision recorded in PROJECTBRAIN §5.14.

## Context
Three sources describe Nationality ID uniqueness with different strength:

- **FR/AC v8 Final** (`CRM_Lite_FR_AC_v8_Final.docx`): AC-CUST-03-11 — "girilen
  Nationality ID ile eşleşen bir müşteri varsa … MSG-CUST-DUP-NATID" (**no
  "active" qualifier**); AC-CUST-04-04 — unique excluding the record itself.
- **Use-case document** (`CRM_Lite_Kullanim_Senaryolari_Final.docx`, FR-CUST-03 step 4.4)
  and the **draw.io diagram** (FR-CUST-03 page, decision node "NAT ID başka **aktif**
  müşteride var mı?") still carry the older **active-only** wording.
- **Analyst decision (2026-07-10, this refactor)**: Nationality ID is **globally and
  permanently unique** across all IND rows; a soft-deleted/passive customer does
  **not** release its Nationality ID.

The previous implementation iteration had removed the DB UNIQUE constraint to satisfy
the active-only reading. The new analyst decision supersedes that.

## Decision
1. `ind.nationality_id` is `NOT NULL`, exactly 11 digits (VR-NATID), and carries a
   **database UNIQUE constraint** covering all rows including soft-deleted ones.
2. Create checks uniqueness against **all** IND rows (active, passive, deleted).
3. Update checks uniqueness against all rows **excluding only the customer's own IND row**.
4. A racing insert that slips past the application check and hits the DB constraint is
   translated by `GlobalExceptionHandler` (`DataIntegrityViolationException`) to
   **HTTP 409 + `MSG-CUST-DUP-NATID`** — duplicate constraints never surface as HTTP 500.

## Documented discrepancy in stale sources
The use-case document step 4.4 ("eşleşen **aktif** bir müşteri") and the draw.io
FR-CUST-03 decision node ("başka **aktif** müşteride") predate this decision and are
**not canonical**. The FR/AC v8 wording plus this ADR govern. The binary/XML source
documents are intentionally left unedited; this ADR and the traceability matrix record
the supersession.

## Consequences
- Soft-deleting a customer does not free the Nationality ID for reuse.
- The DB constraint is the last line of defence; the application check exists to give
  a friendly, field-level error before hitting the database.
