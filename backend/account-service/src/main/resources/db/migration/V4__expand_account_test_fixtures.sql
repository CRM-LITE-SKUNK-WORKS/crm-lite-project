-- Project-added local dev/demo fixture expansion (not analyst-approved commercial or
-- production data). Extends the V2/V3 seed (customer 1001's 4 accounts) with 14 more
-- accounts across customers 1004-1008 and 1011 (customer-service V3 fixtures),
-- covering: a customer with multiple Billing Accounts (1004), a Billing Account with
-- no product involvement (1007's third account, 1011's second account), accounts
-- with one product, an account with several independent product families (1007's
-- second account), and the K-8 automatic 223 Customer Account for every applicable
-- customer (never listed, never API-visible — ADR-013 §4.5).
--
-- No applied migration is modified (V1/V2/V3 untouched). This is forward-only.
--
-- Account numbers are derived from the SAME KR-11 generator/Luhn algorithm as
-- production code (AccountNumberGenerator.format / LuhnCheckDigit — ADR-014),
-- verified against a transliteration of that exact algorithm before being committed
-- here (see docs/testing/seed-fixture-catalog.md for the verification method).
-- Segment 1 (individual, ADR-014 §5), creation year 2026, contiguously continuing
-- the V2 sequence from 100004 through 100017 (V2 seeded acct_number_seq(1,2026) =
-- 100004 as the next value to issue).
--
-- cust_acct_prod_invl rows below are the ONLY place the product<->account link is
-- written for these fixtures (ADR-013 §5) — product-service's own V3 migration does
-- not touch account_db, and this migration does not touch product_db.
--
-- status_id 1 = ACTV — external GNL_ST contract ID (ADR-002), NOT a local row.
-- customer_number values (1004-1008, 1011) are external references to
-- customer-service's V3 fixtures — no cross-database FK.

INSERT INTO cust_acct (id, customer_number, account_type_id, account_number, account_name, description, address_id, status_id, created_date, created_by) VALUES
    -- Customer 1004: K-8 223 + two Billing Accounts (multiple-BA scenario).
    (5,  1004, 1, '1261000044', 'Customer Account', 'Default customer account', 4, 1, '2026-07-05 10:00:00+00', 'system'),
    (6,  1004, 2, '1261000051', '1261000051',        'Billing account',          4, 1, '2026-07-05 10:05:00+00', 'system'),
    (7,  1004, 2, '1261000069', '1261000069',        'Billing account',          4, 1, '2026-07-06 10:00:00+00', 'system'),
    -- Customer 1005: K-8 223 + one Billing Account (parent + 2 children family).
    (8,  1005, 1, '1261000077', 'Customer Account', 'Default customer account', 5, 1, '2026-07-08 10:00:00+00', 'system'),
    (9,  1005, 2, '1261000085', '1261000085',        'Billing account',          5, 1, '2026-07-08 10:05:00+00', 'system'),
    -- Customer 1006: K-8 223 + one Billing Account (single campaign-less product).
    (10, 1006, 1, '1261000093', 'Customer Account', 'Default customer account', 7, 1, '2026-07-10 10:00:00+00', 'system'),
    (11, 1006, 2, '1261000101', '1261000101',        'Billing account',          7, 1, '2026-07-10 10:05:00+00', 'system'),
    -- Customer 1007: K-8 223 + two Billing Accounts (one with two independent
    -- families across two service addresses, one with NO product involvement).
    (12, 1007, 1, '1261000119', 'Customer Account', 'Default customer account', 8, 1, '2026-07-12 10:00:00+00', 'system'),
    (13, 1007, 2, '1261000127', '1261000127',        'Billing account',          8, 1, '2026-07-12 10:05:00+00', 'system'),
    (14, 1007, 2, '1261000135', '1261000135',        'Billing account',          9, 1, '2026-07-12 10:10:00+00', 'system'),
    -- Customer 1008: K-8 223 + one Billing Account (single campaign-less product).
    (15, 1008, 1, '1261000143', 'Customer Account', 'Default customer account', 11, 1, '2026-07-14 10:00:00+00', 'system'),
    (16, 1008, 2, '1261000150', '1261000150',        'Billing account',          11, 1, '2026-07-14 10:05:00+00', 'system'),
    -- Customer 1011: K-8 223 + one Billing Account with NO product involvement.
    (17, 1011, 1, '1261000168', 'Customer Account', 'Default customer account', 14, 1, '2026-07-15 10:00:00+00', 'system'),
    (18, 1011, 2, '1261000176', '1261000176',        'Billing account',          14, 1, '2026-07-15 10:05:00+00', 'system');

-- Product involvement (external product_id references product-service's V3
-- fixtures — no FK, no cross-database access). Accounts 1261000135 and 1261000176
-- deliberately stay product-less (MSG-PROD-NONE fixtures, same pattern as V2/V3's
-- 1261000028).
INSERT INTO cust_acct_prod_invl (id, customer_account_id, product_id, short_code, status_id, created_date, created_by) VALUES
    (5,  6,  5,  'ACCT_PROD', 1, '2026-07-05 09:00:00+00', 'system'),
    (6,  6,  6,  'ACCT_PROD', 1, '2026-07-05 09:00:00+00', 'system'),
    (7,  6,  7,  'ACCT_PROD', 1, '2026-07-05 09:00:00+00', 'system'),
    (8,  7,  8,  'ACCT_PROD', 1, '2026-07-06 09:00:00+00', 'system'),
    (9,  9,  9,  'ACCT_PROD', 1, '2026-07-08 09:00:00+00', 'system'),
    (10, 9,  10, 'ACCT_PROD', 1, '2026-07-08 09:00:00+00', 'system'),
    (11, 9,  11, 'ACCT_PROD', 1, '2026-07-08 09:00:00+00', 'system'),
    (12, 11, 12, 'ACCT_PROD', 1, '2026-07-10 09:00:00+00', 'system'),
    (13, 13, 13, 'ACCT_PROD', 1, '2026-07-12 09:00:00+00', 'system'),
    (14, 13, 14, 'ACCT_PROD', 1, '2026-07-12 09:05:00+00', 'system'),
    (15, 13, 15, 'ACCT_PROD', 1, '2026-07-12 09:05:00+00', 'system'),
    (16, 13, 16, 'ACCT_PROD', 1, '2026-07-12 09:05:00+00', 'system'),
    (17, 16, 17, 'ACCT_PROD', 1, '2026-07-14 09:00:00+00', 'system');

-- KR-11 sequence invariant (ADR-014): fixtures consume 100004..100017 for
-- (segment 1, year 2026), so the NEXT value to issue is 100018. GREATEST never
-- decreases the sequence (safe even if run against a state where it was already
-- advanced further by runtime creates in a long-lived dev environment).
UPDATE acct_number_seq SET next_value = GREATEST(next_value, 100018)
 WHERE segment = 1 AND seq_year = 2026;

-- Explicit IDs above: advance the identity sequences past the seeded values.
SELECT setval(pg_get_serial_sequence('cust_acct', 'id'),           (SELECT MAX(id) FROM cust_acct));
SELECT setval(pg_get_serial_sequence('cust_acct_prod_invl', 'id'), (SELECT MAX(id) FROM cust_acct_prod_invl));
