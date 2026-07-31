-- Project-added local dev/demo fixture expansion (not analyst-approved commercial or
-- production data). Extends the V2 seed (customers 1001-1003) with 8 more active
-- customers, 1004-1011, covering FR-CUST-01 search branches: middle-name presence,
-- shared last name, GSM prefix grouping, multiple addresses with exactly one active
-- primary, nullable home_phone/fax, a fully populated contact record, and city-district
-- variation. Also substantially grows the city/district REFERENCE catalog itself
-- (city/district are owned locally by customer-service, not a shared GNL catalog —
-- ADR-002 is unaffected): 7 more cities (10 total) and 19 more districts (24 total),
-- covering Turkey's next most populous cities so address forms/dropdowns have
-- realistic variety to develop against. Unlike the customer/account/product fixtures,
-- these catalog rows are reference data, not all individually exercised by a seeded
-- customer address — same pattern as the shared GNL_ST/GNL_TP catalogs, where not
-- every contract row needs to be referenced by a business row to earn its place.
-- See docs/testing/seed-fixture-catalog.md.
--
-- No applied migration is modified; this is forward-only (V1/V2 untouched).
-- party_type_id 3 = GNL_TP INDV, gender_id 1 = MALE / 2 = FEMALE, status_id 1 = ACTV
-- (external GNL_TP/GNL_ST contract IDs, ADR-002 — no local copies, no FK).
-- Nationality IDs are synthetic 11-digit values, globally unique per ADR-003, encoding
-- the fixture's own customer number for traceability (never a real TCKN).

INSERT INTO city (id, name, status_id, created_date, created_by) VALUES
    (3,  'Izmir',     1, '2026-01-01 00:00:00+00', 'system'),
    (4,  'Bursa',      1, '2026-01-01 00:00:00+00', 'system'),
    (5,  'Antalya',    1, '2026-01-01 00:00:00+00', 'system'),
    (6,  'Adana',      1, '2026-01-01 00:00:00+00', 'system'),
    (7,  'Konya',      1, '2026-01-01 00:00:00+00', 'system'),
    (8,  'Gaziantep',  1, '2026-01-01 00:00:00+00', 'system'),
    (9,  'Mersin',     1, '2026-01-01 00:00:00+00', 'system'),
    (10, 'Kayseri',    1, '2026-01-01 00:00:00+00', 'system');

INSERT INTO district (id, city_id, name, status_id, created_date, created_by) VALUES
    (4,  1,  'Uskudar',     1, '2026-01-01 00:00:00+00', 'system'),
    (5,  3,  'Konak',       1, '2026-01-01 00:00:00+00', 'system'),
    (6,  1,  'Sisli',       1, '2026-01-01 00:00:00+00', 'system'),
    (7,  2,  'Kecioren',    1, '2026-01-01 00:00:00+00', 'system'),
    (8,  2,  'Yenimahalle', 1, '2026-01-01 00:00:00+00', 'system'),
    (9,  3,  'Bornova',     1, '2026-01-01 00:00:00+00', 'system'),
    (10, 3,  'Karsiyaka',   1, '2026-01-01 00:00:00+00', 'system'),
    (11, 4,  'Osmangazi',   1, '2026-01-01 00:00:00+00', 'system'),
    (12, 4,  'Nilufer',     1, '2026-01-01 00:00:00+00', 'system'),
    (13, 5,  'Muratpasa',   1, '2026-01-01 00:00:00+00', 'system'),
    (14, 5,  'Konyaalti',   1, '2026-01-01 00:00:00+00', 'system'),
    (15, 6,  'Seyhan',      1, '2026-01-01 00:00:00+00', 'system'),
    (16, 6,  'Yuregir',     1, '2026-01-01 00:00:00+00', 'system'),
    (17, 7,  'Selcuklu',    1, '2026-01-01 00:00:00+00', 'system'),
    (18, 7,  'Meram',       1, '2026-01-01 00:00:00+00', 'system'),
    (19, 8,  'Sahinbey',    1, '2026-01-01 00:00:00+00', 'system'),
    (20, 8,  'Sehitkamil',  1, '2026-01-01 00:00:00+00', 'system'),
    (21, 9,  'Akdeniz',     1, '2026-01-01 00:00:00+00', 'system'),
    (22, 9,  'Yenisehir',   1, '2026-01-01 00:00:00+00', 'system'),
    (23, 10, 'Melikgazi',   1, '2026-01-01 00:00:00+00', 'system'),
    (24, 10, 'Kocasinan',   1, '2026-01-01 00:00:00+00', 'system');

