# ADR-003: Nationality ID Uniqueness

## Status
Accepted (2026-07-10) — supersedes the earlier "active-only" interpretation and the
schema decision recorded in PROJECTBRAIN §5.14.

## Context
Three sources describe Nationality ID uniqueness with different strength:

- **FR/AC v8 Final** (`CRM_Lite_FR_AC_v8_Final.docx`): the duplicate rule — "girilen
  Nationality ID ile eşleşen bir müşteri varsa … MSG-CUST-DUP-NATID" (**no
  "active" qualifier**) — was AC-CUST-03-11 when this ADR was written; the
  **16.07.2026 revision renumbered it to AC-CUST-03-12** (AC-CUST-03-11 is now the
  VR-NATID format rule). The wording still has no active qualifier, so this ADR's
  decision is **confirmed**, not weakened, by the revision. AC-CUST-04-04 — unique
  excluding the record itself — unchanged.
- **Use-case document** (`CRM_Lite_Kullanim_Senaryolari_Final.docx`, FR-CUST-03
  alternative step 4.5 in the current revision — previously step 4.4)
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

## Open conflict / superseded wording (still present as of FR/AC v8-2, 03.08.2026)
The use-case document alternative step 4.5 ("eşleşen **aktif** bir müşteri") and the
draw.io FR-CUST-03 decision node ("başka **aktif** müşteride") predate this decision
and are **not canonical** — neither the 16.07.2026 nor the subsequent v8-1/v8-2
FR/AC revisions cleaned them up, so the conflict remains open on the analyst side.
**Re-verified 2026-08-05 against v8-2:** AC-CUST-03-12 still reads "girilen
Nationality ID ile eşleşen bir müşteri varsa" with no active qualifier — the FR/AC
side of this conflict is unchanged; the use-case/draw.io side was not independently
re-checked as part of this pass (see `docs/requirements/document-delta.md` conflict
#1, still open). The FR/AC v8-2 wording (AC-CUST-03-12, no active qualifier) plus
this ADR govern. The binary/XML source documents are intentionally left unedited;
this ADR, `docs/requirements/document-delta.md` and the traceability matrix record
the supersession.

## Consequences
- Soft-deleting a customer does not free the Nationality ID for reuse.
- The DB constraint is the last line of defence; the application check exists to give
  a friendly, field-level error before hitting the database.
