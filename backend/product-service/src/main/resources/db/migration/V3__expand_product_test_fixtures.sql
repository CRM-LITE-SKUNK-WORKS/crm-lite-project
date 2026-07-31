-- Project-added local dev/demo fixture expansion (not analyst-approved commercial or
-- production data — extends the same open conflict recorded in
-- docs/requirements/document-delta.md as the V2 ADSL prices). Adds a Fiber Internet
-- family (reusing the existing approved SERVICE_TYPE contract rows 10/11/12 —
-- INTERNET/RESOURCE/ACTIVATION; no new shared lookup type is introduced) plus two
-- extra ADSL offers, three more ACTIVE campaigns (all with a future
-- activation_end_date — no expired or passive campaign fixtures are added), and
-- 13 more product instances. See docs/testing/seed-fixture-catalog.md.
--
-- No applied migration is modified (V1/V2 untouched, including the existing passive
-- product 4 "ADSL 8MB Legacy" and its account-service involvement row, neither of
-- which is changed or expanded here).
--
-- Prices follow the same relative-positioning convention as V2: slower/basic
-- internet < faster/premium internet; device/resource offer priced separately;
-- activation priced below the recurring main service.
--   Fiber 100MB Offer   399.00 < Fiber 500MB Offer   599.00 < Fiber 1000MB Offer 799.00
--   Fiber Wi-Fi 6 Modem Offer 249.00 (device)
--   Fiber Activation Offer     79.00 (activation, below any Fiber internet offer)
--   ADSL 16MB Offer 349.00 < ADSL 24MB Offer 379.00 (both above the existing ADSL 8MB 299.00)
-- Campaign totals are DERIVED from active member offers (cmpg has no price column):
--   CMP-FIBER-01 = 399.00 + 249.00 + 79.00 = 727.00
--   CMP-FIBER-02 = 599.00 + 249.00 + 79.00 = 927.00
--   CMP-ADSL-02  = 349.00 + 149.00 + 49.00 = 547.00  (reuses existing offers 2 and 3)
--
-- status_id 1 = ACTV — external GNL_ST contract ID (ADR-002); service_type_id
-- 10/11/12 and catalog_type_id 5 — external GNL_TP contract IDs. NOT local rows.

INSERT INTO prod_spec (id, name, description, service_type_id, is_dev, status_id, created_date, created_by) VALUES
    (4, 'Fiber Internet Service',   'Fiber internet service spec',   10, 0, 1, '2026-07-01 09:00:01+00', 'system'),
    (5, 'Fiber Wi-Fi 6 Modem',      'Fiber Wi-Fi 6 modem device spec', 11, 1, 1, '2026-07-01 09:00:01+00', 'system'),
    (6, 'Fiber Activation Service', 'Fiber activation service spec', 12, 0, 1, '2026-07-01 09:00:01+00', 'system');

INSERT INTO prod_ofr (id, product_spec_id, name, description, parent_offer_id, product_offer_total_price, status_id, created_date, created_by) VALUES
    (4,  4, 'Fiber 100MB Offer',        'Fiber 100MB internet offer',        NULL, 399.00, 1, '2026-07-01 09:10:00+00', 'system'),
    (5,  4, 'Fiber 500MB Offer',        'Fiber 500MB internet offer',        NULL, 599.00, 1, '2026-07-01 09:10:00+00', 'system'),
    (6,  4, 'Fiber 1000MB Offer',       'Fiber 1000MB internet offer',       NULL, 799.00, 1, '2026-07-01 09:10:00+00', 'system'),
    (7,  5, 'Fiber Wi-Fi 6 Modem Offer','Fiber Wi-Fi 6 modem offer',         NULL, 249.00, 1, '2026-07-01 09:10:00+00', 'system'),
    (8,  6, 'Fiber Activation Offer',   'Fiber activation service offer',    NULL,  79.00, 1, '2026-07-01 09:10:00+00', 'system'),
    (9,  1, 'ADSL 16MB Offer',          'ADSL 16MB internet offer',          NULL, 349.00, 1, '2026-07-01 09:10:00+00', 'system'),
    (10, 1, 'ADSL 24MB Offer',          'ADSL 24MB internet offer',          NULL, 379.00, 1, '2026-07-01 09:10:00+00', 'system');

-- 3 active campaigns, all main-internet + resource + activation triples (AC-SALE-01-08
-- basket shape). Future activation_end_date only — expired/passive campaigns are
-- explicitly out of scope for this fixture expansion.
INSERT INTO cmpg (id, name, campaign_code, description, activation_end_date, status_id, created_date, created_by) VALUES
    (2, 'Fiber Baslangic Kampanyasi', 'CMP-FIBER-01', 'Fiber 100MB + modem + aktivasyon', '2027-06-30 23:59:59+00', 1, '2026-07-01 09:00:00+00', 'system'),
    (3, 'Fiber Hizli Kampanya',       'CMP-FIBER-02', 'Fiber 500MB + modem + aktivasyon', '2027-06-30 23:59:59+00', 1, '2026-07-01 09:00:01+00', 'system'),
    (4, 'ADSL Hiz Yukseltme Kampanyasi', 'CMP-ADSL-02', 'ADSL 16MB + modem + aktivasyon', '2027-06-30 23:59:59+00', 1, '2026-07-01 09:00:02+00', 'system');

