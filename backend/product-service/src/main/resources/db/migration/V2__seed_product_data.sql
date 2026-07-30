-- Seed from CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx (PROD/catalog scope), with
-- the following DOCUMENTED DEVIATIONS (all recorded in
-- docs/requirements/document-delta.md; analyst approval pending):
--
--   1. PRICES ARE INVENTED. The workbook leaves product_offer_total_price empty in
--      all three PROD_OFR rows, but AC-SALE-01-12 requires per-offer amounts and a
--      Total Amount. Chosen fixture values: offer 1 (ADSL 8MB Offer) 299.00,
--      offer 2 (ADSL Data Modem Offer) 149.00, offer 3 (ADSL Activation Offer)
--      49.00. The campaign price is DERIVED (sum of member offers = 497.00) —
--      CMPG deliberately has no price column.
--
--   2. CAMPAIGN FIXTURE. Every workbook PROD row has campaign_id empty, leaving
--      the AC-PROD-01-03 "show campaign when present" branch untestable. Products
--      1 and 2 are linked to campaign 1 (CMP-ADSL-01); product 3 stays
--      campaign-less to keep the "-" branch testable.
--
--   3. PASSIVE PRODUCT FIXTURE. Every workbook product is ACTV, leaving the
--      Status column's "Passive" branch untestable. Product 4 is a NEW row (not
--      in the workbook): a passivated standalone ADSL product following the full
--      soft-delete invariant (status_id = 2 PASV + deleted_date/deleted_by set).
--      Its involvement row lives in account_db (account-service's V3 migration) —
--      product-service NEVER writes account_db (ADR-013 §5).
--
--   4. The workbook's dangling activation product (id 3) is linked to billing
--      account 1261000010 by account-service's OWN V3 migration for the same
--      reason — that row belongs to account_db and is deliberately absent here.
--
-- status_id 1 = ACTV, 2 = PASV — external GNL_ST contract IDs (ADR-002);
-- service_type_id 10/11/12 and catalog_type_id 5 — external GNL_TP contract IDs.
-- NOT local rows.

INSERT INTO prod_spec (id, name, description, service_type_id, is_dev, status_id, created_date, created_by) VALUES
    (1, 'ADSL Internet Service',   'ADSL internet service spec',    10, 0, 1, '2026-06-01 09:00:01+00', 'system'),
    (2, 'ADSL Data Modem',         'ADSL modem device spec',        11, 1, 1, '2026-06-01 09:00:01+00', 'system'),
    (3, 'ADSL Activation Service', 'ADSL activation service spec',  12, 0, 1, '2026-06-01 09:00:01+00', 'system');

-- Deviation 1: prices are invented fixtures (workbook column empty; see header).
INSERT INTO prod_ofr (id, product_spec_id, name, description, parent_offer_id, product_offer_total_price, status_id, created_date, created_by) VALUES
    (1, 1, 'ADSL 8MB Offer',        'ADSL 8MB internet offer',       NULL, 299.00, 1, '2026-06-01 09:10:00+00', 'system'),
    (2, 2, 'ADSL Data Modem Offer', 'ADSL data modem offer',         NULL, 149.00, 1, '2026-06-01 09:10:00+00', 'system'),
    (3, 3, 'ADSL Activation Offer', 'ADSL activation service offer', NULL,  49.00, 1, '2026-06-01 09:10:00+00', 'system');

INSERT INTO cmpg (id, name, campaign_code, description, activation_end_date, status_id, created_date, created_by) VALUES
    (1, 'ADSL Hosgeldin Kampanyasi', 'CMP-ADSL-01', 'ADSL 8MB + modem birlikte', '2026-12-31 23:59:59+00', 1, '2026-06-01 09:00:00+00', 'system');

INSERT INTO cmpg_prod_ofr (id, campaign_id, product_offer_id, is_main, short_code, status_id, created_date, created_by) VALUES
    (1, 1, 1, TRUE,  'CMPG_OFR', 1, '2026-06-01 09:10:00+00', 'system'),
    (2, 1, 2, FALSE, 'CMPG_OFR', 1, '2026-06-01 09:10:00+00', 'system'),
    (3, 1, 3, FALSE, 'CMPG_OFR', 1, '2026-06-01 09:10:00+00', 'system');

-- Deviation 2: products 1 and 2 carry campaign_id = 1 (workbook: empty).
-- service_address_id 1 (external customer_db reference) only on the MAIN product;
-- children (2, 3) display their parent's address (FR-PROD-02 rule).
INSERT INTO prod (id, parent_prod_id, product_offer_id, product_spec_id, name, description, transaction_id, campaign_id, service_start_date, service_address_id, status_id, created_date, created_by) VALUES
    (1, NULL, 1, 1, 'ADSL 8MB',        'ADSL internet product',            NULL, 1,    '2026-06-10', 1,    1, '2026-06-10 09:00:00+00', 'system'),
    (2, 1,    2, 2, 'ADSL Data Modem', 'Modem product linked to ADSL',     NULL, 1,    '2026-06-10', NULL, 1, '2026-06-10 09:00:00+00', 'system'),
    (3, 1,    3, 3, 'ADSL Activation', 'Activation product linked to ADSL', NULL, NULL, '2026-06-10', NULL, 1, '2026-06-10 09:00:00+00', 'system');

-- Deviation 3: passive product fixture (NEW row, not in the workbook) — full
-- soft-delete invariant applied (PASV + deleted/updated metadata).
INSERT INTO prod (id, parent_prod_id, product_offer_id, product_spec_id, name, description, transaction_id, campaign_id, service_start_date, service_address_id, status_id, created_date, created_by, updated_date, updated_by, deleted_date, deleted_by) VALUES
    (4, NULL, 1, 1, 'ADSL 8MB Legacy', 'Passivated ADSL product (fixture)', NULL, NULL, '2026-03-01', 1, 2, '2026-03-01 09:00:00+00', 'system', '2026-07-01 09:00:00+00', 'system', '2026-07-01 09:00:00+00', 'system');

INSERT INTO prod_spec_char (id, name, data_type, description, status_id, created_date, created_by) VALUES
    (1, 'Download Speed (Mbps)', 'NUMBER',  'Indirme hizi',          1, '2026-06-01 09:00:00+00', 'system'),
    (2, 'Static IP',             'BOOLEAN', 'Sabit IP istegi',       1, '2026-06-01 09:00:00+00', 'system'),
    (3, 'MAC Address',           'TEXT',    'Cihaz MAC adresi',      1, '2026-06-01 09:00:00+00', 'system'),
    (4, 'Commitment End Date',   'DATE',    'Taahhut bitis tarihi',  1, '2026-06-01 09:00:00+00', 'system');

INSERT INTO prod_spec_char_use (id, product_spec_id, product_spec_char_id, is_mandatory, status_id, created_date, created_by) VALUES
    (1, 1, 1, TRUE,  1, '2026-06-01 09:00:00+00', 'system'),
    (2, 1, 2, FALSE, 1, '2026-06-01 09:00:00+00', 'system'),
    (3, 1, 4, FALSE, 1, '2026-06-01 09:00:00+00', 'system'),
    (4, 2, 3, TRUE,  1, '2026-06-01 09:00:00+00', 'system');

INSERT INTO prod_char_val (id, product_id, product_spec_char_id, val, status_id, created_date, created_by) VALUES
    (1, 1, 1, '8',                 1, '2026-06-10 09:00:00+00', 'system'),
    (2, 1, 2, 'FALSE',             1, '2026-06-10 09:00:00+00', 'system'),
    (3, 2, 3, 'A1:B2:C3:D4:E5:F6', 1, '2026-06-10 09:00:00+00', 'system');

INSERT INTO prod_catal (id, name, description, catalog_type_id, short_code, status_id, created_date, created_by) VALUES
    (1, 'Bireysel Katalog', 'Bireysel musteri katalogu', 5, 'CATAL', 1, '2026-06-01 09:00:00+00', 'system');

INSERT INTO prod_catal_prod_ofr (id, product_catalog_id, product_offer_id, short_code, status_id, created_date, created_by) VALUES
    (1, 1, 1, 'CATAL_OFR', 1, '2026-06-01 09:10:00+00', 'system'),
    (2, 1, 2, 'CATAL_OFR', 1, '2026-06-01 09:10:00+00', 'system'),
    (3, 1, 3, 'CATAL_OFR', 1, '2026-06-01 09:10:00+00', 'system');

-- Explicit IDs above: advance every identity sequence past the seeded values.
SELECT setval(pg_get_serial_sequence('prod_spec', 'id'),           (SELECT MAX(id) FROM prod_spec));
SELECT setval(pg_get_serial_sequence('prod_ofr', 'id'),            (SELECT MAX(id) FROM prod_ofr));
SELECT setval(pg_get_serial_sequence('cmpg', 'id'),                (SELECT MAX(id) FROM cmpg));
SELECT setval(pg_get_serial_sequence('cmpg_prod_ofr', 'id'),       (SELECT MAX(id) FROM cmpg_prod_ofr));
SELECT setval(pg_get_serial_sequence('prod', 'id'),                (SELECT MAX(id) FROM prod));
SELECT setval(pg_get_serial_sequence('prod_spec_char', 'id'),      (SELECT MAX(id) FROM prod_spec_char));
SELECT setval(pg_get_serial_sequence('prod_spec_char_use', 'id'),  (SELECT MAX(id) FROM prod_spec_char_use));
SELECT setval(pg_get_serial_sequence('prod_char_val', 'id'),       (SELECT MAX(id) FROM prod_char_val));
SELECT setval(pg_get_serial_sequence('prod_catal', 'id'),          (SELECT MAX(id) FROM prod_catal));
SELECT setval(pg_get_serial_sequence('prod_catal_prod_ofr', 'id'), (SELECT MAX(id) FROM prod_catal_prod_ofr));
