# ADR-014: KR-11 Account Number Generation (Format, Check Digit, Sequence)

## Status
Accepted (2026-07-23) — technical design for FR v8-1 Final (23.07.2026) KR-11,
implemented in account-service (ADR-013).

## Context
KR-11: the Account Number is a 10-digit numeric string
`[T][YY][SSSSSS][C]` — `T` = customer-segment digit (fixed `1` this phase,
individual customers only), `YY` = last two digits of the creation year,
`SSSSSS` = per-segment, per-year sequence starting at `100000`, `C` = check
digit whose calculation method is left to the technical team. Once assigned
the number is immutable and never reused, even after passivation.

## Decision

### 1. Storage
`cust_acct.account_number` is **`VARCHAR(10)` with a database UNIQUE
constraint** — never a numeric column type. A numeric type would invite
arithmetic/formatting on an identifier and could not survive a future segment
digit of `0`. The UNIQUE constraint covers all rows including soft-deleted
ones; passivation never frees a number (same permanence pattern as ADR-003).

### 2. Check digit: Luhn (mod 10)
`C` is the **Luhn check digit** computed over the first nine digits. Standard,
well-understood, catches all single-digit errors and most transpositions.
Canonical worked sample (validated computationally, also a fixed unit-test
vector): payload `126100000` → Luhn sum 8 → check digit **2** → complete
number **`1261000002`**, and the full 10-digit number passes standard Luhn
validation (mod 10 == 0).

### 3. Sequence state: `acct_number_seq`
```sql
CREATE TABLE acct_number_seq (
    segment    SMALLINT NOT NULL,
    seq_year   INTEGER  NOT NULL,   -- full year (e.g. 2026); YY is derived for display
    next_value INTEGER  NOT NULL CHECK (next_value >= 100000),
    PRIMARY KEY (segment, seq_year)
);
```
**Invariant: `next_value` is the NEXT sequence value that will be issued** for
that (segment, year). After the four seed accounts consume
`100000..100003`, the persisted `next_value` is `100004`.

A PostgreSQL native `SEQUENCE` per (segment, year) was rejected: sequences
cannot be created on demand transactionally without DDL-in-transaction
complexity, and their non-transactional semantics create gaps KR-11 does not
need; a keyed row gives exact, inspectable state per segment+year.

### 4. Allocation statement (race-safe, single round trip)
Executed inside the **same transaction** as the account INSERT:

```sql
INSERT INTO acct_number_seq (segment, seq_year, next_value)
VALUES (:segment, :year, 100001)          -- claims 100000; next to issue = 100001
ON CONFLICT (segment, seq_year)
DO UPDATE SET next_value = acct_number_seq.next_value + 1
RETURNING next_value - 1 AS issued_value;
```

**No-off-by-one proof.**
- First caller for a fresh (segment, year): no row exists, the INSERT arm
  fires with `next_value = 100001`; `RETURNING next_value - 1` yields
  **100000** — the exact KR-11 first value. Post-state `next_value = 100001`
  = next to issue: the invariant holds from the first allocation.
- Second caller: conflict → `next_value = 100002`, returns `100001`.
- After four allocations (`100000..100003`) the stored `next_value` is
  **100004**, matching the seed invariant.

**Concurrency.** `INSERT … ON CONFLICT DO UPDATE` locks the target row; a
concurrent caller blocks until the first transaction commits, then its
`DO UPDATE` reads the committed, incremented value — two callers can never
receive the same number (covered by an integration test firing parallel
creates). If the surrounding transaction rolls back, the increment rolls back
with it: allocation is gapless, and a rolled-back number was never visible,
so no "reuse" can occur. The row lock serializes account creation per
(segment, year) — an accepted trade-off at this scale.

### 5. Year and segment inputs
The creation year comes from an **injectable `java.time.Clock`** bean (fixed
clocks in tests; a year rollover automatically starts a fresh sequence row at
100000). The segment digit is the constant `1` this phase — it encodes the
**customer segment**, not the account type, so 223 and 224 accounts share the
same numbering scheme and sequence (K-8). `YY = year % 100`.

### 6. Overflow
If the issued value exceeds `999999`, the service throws a domain
`AccountNumberCapacityExceededException`, mapped to
**409 `MSG-ACCT-NUMBER-CAPACITY-EXCEEDED`** — never a raw 500. The
transaction rolls back (the increment with it).

### 7. Uniqueness fallback
The application never re-issues numbers, but as defence in depth any
`account_number` UNIQUE violation (e.g. an unforeseen race) is translated by
the exception handler to a clean **409 `MSG-ACCT-DUP-NUMBER`**, never a 500
(the ADR-003 pattern).

### 8. Seed regeneration (workbook deviation, recorded)
The workbook's legacy sample numbers (`0101112900`, `0101112911`,
`0101112915`, `0101112441`) violate KR-11 and are **not** copied. The Flyway
seed regenerates them for creation year 2026, segment 1, in workbook row
order:

| Workbook row | Type | KR-11 number |
|---|---|---|
| id 1 (Customer Account) | 223 | `1261000002` |
| id 2 (first Billing) | 224 | `1261000010` |
| id 3 | 224 | `1261000028` |
| id 4 | 224 | `1261000036` |

and seeds `acct_number_seq (1, 2026) = 100004`.

## Consequences
- Numbers are lexicographically ordered within a segment+year, so the
  AC-ACCT-01-04 `accountNumber ASC` sort works directly on the VARCHAR column.
- A future non-`1` segment digit is a configuration/data change, not a schema
  change; per-segment sequences are already isolated by the primary key.
- Capacity is 900,000 accounts per segment per year; exceeding it is a
  documented domain error, visible to analysts long before it is plausible.
