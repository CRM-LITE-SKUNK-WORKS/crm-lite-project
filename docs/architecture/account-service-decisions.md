# account-service — Sprint Decision Record (FR v8-1 ACCT scope)

Recorded 2026-07-23. **The long-term authorities are ADR-013 (boundary/API/data
ownership) and ADR-014 (KR-11 Account Number generation), plus the ADR-010
addendum (account-service → customer-service authentication).** This document
only records how the Phase 0 blocking questions (B-1..B-4) were answered by the
analysts/team and the details that fed those ADRs — if any wording here ever
disagrees with the ADRs, **the ADRs govern**. Do not extend this file with new
architecture decisions; open a new ADR instead.

## K-8 (was B-1) — Automatic 223 Customer Account: APPROVED

One 223 Customer Account per customer, created **lazily** the moment the
customer's **first 224 Billing Account** is created (use-case FR-ACCT-02 steps
8–8.3), inside the **same local ACID transaction** as the 224. Details (all
normative text in ADR-013 §4):

- `account_type` 223; real, unique, Luhn-checked KR-11 number from the **same
  generator/sequence** as 224s (the `[T]` digit encodes customer segment, not
  account type).
- `account_name` = fixed system constant **"Customer Account"**; never
  user-editable, never surfaced in UI-facing lists.
- `address_id` = the customer's **primary active address**, resolved through
  the same customer-service address validation path as the 224 being created.
  Never nullable/blank.
- At most one 223 per customer — partial unique index (ADR-013 §4.4).
- Pure side effect: no endpoint creates/lists/updates/deletes a 223 directly;
  it never appears in `GET /api/accounts`, and its number answers 404 on the
  single-account endpoints. Immutable once created; follows the same
  soft-passivation rules as any account row.

## Passive account policy (was B-2)

- `GET /api/accounts/{accountNumber}` on a Passive 224: **allowed**, read-only,
  full detail payload (Passive rows stay list-visible; history must remain
  viewable per AC-ACCT-04-03).
- `PUT` on a Passive account: **409 `MSG-ACCT-NOT-ACTIVE`** — the whole update
  is refused; nothing is silently ignored.
- `DELETE` on an already-Passive account: **409 `MSG-ACCT-NOT-ACTIVE`** — not
  idempotent-204, not 404: the record demonstrably still exists and is visible.

## Product-involvement activity (was B-3)

`cust_acct_prod_invl` is **real, queried local state** — the sole source of
truth for the 409 `MSG-ACCT-HAS-PRODUCTS` delete guard. Active involvement ⇔
row with `status_id = ACTV AND deleted_date IS NULL`. Until product-service
exists the table is populated only via seed data. **Documented follow-up TODO
(deliberately NOT implemented now):** syncing this projection from a future
product-service via an account-service command/API or a consumed event —
never a live cross-service product call in this sprint, and never direct
writes into `account_db` by another service (ADR-013 §5).

## Seed regeneration (was B-4)

The four workbook `CUST_ACCT` rows are re-seeded with KR-11 numbers
(creation year 2026, segment 1, workbook order): 223 → `1261000002`; the three
224s → `1261000010`, `1261000028`, `1261000036`. `acct_number_seq (1, 2026)`
is seeded at `next_value = 100004`. The two workbook `CUST_ACCT_PROD_INVL`
rows map to the regenerated **first Billing Account** (`1261000010`), products
1 and 2, status ACTV. Workbook deviations recorded in ADR-013/ADR-014 and
`docs/requirements/document-delta.md`; the workbook itself is never edited.

## account-service message catalog (EN/TR)

Analyst catalog keys (FR v8-1): `MSG-ACCT-HAS-PRODUCTS`,
`MSG-ACCT-DELETE-CONFIRM` (frontend-only), `MSG-ACCT-DELETED` (frontend-only,
shown after a successful 204). Project additions introduced by this sprint:

| Key | HTTP | EN | TR |
|---|---|---|---|
| `MSG-ACCT-NOT-FOUND` | 404 | Billing account not found. | Fatura hesabı bulunamadı. |
| `MSG-ACCT-NOT-ACTIVE` | 409 | This account is passive and cannot be modified. | Bu hesap pasif durumda olduğundan üzerinde değişiklik yapılamaz. |
| `MSG-ACCT-IMMUTABLE-FIELD` | 400 | The request contains fields that cannot be set or changed. | İstek, atanamayan veya değiştirilemeyen alanlar içeriyor. |
| `MSG-ACCT-DUP-NUMBER` | 409 | An account with this account number already exists. | Bu hesap numarasına sahip bir hesap zaten mevcut. |
| `MSG-ACCT-NUMBER-CAPACITY-EXCEEDED` | 409 | Account number capacity for this segment and year is exhausted. | Bu segment ve yıl için hesap numarası kapasitesi tükendi. |

Shared platform keys reused unchanged: `MSG-VALIDATION-ERROR`,
`MSG-CUST-NOT-FOUND`, `MSG-SERVICE-UNAVAILABLE`, `MSG-INTERNAL-ERROR`,
`MSG-AUTH-UNAUTHORIZED`, `MSG-AUTH-FORBIDDEN`, `MSG-AUTH-CSRF-REJECTED`
(gateway). The backend returns language-neutral `messageKey`s; the EN/TR texts
above are the catalog entries for the future localization work (FR-LANG).
