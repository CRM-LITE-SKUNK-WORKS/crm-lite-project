-- Seed from CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx (customer scope only).
-- status_id 1 = ACTV, 2 = PASV — external GNL_ST contract IDs (ADR-002), NOT local rows.
-- Customer 1003 (Caner Sahin) is seeded soft-deleted on purpose: it exercises the
-- "passive customers are invisible but their Nationality ID stays reserved" rules.

INSERT INTO role (id, role_name, status_id, created_date, created_by) VALUES
    (1, 'Customer', 1, '2026-01-01 00:00:00+00', 'system');

INSERT INTO city (id, name, status_id, created_date, created_by) VALUES
    (1, 'Istanbul', 1, '2026-01-01 00:00:00+00', 'system'),
    (2, 'Ankara',   1, '2026-01-01 00:00:00+00', 'system');

INSERT INTO district (id, city_id, name, status_id, created_date, created_by) VALUES
    (1, 1, 'Kadikoy',  1, '2026-01-01 00:00:00+00', 'system'),
    (2, 1, 'Besiktas', 1, '2026-01-01 00:00:00+00', 'system'),
    (3, 2, 'Cankaya',  1, '2026-01-01 00:00:00+00', 'system');

-- party_type_id 3 = GNL_TP INDV (external contract ID)
INSERT INTO party (id, party_type_id, status_id, created_date, created_by, updated_date, updated_by, deleted_date, deleted_by) VALUES
    (1, 3, 1, '2026-03-10 11:00:00+00', 'system', NULL, NULL, NULL, NULL),
    (2, 3, 1, '2026-03-12 14:30:00+00', 'system', NULL, NULL, NULL, NULL),
    (3, 3, 2, '2026-04-01 09:20:00+00', 'system', '2026-04-15 09:20:00+00', 'system', '2026-04-15 09:20:00+00', 'system');

-- gender_id 1 = MALE, 2 = FEMALE (external GNL_TP contract IDs)
INSERT INTO ind (id, party_id, first_name, middle_name, last_name, father_name, mother_name, birth_date, gender_id, nationality_id, status_id, created_date, created_by, updated_date, updated_by, deleted_date, deleted_by) VALUES
    (1, 1, 'Ali',    NULL,  'Yildiz', 'Hasan',  'Ayse',  '1990-05-14', 1, '12345678901', 1, '2026-03-10 11:00:00+00', 'system', NULL, NULL, NULL, NULL),
    (2, 2, 'Zeynep', 'Nur', 'Demir',  'Mehmet', 'Fatma', '1995-11-02', 2, '23456789012', 1, '2026-03-12 14:30:00+00', 'system', NULL, NULL, NULL, NULL),
    (3, 3, 'Caner',  NULL,  'Sahin',  'Ahmet',  'Elif',  '1988-07-21', 1, '34567890123', 2, '2026-04-01 09:20:00+00', 'system', '2026-04-15 09:20:00+00', 'system', '2026-04-15 09:20:00+00', 'system');

INSERT INTO party_role (id, party_id, role_id, status_id, created_date, created_by, updated_date, updated_by, deleted_date, deleted_by) VALUES
    (1, 1, 1, 1, '2026-03-10 11:00:00+00', 'system', NULL, NULL, NULL, NULL),
    (2, 2, 1, 1, '2026-03-12 14:30:00+00', 'system', NULL, NULL, NULL, NULL),
    (3, 3, 1, 2, '2026-04-01 09:20:00+00', 'system', '2026-04-15 09:20:00+00', 'system', '2026-04-15 09:20:00+00', 'system');

INSERT INTO cust (id, customer_number, party_role_id, status_id, created_date, created_by, updated_date, updated_by, deleted_date, deleted_by) VALUES
    (1, 1001, 1, 1, '2026-03-10 11:00:00+00', 'system', NULL, NULL, NULL, NULL),
    (2, 1002, 2, 1, '2026-03-12 14:30:00+00', 'system', NULL, NULL, NULL, NULL),
    (3, 1003, 3, 2, '2026-04-01 09:20:00+00', 'system', '2026-04-15 09:20:00+00', 'system', '2026-04-15 09:20:00+00', 'system');

INSERT INTO addr (id, party_id, city_id, district_id, street, house_flat_no, address_description, is_primary, status_id, created_date, created_by) VALUES
    (1, 1, 1, 1, 'Bagdat Cad.',       '12/4', 'Kadikoy ev',  TRUE,  1, '2026-03-10 11:00:00+00', 'system'),
    (2, 1, 1, 2, 'Barbaros Bul.',     '5',    'Besiktas ek', FALSE, 1, '2026-03-12 14:30:00+00', 'system'),
    (3, 2, 2, 3, 'Tunali Hilmi Cad.', '28/2', 'Cankaya ev',  TRUE,  1, '2026-03-12 14:30:00+00', 'system');

INSERT INTO cntc_medium (id, party_id, email, home_phone, mobile_phone, fax, status_id, created_date, created_by) VALUES
    (1, 1, 'ali.yildiz@example.com',   '02161112233', '05321112233', NULL, 1, '2026-03-10 11:00:00+00', 'system'),
    (2, 2, 'zeynep.demir@example.com', NULL,          '05334445566', NULL, 1, '2026-03-12 14:30:00+00', 'system');

-- Explicit IDs above: advance every identity sequence past the seeded values.
SELECT setval(pg_get_serial_sequence('city', 'id'),        (SELECT MAX(id) FROM city));
SELECT setval(pg_get_serial_sequence('district', 'id'),    (SELECT MAX(id) FROM district));
SELECT setval(pg_get_serial_sequence('party', 'id'),       (SELECT MAX(id) FROM party));
SELECT setval(pg_get_serial_sequence('ind', 'id'),         (SELECT MAX(id) FROM ind));
SELECT setval(pg_get_serial_sequence('party_role', 'id'),  (SELECT MAX(id) FROM party_role));
SELECT setval(pg_get_serial_sequence('cust', 'id'),        (SELECT MAX(id) FROM cust));
SELECT setval(pg_get_serial_sequence('addr', 'id'),        (SELECT MAX(id) FROM addr));
SELECT setval(pg_get_serial_sequence('cntc_medium', 'id'), (SELECT MAX(id) FROM cntc_medium));
-- Business customer numbers 1001-1003 are taken; next create gets 1004.
SELECT setval('cust_customer_number_seq', 1003);
