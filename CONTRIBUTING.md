# Contributing to CRM Lite

Concise contributor rules. The complete command-by-command team workflow lives in
**[docs/runbooks/git-workflow.md](docs/runbooks/git-workflow.md)** — read it once
before your first branch.

## Branch model

- **`main`** — stable demo/release branch only. Never develop on it.
- **`dev`** — integrated, reviewed development baseline. Never develop on it directly.
- **Short-lived working branches** created from current `origin/dev`:
  `feature/*`, `fix/*`, `docs/*`, `refactor/*`, `test/*`, `chore/*`
  (e.g. `feature/customer-list-contract`, `fix/address-primary-guard`).

## The normal loop (details + recovery paths in the runbook)

1. Clean working tree → `git fetch origin --prune` → `git switch dev` →
   `git pull --ff-only origin dev`.
2. `git switch -c <type>/<short-description>` — one purpose per branch.
3. Make the change; run format/build/tests (`mvn clean verify`; integration tests
   need Docker — see [docs/runbooks/testcontainers.md](docs/runbooks/testcontainers.md)
   if Testcontainers can't find a Docker environment) and review `git diff`.
4. Stage **only intended files** (never a blind `git add .`), review
   `git diff --cached`, commit with a
   [Conventional Commit](https://www.conventionalcommits.org/) message.
5. `git push -u origin <branch>` → open a PR with **base = `dev`**.
6. At least **one teammate review**; resolve all review conversations.
7. **Squash and merge** (preferred) → then locally:
   `git switch dev && git pull --ff-only origin dev && git branch -d <branch>`,
   delete the remote branch, and start the next branch from refreshed `dev`.

## Hard rules

- Never develop directly on `dev` or `main`.
- Never force-push shared branches (`dev`, `main`, any branch someone else pulled).
  `rebase`/`--force-with-lease` are for experienced users on non-shared branches only;
  beginners: merge `origin/dev` into your feature branch instead.
- WIP commits are fine locally — PRs are squash-merged, so history stays clean.
- Do not commit: secrets/credentials, `target/`, `.idea/`, `workspace.xml`,
  `settings.local.json`, logs, `~$*` temporary Office files, local env files.
- Never edit already-shared Flyway migrations (see CLAUDE.md / docs/runbooks/database.md).
- Run build and tests before marking a PR ready; don't claim green without evidence.

## Project ground rules (pointers)

- Binding architecture decisions: `docs/architecture/adr/` (ADR-001..005).
- Current system state + decisions log: `PROJECTBRAIN.md`.
- Requirements source of truth: `docs/source/**` (files named `Final` win).
