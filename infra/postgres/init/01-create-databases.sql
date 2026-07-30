CREATE DATABASE customer_db;
CREATE DATABASE lookup_db;
CREATE DATABASE keycloak_db;
-- Runs only on FIRST volume initialization: existing dev volumes need a one-time
-- manual `CREATE DATABASE account_db;` (same situation as keycloak_db — see
-- docs/runbooks/database.md).
CREATE DATABASE account_db;
-- Same first-init caveat: existing dev volumes need a one-time manual
-- `CREATE DATABASE product_db;`.
CREATE DATABASE product_db;
