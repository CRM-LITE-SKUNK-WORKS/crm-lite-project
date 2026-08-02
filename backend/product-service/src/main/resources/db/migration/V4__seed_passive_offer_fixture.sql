-- Passive-offer fixture for the FR-SALE write slice (ADR-015 §6).
--
-- WHY: AC-SALE-01-08 requires every basket offer to be Active and names
-- MSG-SALE-OFFER-INACTIVE for the failure, but EVERY seeded offer (workbook V2 and
-- the V3 expansion) is ACTV — so that branch had no fixture to test against. This is
-- the exact same gap, and the exact same remedy, as V2's passive PRODUCT fixture
-- (`ADSL 8MB Legacy`, document-delta P3).
--
-- The workbook is NOT edited and previously applied migrations are NOT touched:
-- forward-only, per CLAUDE.md.
--
-- Deliberately invisible to every existing read: GET /api/offers filters on
-- status_id = ACTV, so the "10 active offers" assertion is unaffected. The row is
-- reachable only by id, which is precisely what the write path does.
--
-- Full soft-delete invariant (PASV + deleted/updated metadata), matching how the
-- passive product fixture was written rather than a status-only row.
--
-- status_id 2 = PASV — an external GNL_ST contract ID (ADR-002), never a local row.
-- Spec 1 = ADSL Internet Service (SERVICE_TYPE 10 = INTERNET), so the fixture also
-- exercises "inactive is reported BEFORE service-type composition" ordering.
-- Price follows the P1/P5 provisional-fixture convention (analyst approval pending).

INSERT INTO prod_ofr (id, product_spec_id, name, description, parent_offer_id, product_offer_total_price,
                      status_id, created_date, created_by, updated_date, updated_by, deleted_date, deleted_by) VALUES
    (11, 1, 'ADSL 4MB Offer (retired)', 'Withdrawn ADSL offer (fixture)', NULL, 199.00,
     2, '2026-01-15 09:10:00+00', 'system', '2026-07-15 09:00:00+00', 'system',
     '2026-07-15 09:00:00+00', 'system');

-- Explicit ID above: advance the identity sequence past the seeded value.
SELECT setval(pg_get_serial_sequence('prod_ofr', 'id'), (SELECT MAX(id) FROM prod_ofr));
