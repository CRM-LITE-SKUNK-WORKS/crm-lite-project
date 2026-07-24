# FE-ADR-001: Frontend Lives in This Repository, Under `frontend/`

## Status
Accepted (2026-07-23) — first frontend architecture decision. Binding for all
subsequent FE-ADRs.

## Context
The repository is a Maven monorepo (root parent POM, `backend/*` modules,
`infra/`, `docs/`). No frontend exists yet; `docs/api/authentication.md` states
explicitly: *"No Angular application exists in the repository yet. This document
is the contract that the future Angular shell codes against."*

The frontend consumes contracts that are themselves versioned in this
repository: `docs/api/customer-service.md`, `docs/api/authentication.md`,
`docs/architecture/adr/ADR-001..012`, `docs/frontend/mock-ui-analysis.md`, and
the compose topology in `infra/docker-compose.yml`.

Two placements were evaluated: **(A)** a separate `crm-lite-frontend`
repository, **(B)** a `frontend/` directory in this repository.

## Decision
1. **The frontend lives in this repository at `frontend/`**, a sibling of
   `backend/`, `infra/` and `docs/`.
2. **`frontend/` is NOT a Maven module.** No `pom.xml` is created for it and the
   root `pom.xml` `<modules>` list is **not touched**. The Angular toolchain
   (npm) and the Java toolchain (Maven) stay fully independent; neither build
   invokes the other.
3. **Option A (separate repository) is rejected.** The decisive reason is
   *contract drift*: the API contract, the ADRs that govern it and the mock
   analysis all live here. In a split repository a backend change and its
   frontend consumer land in two pull requests, in two review queues, with no
   atomic revert. At this project's scale the coordination cost (cross-repo
   versioning, release ordering, duplicated CI, contract-sync rituals) buys
   nothing that a directory boundary does not already give.
4. **Wiring the frontend into the Maven reactor is rejected.** A
   `frontend-maven-plugin` module would download a Node distribution and run
   `npm ci` on every `mvn install`, coupling backend build time and backend CI
   to the Node toolchain. The frontend's production build belongs in its own
   Docker stage instead (FE-ADR-010).
5. **The existing Git workflow applies unchanged** (`CONTRIBUTING.md`,
   `docs/runbooks/git-workflow.md`): branches from `origin/dev`, PRs with
   `base=dev`, squash-merge, never directly on `dev`/`main`. Frontend work uses
   the same branch prefixes (`feature/`, `fix/`, `docs/`, …).

## Consequences
- A single pull request can change an API contract, its documentation and its
  frontend consumer together, and a single revert undoes all three.
- `.gitignore` gains frontend entries (`frontend/node_modules/`,
  `frontend/dist/`, `frontend/.angular/`). The existing rule "never commit
  target folders or IDE workspace files" (CLAUDE.md) extends to these.
- CI gains a **separate** frontend job. It must not be chained into the Maven
  job: a failing frontend lint must not block a backend release, and vice
  versa. *(Concrete CI wiring is not in the files — see
  `docs/frontend/scope-and-conflicts.md`.)*
- Repository clone size grows with the frontend lockfile and sources; build
  artifacts stay ignored.
- Anyone running only the backend is unaffected — `mvn` never descends into
  `frontend/`.
