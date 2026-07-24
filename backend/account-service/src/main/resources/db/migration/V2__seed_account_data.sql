-- Seed from CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx (ACCT scope), with the
-- deviations recorded in ADR-013/ADR-014 and docs/requirements/document-delta.md:
--
--   1. account_number values are REGENERATED to the KR-11 contract (creation year
--      2026, segment 1, Luhn check digit — ADR-014 §8). The workbook's legacy
--      values (0101112900, 0101112911, 0101112915, 0101112441) violate KR-11 and
--      are NOT copied.
--   2. customer_number 1001 replaces the workbook's internal customer_id 1
--      (ADR-013 §2.3): 1001 is the public business number of the same seed
--      customer (Ali Yildiz).
--   3. The 223 row's account_name is the fixed K-8 system constant
--      "Customer Account" (ADR-013 §4.3), not the workbook's per-customer text.
--   4. address_id 1 is the customer's primary address in the customer-service
--      seed — an external reference, no FK (ADR-013 §2.4).
--
-- status_id 1 = ACTV, 2 = PASV — external GNL_ST contract IDs (ADR-002), NOT local rows.

INSERT INTO acct_tp (id, account_type_code, name, description, short_code, status_id, created_date, created_by) VALUES
    (1, '223', 'Customer Account', 'Default customer account', 'CUST_ACCT', 1, '2026-01-01 00:00:01+00', 'system'),
    (2, '224', 'Billing Account',  'Billing account',          'BILL_ACCT', 1, '2026-01-01 00:00:01+00', 'system');

INSERT INTO cust_acct (id, customer_number, account_type_id, account_number, account_name, description, address_id, status_id, created_date, created_by) VALUES
    (1, 1001, 1, '1261000002', 'Customer Account', 'Default customer account', 1, 1, '2026-06-01 10:00:00+00', 'system'),
    (2, 1001, 2, '1261000010', '1261000010',       'Billing account',          1, 1, '2026-06-01 10:05:00+00', 'system'),
    (3, 1001, 2, '1261000028', '1261000028',       'Billing account',          1, 1, '2026-06-01 10:10:00+00', 'system'),
    (4, 1001, 2, '1261000036', '1261000036',       'Billing account',          1, 1, '2026-06-01 10:15:00+00', 'system');

-- Workbook involvement rows: the FIRST Billing Account (workbook id 2, regenerated
-- number 1261000010) carries two ACTIVE product involvements — the fixture that
-- proves the AC-ACCT-04-03 delete guard blocks (409 MSG-ACCT-HAS-PRODUCTS).
INSERT INTO cust_acct_prod_invl (id, customer_account_id, product_id, short_code, status_id, created_date, created_by) VALUES
    (1, 2, 1, 'ACCT_PROD', 1, '2026-06-10 09:00:00+00', 'system'),
    (2, 2, 2, 'ACCT_PROD', 1, '2026-06-10 09:00:00+00', 'system');

-- KR-11 sequence invariant (ADR-014): the four seed accounts consumed
-- 100000..100003 for (segment 1, year 2026), so the NEXT value to issue is 100004.
INSERT INTO acct_number_seq (segment, seq_year, next_value) VALUES
    (1, 2026, 100004);

-- Explicit IDs above: advance the identity sequences past the seeded values.
SELECT setval(pg_get_serial_sequence('cust_acct', 'id'),           (SELECT MAX(id) FROM cust_acct));
SELECT setval(pg_get_serial_sequence('cust_acct_prod_invl', 'id'), (SELECT MAX(id) FROM cust_acct_prod_invl));
