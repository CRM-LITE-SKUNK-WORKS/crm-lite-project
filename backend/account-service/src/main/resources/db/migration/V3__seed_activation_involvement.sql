-- Involvement fixtures for the product-service Phase A slice (FR-PROD-01).
-- These rows belong to account_db because cust_acct_prod_invl is owned and
-- written ONLY by account-service (ADR-013 §5) — product-service's own V2 seed
-- deliberately does NOT touch this table. Committed V1/V2 are never edited;
-- this is a forward-only migration. Deviations recorded in
-- docs/requirements/document-delta.md:
--
--   1. The workbook links only products 1 and 2 to billing account 2
--      (1261000010), leaving product 3 (ADSL Activation) dangling. Row 3 links
--      it to the same account so the seeded ADSL installation is complete.
--   2. Row 4 links product-service's passive-product fixture (prod id 4, PASV —
--      its V2 deviation 3) to the same account with a PASSIVE involvement:
--      deleted_date stays NULL, so the FR-PROD-01 list (which filters only on
--      deleted_date) still shows the product — with Status "Passive" — while the
--      AC-ACCT-04-03 delete guard (which requires ACTV involvement) is unaffected.
--
-- Accounts 3 (1261000028) and 4 (1261000036) deliberately stay product-less:
-- they are the MSG-PROD-NONE fixtures.
--
-- status_id 1 = ACTV, 2 = PASV — external GNL_ST contract IDs (ADR-002).

INSERT INTO cust_acct_prod_invl (id, customer_account_id, product_id, short_code, status_id, created_date, created_by) VALUES
    (3, 2, 3, 'ACCT_PROD', 1, '2026-06-10 09:00:00+00', 'system');

INSERT INTO cust_acct_prod_invl (id, customer_account_id, product_id, short_code, status_id, created_date, created_by, updated_date, updated_by) VALUES
    (4, 2, 4, 'ACCT_PROD', 2, '2026-03-01 09:00:00+00', 'system', '2026-07-01 09:00:00+00', 'system');

-- Explicit IDs above: advance the identity sequence past the seeded values.
SELECT setval(pg_get_serial_sequence('cust_acct_prod_invl', 'id'), (SELECT MAX(id) FROM cust_acct_prod_invl));
