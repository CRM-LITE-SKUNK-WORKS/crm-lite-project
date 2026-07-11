# CRM Lite Agent Instructions

- Read PROJECTBRAIN.md before architectural changes.
- Final requirements are under docs\source\requirements.
- Approved architecture decisions are under docs/architecture/adr/ (ADR-001..004 are binding;
  they override older wording in any other document or diagram).
- GNL_ST/GNL_TP are centrally owned by lookup-service: never create local copies, local seeds
  or cross-database foreign keys in another service's database (ADR-002).
- Nationality ID is permanently, globally unique — soft delete never releases it (ADR-003).
- GET /api/customers is the only customer search endpoint (no /search alias).
- Never edit old committed Flyway migrations.
- Never commit secrets, local settings, target folders or IDE workspace files.
- Do not commit or push automatically.
- Run build and tests before reporting completion (integration tests need Docker).