INSERT INTO party (id, party_type_id, status_id, created_date, created_by) VALUES
    (4,  3, 1, '2026-05-01 10:00:00+00', 'system'),
    (5,  3, 1, '2026-05-02 10:00:00+00', 'system'),
    (6,  3, 1, '2026-05-03 10:00:00+00', 'system'),
    (7,  3, 1, '2026-05-04 10:00:00+00', 'system'),
    (8,  3, 1, '2026-05-05 10:00:00+00', 'system'),
    (9,  3, 1, '2026-05-06 10:00:00+00', 'system'),
    (10, 3, 1, '2026-05-07 10:00:00+00', 'system'),
    (11, 3, 1, '2026-05-08 10:00:00+00', 'system');

INSERT INTO ind (id, party_id, first_name, middle_name, last_name, father_name, mother_name, birth_date, gender_id, nationality_id, status_id, created_date, created_by) VALUES
    (4,  4,  'Ayse',  'Gul',  'Kaya',    'Mustafa', 'Sultan', '1992-03-15', 2, '10000001004', 1, '2026-05-01 10:00:00+00', 'system'),
    (5,  5,  'Mehmet', NULL,  'Yilmaz',  'Hasan',   'Emine',  '1987-08-22', 1, '10000001005', 1, '2026-05-02 10:00:00+00', 'system'),
    (6,  6,  'Elif',   NULL,  'Yilmaz',  'Kemal',   'Nazan',  '1994-01-10', 2, '10000001006', 1, '2026-05-03 10:00:00+00', 'system'),
    (7,  7,  'Ali',    'Kemal','Ozturk', 'Ibrahim', 'Havva',  '1990-06-05', 1, '10000001007', 1, '2026-05-04 10:00:00+00', 'system'),
    (8,  8,  'Fatma', 'Nur',  'Sahin',   'Osman',   'Sevim',  '1996-09-18', 2, '10000001008', 1, '2026-05-05 10:00:00+00', 'system'),
    (9,  9,  'Burak',  NULL,  'Demir',   'Yusuf',   'Gulcan', '1991-12-01', 1, '10000001009', 1, '2026-05-06 10:00:00+00', 'system'),
    (10, 10, 'Selin',  NULL,  'Aydin',   'Cemal',   'Aysel',  '1993-04-27', 2, '10000001010', 1, '2026-05-07 10:00:00+00', 'system'),
    (11, 11, 'Kerem',  'Ali', 'Toprak',  'Metin',   'Nurcan', '1989-11-11', 1, '10000001011', 1, '2026-05-08 10:00:00+00', 'system');

INSERT INTO party_role (id, party_id, role_id, status_id, created_date, created_by) VALUES
    (4,  4,  1, 1, '2026-05-01 10:00:00+00', 'system'),
    (5,  5,  1, 1, '2026-05-02 10:00:00+00', 'system'),
    (6,  6,  1, 1, '2026-05-03 10:00:00+00', 'system'),
    (7,  7,  1, 1, '2026-05-04 10:00:00+00', 'system'),
    (8,  8,  1, 1, '2026-05-05 10:00:00+00', 'system'),
    (9,  9,  1, 1, '2026-05-06 10:00:00+00', 'system'),
    (10, 10, 1, 1, '2026-05-07 10:00:00+00', 'system'),
    (11, 11, 1, 1, '2026-05-08 10:00:00+00', 'system');

INSERT INTO cust (id, customer_number, party_role_id, status_id, created_date, created_by) VALUES
    (4,  1004, 4,  1, '2026-05-01 10:00:00+00', 'system'),
    (5,  1005, 5,  1, '2026-05-02 10:00:00+00', 'system'),
    (6,  1006, 6,  1, '2026-05-03 10:00:00+00', 'system'),
    (7,  1007, 7,  1, '2026-05-04 10:00:00+00', 'system'),
    (8,  1008, 8,  1, '2026-05-05 10:00:00+00', 'system'),
    (9,  1009, 9,  1, '2026-05-06 10:00:00+00', 'system'),
    (10, 1010, 10, 1, '2026-05-07 10:00:00+00', 'system'),
    (11, 1011, 11, 1, '2026-05-08 10:00:00+00', 'system');

