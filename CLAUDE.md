# CRM Lite Agent Instructions

- Read PROJECTBRAIN.md before architectural changes.
- Final requirements are under docs\source\requirements (current: FR/AC v8-2,
  03.08.2026 revision — reconciliation record in docs/requirements/document-delta.md).
- Approved architecture decisions are under docs/architecture/adr/ (ADR-001..005 and
  ADR-013/014 are binding; they override older wording in any other document or diagram).
- account-service (FR-ACCT-01..04, KR-11) is implemented per ADR-013/014: account numbers are
  VARCHAR(10) Luhn-checked KR-11 values that are immutable and never reused; delete =
  passivation (Passive rows stay list-visible); the K-8 automatic 223 Customer Account is a
  create-time side effect that never appears in any API response; cust_acct_prod_invl is
  written ONLY by account-service — future product/order services must integrate through an
  account-service API/event, never by writing account_db directly.
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
- Never add `Co-Authored-By: Claude`, `Generated with Claude Code`, or any other
  AI-attribution trailer to commit messages or PR bodies. Commit author and
  co-authors are humans only. (Enforced by `includeCoAuthoredBy: false` in
  .claude/settings.json; this line is the backstop.)
- Never commit secrets, local settings, target folders or IDE workspace files.
- Do not commit or push automatically.
- Run build and tests before reporting completion (integration tests need Docker).