INSERT INTO cmpg_prod_ofr (id, campaign_id, product_offer_id, is_main, short_code, status_id, created_date, created_by) VALUES
    (4,  2, 4, TRUE,  'CMPG_OFR', 1, '2026-07-01 09:10:00+00', 'system'),
    (5,  2, 7, FALSE, 'CMPG_OFR', 1, '2026-07-01 09:10:00+00', 'system'),
    (6,  2, 8, FALSE, 'CMPG_OFR', 1, '2026-07-01 09:10:00+00', 'system'),
    (7,  3, 5, TRUE,  'CMPG_OFR', 1, '2026-07-01 09:10:01+00', 'system'),
    (8,  3, 7, FALSE, 'CMPG_OFR', 1, '2026-07-01 09:10:01+00', 'system'),
    (9,  3, 8, FALSE, 'CMPG_OFR', 1, '2026-07-01 09:10:01+00', 'system'),
    (10, 4, 9, TRUE,  'CMPG_OFR', 1, '2026-07-01 09:10:02+00', 'system'),
    (11, 4, 2, FALSE, 'CMPG_OFR', 1, '2026-07-01 09:10:02+00', 'system'),
    (12, 4, 3, FALSE, 'CMPG_OFR', 1, '2026-07-01 09:10:02+00', 'system');

-- 13 new product instances (5-17), 17 total with the existing 4. Cross-service
-- fixture contract: service_address_id references customer-service addr ids
-- 4/5/7/8/9/11 (external, no FK — see docs/testing/seed-fixture-catalog.md).
-- Only MAIN products carry service_address_id; children resolve their parent's
-- address (FR-PROD-02, ProductBusinessRules.resolveEffectiveServiceAddressId).
INSERT INTO prod (id, parent_prod_id, product_offer_id, product_spec_id, name, description, transaction_id, campaign_id, service_start_date, service_address_id, status_id, created_date, created_by) VALUES
    -- Customer 1004, billing account 1261000051: Fiber 100 family (CMP-FIBER-01).
    (5,  NULL, 4,  4, 'Fiber 100MB',            'Fiber internet product',                 NULL, 2,    '2026-07-05', 4,    1, '2026-07-05 09:00:00+00', 'system'),
    (6,  5,    7,  5, 'Fiber Wi-Fi 6 Modem',    'Fiber modem linked to Fiber 100MB',      NULL, 2,    '2026-07-05', NULL, 1, '2026-07-05 09:00:00+00', 'system'),
    (7,  5,    8,  6, 'Fiber Activation',       'Fiber activation linked to Fiber 100MB', NULL, NULL, '2026-07-05', NULL, 1, '2026-07-05 09:00:00+00', 'system'),
    -- Customer 1004, billing account 1261000069 (2nd billing account): campaign-less.
    (8,  NULL, 10, 1, 'ADSL 24MB',              'Standalone ADSL 24MB product (no campaign)', NULL, NULL, '2026-07-06', 4, 1, '2026-07-06 09:00:00+00', 'system'),
    -- Customer 1005, billing account 1261000085: Fiber 500 family (CMP-FIBER-02).
    (9,  NULL, 5,  4, 'Fiber 500MB',            'Fiber internet product',                 NULL, 3,    '2026-07-08', 5,    1, '2026-07-08 09:00:00+00', 'system'),
    (10, 9,    7,  5, 'Fiber Wi-Fi 6 Modem',    'Fiber modem linked to Fiber 500MB',      NULL, 3,    '2026-07-08', NULL, 1, '2026-07-08 09:00:00+00', 'system'),
    (11, 9,    8,  6, 'Fiber Activation',       'Fiber activation linked to Fiber 500MB', NULL, NULL, '2026-07-08', NULL, 1, '2026-07-08 09:00:00+00', 'system'),
    -- Customer 1006, billing account 1261000101: single campaign-less product.
    (12, NULL, 9,  1, 'ADSL 16MB',              'Standalone ADSL 16MB product (no campaign)', NULL, NULL, '2026-07-10', 7, 1, '2026-07-10 09:00:00+00', 'system'),
    -- Customer 1007, billing account 1261000127: two independent families sharing
    -- one billing account, using two different service addresses.
    (13, NULL, 6,  4, 'Fiber 1000MB',           'Standalone Fiber 1000MB product (no campaign)', NULL, NULL, '2026-07-12', 8, 1, '2026-07-12 09:00:00+00', 'system'),
    (14, NULL, 9,  1, 'ADSL 16MB',              'ADSL internet product (CMP-ADSL-02)',    NULL, 4,    '2026-07-12', 9,    1, '2026-07-12 09:05:00+00', 'system'),
    (15, 14,   2,  2, 'ADSL Data Modem',        'Modem product linked to ADSL 16MB',      NULL, 4,    '2026-07-12', NULL, 1, '2026-07-12 09:05:00+00', 'system'),
    (16, 14,   3,  3, 'ADSL Activation',        'Activation product linked to ADSL 16MB', NULL, NULL, '2026-07-12', NULL, 1, '2026-07-12 09:05:00+00', 'system'),
    -- Customer 1008, billing account 1261000150: single campaign-less product, using
    -- a campaign-eligible offer purchased OUTSIDE a campaign (campaign_id stays NULL).
    (17, NULL, 4,  4, 'Fiber 100MB',            'Standalone Fiber 100MB product (no campaign)', NULL, NULL, '2026-07-14', 11, 1, '2026-07-14 09:00:00+00', 'system');