-- Addresses: exactly one active primary per party (ux_addr_active_primary).
-- 1004: 1 address (Istanbul/Kadikoy). 1005: 2 addresses, one Istanbul one Ankara.
-- 1006: 2 addresses (Istanbul/Kadikoy primary + Istanbul/Uskudar secondary).
-- 1007: 3 addresses (Istanbul x2 + Ankara). 1008, 1010, 1011: 1 address each.
-- 1009: 2 addresses (Istanbul/Kadikoy primary + Izmir/Konak secondary).
INSERT INTO addr (id, party_id, city_id, district_id, street, house_flat_no, address_description, is_primary, status_id, created_date, created_by) VALUES
    (4,  4,  1, 1, 'Fahrettin Kerim Gokay Cad.', '3',   'Kadikoy ev',    TRUE,  1, '2026-05-01 10:00:00+00', 'system'),
    (5,  5,  1, 2, 'Ciragan Cad.',               '17',  'Besiktas ev',   TRUE,  1, '2026-05-02 10:00:00+00', 'system'),
    (6,  5,  2, 3, 'Kizilirmak Sok.',            '9/2', 'Cankaya ek',    FALSE, 1, '2026-05-02 10:05:00+00', 'system'),
    (7,  6,  1, 1, 'Bahariye Cad.',              '21',  'Kadikoy ev',    TRUE,  1, '2026-05-03 10:00:00+00', 'system'),
    (8,  7,  1, 1, 'Moda Cad.',                  '45',  'Kadikoy ev',    TRUE,  1, '2026-05-04 10:00:00+00', 'system'),
    (9,  7,  1, 2, 'Levent Cad.',                '8',   'Besiktas ofis', FALSE, 1, '2026-05-04 10:05:00+00', 'system'),
    (10, 7,  2, 3, 'Ataturk Bul.',               '112', 'Cankaya ek',    FALSE, 1, '2026-05-04 10:10:00+00', 'system'),
    (11, 8,  1, 2, 'Abbasaga Sok.',              '6',   'Besiktas ev',   TRUE,  1, '2026-05-05 10:00:00+00', 'system'),
    (12, 9,  1, 1, 'Rihtim Cad.',                '14',  'Kadikoy ev',    TRUE,  1, '2026-05-06 10:00:00+00', 'system'),
    (13, 10, 2, 3, 'Bestekar Sok.',              '2',   'Cankaya ev',    TRUE,  1, '2026-05-07 10:00:00+00', 'system'),
    (14, 11, 1, 1, 'Caferaga Sok.',              '30',  'Kadikoy ev',    TRUE,  1, '2026-05-08 10:00:00+00', 'system'),
    (15, 6,  1, 4, 'Hakimiyet Cad.',             '19',  'Uskudar ek',    FALSE, 1, '2026-05-03 10:05:00+00', 'system'),
    (16, 9,  3, 5, 'Kordon Cad.',                '2/3', 'Konak ek',      FALSE, 1, '2026-05-06 10:05:00+00', 'system');

-- Contact medium: one row per party (unique). GSM prefixes deliberately grouped so
-- 0532 resolves to 3 customers (1001, 1007, 1011) and 0533 to 2 (1002, 1006).
-- 1004 is the fully-populated contact (email/home/mobile/fax all set); 1005 has both
-- home_phone and fax NULL (mobile is the only NOT NULL column on this table).
INSERT INTO cntc_medium (id, party_id, email, home_phone, mobile_phone, fax, status_id, created_date, created_by) VALUES
    (3,  4,  'ayse.kaya@example.com',    '02161112244', '05351112244', '02161112245', 1, '2026-05-01 10:00:00+00', 'system'),
    (4,  5,  'mehmet.yilmaz@example.com', NULL,         '05361112255', NULL,          1, '2026-05-02 10:00:00+00', 'system'),
    (5,  6,  'elif.yilmaz@example.com',  '02121112266', '05334445599', NULL,          1, '2026-05-03 10:00:00+00', 'system'),
    (6,  7,  'ali.ozturk@example.com',   '02161112277', '05321112288', NULL,          1, '2026-05-04 10:00:00+00', 'system'),
    (7,  8,  'fatma.sahin@example.com',  NULL,          '05371112266', NULL,          1, '2026-05-05 10:00:00+00', 'system'),
    (8,  9,  'burak.demir@example.com',  '02161112288', '05381112277', NULL,          1, '2026-05-06 10:00:00+00', 'system'),
    (9,  10, 'selin.aydin@example.com',  NULL,          '05391112200', NULL,          1, '2026-05-07 10:00:00+00', 'system'),
    (10, 11, 'kerem.toprak@example.com', '02161112299', '05321112299', NULL,          1, '2026-05-08 10:00:00+00', 'system');

-- Explicit IDs above: advance every identity sequence past the seeded values.
SELECT setval(pg_get_serial_sequence('city', 'id'),        (SELECT MAX(id) FROM city));
SELECT setval(pg_get_serial_sequence('district', 'id'),    (SELECT MAX(id) FROM district));
SELECT setval(pg_get_serial_sequence('party', 'id'),       (SELECT MAX(id) FROM party));
SELECT setval(pg_get_serial_sequence('ind', 'id'),         (SELECT MAX(id) FROM ind));
SELECT setval(pg_get_serial_sequence('party_role', 'id'),  (SELECT MAX(id) FROM party_role));
SELECT setval(pg_get_serial_sequence('cust', 'id'),        (SELECT MAX(id) FROM cust));
SELECT setval(pg_get_serial_sequence('addr', 'id'),        (SELECT MAX(id) FROM addr));
SELECT setval(pg_get_serial_sequence('cntc_medium', 'id'), (SELECT MAX(id) FROM cntc_medium));
-- Business customer numbers 1001-1011 are now taken; next create gets 1012.
SELECT setval('cust_customer_number_seq', 1011);
