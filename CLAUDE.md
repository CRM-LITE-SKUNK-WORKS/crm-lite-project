# CRM Lite Agent Instructions

- Read PROJECTBRAIN.md before architectural changes.
- Final requirements are under docs\source\requirements (current: FR/AC v8 Final,
  16.07.2026 revision — reconciliation record in docs/requirements/document-delta.md).
- Approved architecture decisions are under docs/architecture/adr/ (ADR-001..005 are binding;
  they override older wording in any other document or diagram).
- GNL_ST/GNL_TP are centrally owned by lookup-service: never create local copies, local seeds
  or cross-database foreign keys in another service's database (ADR-002).
- Nationality ID is permanently, globally unique — soft delete never releases it (ADR-003).
- GET /api/customers is the only customer list/filter endpoint (no /search alias). It has a
  criterion-less browse mode and returns Page<CustomerDetailResponse> (ADR-005) — do not
  reintroduce a mandatory-criteria rule or a slim search row DTO.
- MERNIS message keys are the analyst catalog names: MSG-CUST-NATID-VERIFICATION-FAILED (400)
  and MSG-MERNIS-UNAVAILABLE (503) — do not revive MSG-NATID-VERIFY-FAILED.
- Follow the team Git workflow (CONTRIBUTING.md + docs/runbooks/git-workflow.md): branches
  from origin/dev, PRs base=dev, squash-merge; never work directly on dev/main.
- Never edit old committed Flyway migrations.
- Never commit secrets, local settings, target folders or IDE workspace files.
- Do not commit or push automatically.
- Run build and tests before reporting completion (integration tests need Docker).