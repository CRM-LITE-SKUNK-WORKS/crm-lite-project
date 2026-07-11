-- Contract seed from CRM_Lite_Entity_Seed_PreviewV8_Final.xlsx.
-- IDs are EXPLICIT and IMMUTABLE (ADR-002): consumers persist these IDs as external
-- references, so renumbering is a breaking contract change. Additions must use new
-- forward-only migrations with new explicit IDs.

INSERT INTO gnl_st (id, short_code, name, status_domain, created_date, created_by) VALUES
    (1, 'ACTV',      'Active',                      'GENERAL', '2026-01-01 00:00:00+00', 'system'),
    (2, 'PASV',      'Passive',                     'GENERAL', '2026-01-01 00:00:00+00', 'system'),
    (3, 'WAIT',      'Waiting',                     'ORDER',   '2026-01-01 00:00:00+00', 'system'),
    (4, 'MIDLWARE',  'Siparis Alindi Isleniyor...', 'ORDER',   '2026-01-01 00:00:00+00', 'system'),
    (5, 'CANCELLED', 'Cancelled',                   'ORDER',   '2026-01-01 00:00:00+00', 'system'),
    (6, 'PNDG',      'Pending',                     'PROD',    '2026-01-01 00:00:00+00', 'system');

INSERT INTO gnl_tp (id, short_code, name, type_domain, created_date, created_by) VALUES
    (1,  'MALE',       'Male',               'GENDER',         '2026-01-01 00:00:00+00', 'system'),
    (2,  'FEMALE',     'Female',             'GENDER',         '2026-01-01 00:00:00+00', 'system'),
    (3,  'INDV',       'Individual',         'PARTY_TYPE',     '2026-01-01 00:00:00+00', 'system'),
    (4,  'ORG',        'Organization',       'PARTY_TYPE',     '2026-01-01 00:00:00+00', 'system'),
    (5,  'INDVCAT',    'Individual Catalog', 'CATALOG_TYPE',   '2026-01-01 00:00:00+00', 'system'),
    (6,  'CORPCAT',    'Corporate Catalog',  'CATALOG_TYPE',   '2026-01-01 00:00:00+00', 'system'),
    (7,  'NEWSALE',    'New Sale',           'BSN_INTER_TYPE', '2026-01-01 00:00:00+00', 'system'),
    (8,  'TRANSFER',   'Transfer',           'BSN_INTER_TYPE', '2026-01-01 00:00:00+00', 'system'),
    (9,  'CANCEL',     'Cancellation',       'BSN_INTER_TYPE', '2026-01-01 00:00:00+00', 'system'),
    (10, 'INTERNET',   'Internet Service',   'SERVICE_TYPE',   '2026-01-01 00:00:01+00', 'system'),
    (11, 'RESOURCE',   'Resource',           'SERVICE_TYPE',   '2026-01-01 00:00:02+00', 'system'),
    (12, 'ACTIVATION', 'Activation Service', 'SERVICE_TYPE',   '2026-01-01 00:00:03+00', 'system');
