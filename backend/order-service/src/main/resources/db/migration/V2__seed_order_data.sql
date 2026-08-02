-- Workbook seed for the order domain, adapted per ADR-016 §8.5.
--
-- The workbook's single sample order is:
--   BSN_INTER      (1, customer_account_id=2, bsn_inter_type_id=7 NEWSALE,
--                   'Ali Yildiz yeni urun satisi islemi', status_id=4 MIDLWARE)
--   CUST_ORD       (1, order_number=5001, customer_id=1, bsn_inter_id=1, status_id=4)
--   CUST_ORD_ITEM  (1..3, offers 1/2/3 -> products 1/2/3, status_id=1 ACTV)
--
-- THREE recorded deviations (the workbook itself is NEVER edited):
--
--   1. order_number 5001 does NOT satisfy KR-12 and is NOT copied verbatim. It is
--      regenerated from the production generator for segment 1, year 2026, first
--      sequence value 100000: payload "126100000" -> Luhn check digit 2 ->
--      1261000002. Exactly how ADR-014 §8 regenerated the CUST_ACCT seed.
--      ⚠️ This value is IDENTICAL to seed account 1261000002's number — the
--      documented, accepted consequence of reusing the KR-11 shape (ADR-016 §8.1).
--      Different database, different service, no shared namespace.
--
--   2. customer_account_id=2 becomes customer_account_number '1261000010' and
--      customer_id=1 becomes customer_number 1001 — the public business numbers of
--      the same rows in account_db / customer_db (ADR-016 §2.3). Internal ids never
--      leave the service that owns them.
--
--   3. amount / total_amount are project-added columns the workbook does not have
--      (ADR-016 §2.4). Values mirror product-service's seeded offer prices
--      (299.00 + 149.00 + 49.00 = 497.00), which are themselves PROVISIONAL
--      fixtures pending analyst approval (document-delta P1, open conflict #10).
--
-- status_id / bsn_inter_type_id below are external GNL_ST / GNL_TP contract IDs
-- (ADR-002), never local rows: 1=ACTV (GENERAL), 4=MIDLWARE (ORDER), 7=NEWSALE.
--
-- The seeded order is deliberately left in MIDLWARE — no AC moves an order out of
-- it (ADR-016 §6), so this is a COMPLETE order, not an in-flight one.

INSERT INTO bsn_inter (id, customer_account_number, bsn_inter_type_id, description, status_id,
                       created_date, created_by) VALUES
    (1, '1261000010', 7, 'Ali Yildiz yeni urun satisi islemi', 4, '2026-06-10 09:00:00+00', 'system');

INSERT INTO cust_ord (id, order_number, customer_number, bsn_inter_id, total_amount, status_id,
                      created_date, created_by) VALUES
    (1, '1261000002', 1001, 1, 497.00, 4, '2026-06-10 09:00:00+00', 'system');

-- product_id values reference product-service's V2 seed (products 1/2/3 = the ADSL
-- installation of account 1261000010). No cross-database FK — external refs only.
INSERT INTO cust_ord_item (id, cust_ord_id, product_offer_id, product_id, amount, status_id,
                           created_date, created_by) VALUES
    (1, 1, 1, 1, 299.00, 1, '2026-06-10 09:00:00+00', 'system'),
    (2, 1, 2, 2, 149.00, 1, '2026-06-10 09:00:00+00', 'system'),
    (3, 1, 3, 3,  49.00, 1, '2026-06-10 09:00:00+00', 'system');

-- KR-12 sequence invariant (ADR-016 §4.4): the seed order consumed 100000 for
-- (segment 1, year 2026), so the NEXT value to issue is 100001 — which formats to
-- 1261000010. (Yes, also an existing account number; see deviation 1.)
INSERT INTO order_number_seq (segment, seq_year, next_value) VALUES
    (1, 2026, 100001);

-- Explicit IDs above: advance the identity sequences past the seeded values.
SELECT setval(pg_get_serial_sequence('bsn_inter', 'id'),     (SELECT MAX(id) FROM bsn_inter));
SELECT setval(pg_get_serial_sequence('cust_ord', 'id'),      (SELECT MAX(id) FROM cust_ord));
SELECT setval(pg_get_serial_sequence('cust_ord_item', 'id'), (SELECT MAX(id) FROM cust_ord_item));
