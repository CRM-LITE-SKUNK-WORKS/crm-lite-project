# Runbook — Team Git Workflow

Complete, command-by-command workflow for the CRM Lite team. The short version lives
in [CONTRIBUTING.md](../../CONTRIBUTING.md); PROJECTBRAIN.md links here.

## 1. Branch model

| Branch | Purpose | Rules |
|---|---|---|
| `main` | Stable demo/release branch **only** | Receives merges from `dev` via PR; optionally tagged. Never commit to it directly |
| `dev` | Integrated, reviewed development baseline | All feature work lands here through PRs. Never commit to it directly |
| `feature/*` | New functionality | Short-lived, created from current `origin/dev` |
| `fix/*` | Bug fixes | same |
| `docs/*` | Documentation-only changes | same |
| `refactor/*` | Behaviour-preserving restructuring | same |
| `test/*` | Test-only additions/changes | same |
| `chore/*` | Build/tooling/housekeeping | same |

Branch names: lowercase, hyphenated, short and descriptive —
`feature/customer-list-contract`, `fix/gsm-prefix-escape`, `docs/git-workflow`.

No `release/*` branch is required yet: releases are `dev → PR → main`, optionally
followed by a tag (`git tag -a v0.2.0 -m "demo 2" && git push origin v0.2.0`).

## 2. The normal workflow, step by step

```bash
# 1. Make sure the working tree is clean
git status --short              # must print nothing; if not, commit or stash (see §5)

# 2-4. Sync your dev with the remote
git fetch origin --prune
git switch dev
git pull --ff-only origin dev   # --ff-only: fails loudly instead of creating a surprise merge

# 5. Create the working branch (ALWAYS from refreshed dev)
git switch -c feature/customer-list-contract

# 6. Make one-purpose changes (one branch = one reviewable concern)

# 7. Verify before staging
mvn clean verify                # build + tests (integration tests need Docker running)
git diff                        # read your own change first
git diff --check                # whitespace/conflict-marker check

# 8. Stage only intended files — NEVER a blind `git add .`
git status --short              # know every line in this output
git add backend/customer-service/src/main/java/... docs/api/customer-service.md

# 9. Review exactly what will be committed
git diff --cached

# 10. Commit with a meaningful Conventional Commit message
git commit -m "feat(customer): browse mode + full detail rows for GET /api/customers"

# 11. Push and set the upstream
git push -u origin feature/customer-list-contract

# 12. Open a PR with base = dev (GitHub UI or:)
#     gh pr create --base dev --title "feat(customer): ..." --body "..."

# 13. Get at least one teammate review
# 14. Resolve every review conversation (code or reply, then mark resolved)

# 15. Merge with "Squash and merge" (preferred — one clean commit per PR on dev)

# 16. Clean up locally after the merge
git switch dev
git pull --ff-only origin dev
git branch -d feature/customer-list-contract

# 17. Delete the remote branch (GitHub "Delete branch" button, or:)
git push origin --delete feature/customer-list-contract

# 18. Start the next branch only from this refreshed dev (back to step 1)
```

### Commit message convention (Conventional Commits)

```
<type>(<scope>): <imperative summary, <= 72 chars>

optional body: what & why (not how)
```

Types match the branch prefixes: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`.
Examples from this repo's history: `feat: implement customer service aggregate (#3)`,
`docs: update project analysis artifacts (#4)`.

WIP commits (`wip: ...`) are acceptable **locally and on your own feature branch** —
the PR is squash-merged, so they never reach `dev` history.

## 3. Hard rules

- **Never develop directly on `dev` or `main`.** Even a "one-liner" goes through a
  branch + PR.
- **Never use a blind `git add .`** — always read `git status --short` first and stage
  deliberately. This is how `target/`, IDE files and local settings leak into commits.
- **Do not force-push shared branches** (`dev`, `main`, or any branch a teammate may
  have pulled). GitHub branch protection should reject it; don't try.
- **Rebase / `git push --force-with-lease`** are allowed only for experienced users on
  their own non-shared branches. If you have to ask, use merge instead.
- **If `dev` advances while your branch is open** (beginner-safe path):

  ```bash
  git fetch origin
  git switch feature/my-branch
  git merge origin/dev          # resolve conflicts once, locally
  mvn clean verify              # re-verify after the merge
  git push
  ```

  The merge commit disappears at squash-merge time, so history stays clean anyway.

## 4. Handling conflicts

1. `git merge origin/dev` reports conflicting files.
2. `git status` lists them; open each and resolve the `<<<<<<< / ======= / >>>>>>>`
   blocks (IntelliJ's conflict tool is fine).
3. `git add <resolved-file>` each one, then `git commit` (default merge message OK).
4. Re-run the build and tests before pushing — a textually clean merge can still be
   semantically broken.
5. If you get lost mid-merge: `git merge --abort` returns to the pre-merge state.

## 5. Uncommitted changes in the way (stash)

```bash
git stash push -m "wip: address validation"   # park tracked changes
git stash push -u -m "wip incl. new files"    # include untracked files
git switch dev && git pull --ff-only origin dev
git switch feature/my-branch
git stash list
git stash pop                                  # re-apply newest stash (drops it on success)
```

Prefer a WIP commit on your own branch over a long-lived stash — stashes are easy to
forget and have no review trail.

## 6. Release flow

1. `dev` is demo-ready (all planned PRs merged, `mvn clean verify` green).
2. Open a PR **base = `main`, compare = `dev`**. Review = release check.
3. Merge (regular merge or squash — team's call per release), optionally tag:
   `git switch main && git pull --ff-only origin main && git tag -a v0.2.0 -m "Demo 2" && git push origin v0.2.0`.
4. No `release/*` branches until the project actually needs release hardening.

## 7. Files that must never be committed

Generated / local / secret material — most are already in `.gitignore`, but staging
deliberately (§2 step 8) is the real protection:

- `target/` build output, `*.class`, surefire reports
- `.idea/`, `*.iml`, `workspace.xml`, `.vscode/` personal settings
- `settings.local.json`, `.claude/settings.local.json`
- log files, database dumps, local env files (`.env`, `application-local.yml`)
- temporary Office lock files starting with `~$` (e.g. under `docs/source/`)
- **secrets of any kind** (passwords, tokens, JWT secrets, Keycloak client secrets) —
  including inside `config-repo/*.yml`; real credentials get a secret-management
  story (PROJECTBRAIN §9.4) before they ever exist in this repo

## 8. Quick reference — what do I do when…

| Situation | Command(s) |
|---|---|
| Start new work | §2 steps 1–5 |
| dev moved under me | §3 "if dev advances" |
| Conflict during merge | §4 |
| Need to switch tasks with dirty tree | §5 stash or WIP commit |
| Accidentally committed on dev (not pushed) | `git branch feature/rescue && git reset --hard origin/dev && git switch feature/rescue` |
| Staged something unwanted | `git restore --staged <file>` |
| Committed an unwanted file (not pushed) | `git rm --cached <file> && git commit --amend` (own branch only) |
| PR merged, next task | §2 steps 16–18 |