INSERT INTO prod_spec_char_use (id, product_spec_id, product_spec_char_id, is_mandatory, status_id, created_date, created_by) VALUES
    (5, 4, 1, TRUE,  1, '2026-07-01 09:00:00+00', 'system'),
    (6, 4, 2, FALSE, 1, '2026-07-01 09:00:00+00', 'system'),
    (7, 4, 4, FALSE, 1, '2026-07-01 09:00:00+00', 'system'),
    (8, 5, 3, TRUE,  1, '2026-07-01 09:00:00+00', 'system');

INSERT INTO prod_char_val (id, product_id, product_spec_char_id, val, status_id, created_date, created_by) VALUES
    (4,  5,  1, '100',               1, '2026-07-05 09:00:00+00', 'system'),
    (5,  5,  2, 'FALSE',             1, '2026-07-05 09:00:00+00', 'system'),
    (6,  6,  3, 'AA:BB:CC:DD:EE:01', 1, '2026-07-05 09:00:00+00', 'system'),
    (7,  9,  1, '500',               1, '2026-07-08 09:00:00+00', 'system'),
    (8,  9,  2, 'TRUE',              1, '2026-07-08 09:00:00+00', 'system'),
    (9,  10, 3, 'AA:BB:CC:DD:EE:02', 1, '2026-07-08 09:00:00+00', 'system'),
    (10, 13, 1, '1000',              1, '2026-07-12 09:00:00+00', 'system'),
    (11, 13, 2, 'FALSE',             1, '2026-07-12 09:00:00+00', 'system'),
    (12, 17, 1, '100',               1, '2026-07-14 09:00:00+00', 'system'),
    (13, 17, 2, 'FALSE',             1, '2026-07-14 09:00:00+00', 'system');

INSERT INTO prod_catal_prod_ofr (id, product_catalog_id, product_offer_id, short_code, status_id, created_date, created_by) VALUES
    (4,  1, 4,  'CATAL_OFR', 1, '2026-07-01 09:10:00+00', 'system'),
    (5,  1, 5,  'CATAL_OFR', 1, '2026-07-01 09:10:00+00', 'system'),
    (6,  1, 6,  'CATAL_OFR', 1, '2026-07-01 09:10:00+00', 'system'),
    (7,  1, 7,  'CATAL_OFR', 1, '2026-07-01 09:10:00+00', 'system'),
    (8,  1, 8,  'CATAL_OFR', 1, '2026-07-01 09:10:00+00', 'system'),
    (9,  1, 9,  'CATAL_OFR', 1, '2026-07-01 09:10:00+00', 'system'),
    (10, 1, 10, 'CATAL_OFR', 1, '2026-07-01 09:10:00+00', 'system');

-- Explicit IDs above: advance every identity sequence past the seeded values.
SELECT setval(pg_get_serial_sequence('prod_spec', 'id'),           (SELECT MAX(id) FROM prod_spec));
SELECT setval(pg_get_serial_sequence('prod_ofr', 'id'),            (SELECT MAX(id) FROM prod_ofr));
SELECT setval(pg_get_serial_sequence('cmpg', 'id'),                (SELECT MAX(id) FROM cmpg));
SELECT setval(pg_get_serial_sequence('cmpg_prod_ofr', 'id'),       (SELECT MAX(id) FROM cmpg_prod_ofr));
SELECT setval(pg_get_serial_sequence('prod', 'id'),                (SELECT MAX(id) FROM prod));
SELECT setval(pg_get_serial_sequence('prod_spec_char_use', 'id'),  (SELECT MAX(id) FROM prod_spec_char_use));
SELECT setval(pg_get_serial_sequence('prod_char_val', 'id'),       (SELECT MAX(id) FROM prod_char_val));
SELECT setval(pg_get_serial_sequence('prod_catal_prod_ofr', 'id'), (SELECT MAX(id) FROM prod_catal_prod_ofr));
