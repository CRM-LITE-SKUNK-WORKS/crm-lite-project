# FE-ADR-002: Standalone Components, TypeScript Strict Mode, Exactly Pinned Toolchain

## Status
Accepted (2026-07-23). Versions verified against the npm registry, the Node.js
release index and Docker Hub on **2026-07-23** (see §Verification).

## Context
The backend pins its toolchain explicitly and by exact number: `java.version 25`,
`spring-cloud.version 2025.1.2`, `springdoc-openapi.version 2.8.6`, and pinned
container images (`quay.io/keycloak/keycloak:26.3.4`, `postgres:16`). PROJECTBRAIN
§5.11 and §5.9 record real failures caused by unpinned or implicit tooling
assumptions. The frontend adopts the same discipline.

Angular offers two component authoring models (NgModule-based and standalone)
and a spectrum of TypeScript strictness settings.

## Decision

### 1. Standalone components — no NgModules
Every component, directive and pipe is **standalone**. `NgModule` is not used
anywhere, including for routing (routes are plain `Routes` arrays with
`loadComponent` / `loadChildren` for lazy loading) and for bootstrapping
(`bootstrapApplication` + `ApplicationConfig` providers).

**Why not NgModules:** standalone is the default authoring model in current
Angular; NgModules exist for backwards compatibility. For an application of this
size the NgModule layer contributes only indirection — a second declaration site
for every component and an import graph that must be kept in sync by hand.
Mixing both models is worse than either, so the choice is made once, globally.

### 2. TypeScript strict mode — all strictness flags on
`strict: true` plus Angular's `strictTemplates`, and additionally
`noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch`,
`noImplicitOverride` and `noPropertyAccessFromIndexSignature`.

**Why:** the frontend's job is to consume a contract it does not own. Strict
typing turns a backend field rename (`customerId` → `customerNumber`, which
ADR-005 already performed once) into a **compile error** instead of an
`undefined` rendered on screen. This mirrors the backend's own
`ddl-auto: validate` philosophy — fail at build time, never silently at runtime.
`strictTemplates` extends the same guarantee into HTML.

### 3. Exact version pinning — no range operators
`package.json` records **exact versions** (no `^`, no `~`, never `latest`), and
`package-lock.json` is committed. Dependency upgrades are deliberate, reviewed
commits — the same rule the backend applies to its `<properties>` block.

**Why not caret ranges:** with `^`, two developers running `npm install` a week
apart get different Angular patch versions, and CI can differ from both. The
lockfile alone would mostly prevent this, but an exact `package.json` makes the
intended version readable without parsing a 10k-line lockfile.

### 4. Pinned versions

| Component | **Pinned version** | Why this one |
|---|---|---|
| `@angular/core`, `@angular/common`, `@angular/forms`, `@angular/router`, `@angular/platform-browser` | **22.0.8** | Latest published Angular release on the verification date |
| `@angular/cli`, `@angular/build`, `@angular/compiler-cli` | **22.0.8** | Aligned to the framework version. (Written as 22.0.7 pre-scaffold; `ng new` resolved the latest 22.x = 22.0.8 for tooling too, so all `@angular/*` are one version.) |
| **TypeScript** | **6.0.3** | ⚠️ **NOT the latest TypeScript.** `@angular/compiler-cli@22.0.8` declares `peerDependencies: { typescript: ">=6.0 <6.1" }`. The newest TypeScript is 7.0.2 and is **incompatible** with this Angular |
| **Node.js** | **22.23.1** (LTS "Jod") | Angular 22 declares `engines.node: "^22.22.3 \|\| ^24.15.0 \|\| >=26.0.0"`. **22.23.1 satisfies `^22.22.3`** — verified. Chosen by the team on 2026-07-23; see the support-window note below |
| `rxjs` | **7.8.2** | Satisfies Angular's peer range `^6.5.3 \|\| ^7.4.0` |
| `tailwindcss` | **4.3.3** | Latest published release (FE-ADR-011) |
| Build image | **`node:22.23.1-alpine`** | Matches the pinned Node exactly (FE-ADR-010) |
| Runtime image | **`nginx:1.30.4-alpine`** | nginx **stable** line (even minor = stable; 1.31.x is mainline) (FE-ADR-010) |

> **`zone.js` is NOT a dependency.** The application runs **zoneless** —
> see FE-ADR-006 §7. `@angular/core@22.0.8` declares
> `peerDependenciesMeta: { "zone.js": { "optional": true } }`, so omitting it is
> a supported configuration, not a workaround.

#### Node 22 vs Node 24 — the trade-off, stated honestly
Both satisfy Angular 22. The difference is the support window (nodejs/Release
schedule, read 2026-07-23):

| Line | Status today (2026-07-23) | End of life |
|---|---|---|
| **v22 "Jod"** (chosen) | **Maintenance** since 2025-10-21 — security/critical fixes only | **2027-04-30** |
| v24 "Krypton" | **Active LTS** until 2026-10-20 | 2028-04-30 |

Node 22 is a fully valid choice and blocks nothing. The consequence to plan for
is that its EOL is roughly **nine months out**, so a migration to an Active LTS
line becomes a scheduled task rather than an emergency. Node 24 would have
bought about twelve more months. The team chose 22.23.1 deliberately; this note
exists so the expiry is not discovered late.

**The version must be identical in all three places** — `.nvmrc`,
`package.json` `engines`, and the Docker build stage — so local, CI and
container builds cannot diverge.

### 5. Developer environment requirement
The pinned Node version is a **hard requirement**, not a suggestion. It is
recorded in `frontend/.nvmrc` and in `package.json` `engines`.

> ⚠️ **Current developer machine does not satisfy it.** The machine used on
> 2026-07-23 runs **Node v23.11.1**. Node 23 is an odd-numbered, non-LTS line
> and satisfies **none** of Angular 22's three accepted ranges
> (`^22.22.3 || ^24.15.0 || >=26.0.0`). Node **22.23.1** must be installed
> before scaffolding, otherwise `ng` refuses to run.

## Verification
Every number above was read from an authoritative source on **2026-07-23**, not
from memory:

```bash
npm view @angular/core version                       # 22.0.8
npm view @angular/cli version                        # 22.0.7
npm view @angular/compiler-cli@22.0.8 peerDependencies
                                                     # typescript: '>=6.0 <6.1'
npm view @angular/core@22.0.8 engines                # node: ^22.22.3 || ^24.15.0 || >=26.0.0
npm view @angular/core@22.0.8 peerDependenciesMeta   # zone.js: { optional: true }
npm view typescript version                          # 7.0.2  <- too new, NOT used
npm view tailwindcss version                         # 4.3.3
curl -s https://nodejs.org/dist/index.json           # Jod LTS = v22.23.1
curl -s https://raw.githubusercontent.com/nodejs/Release/main/schedule.json
                                                     # v22 maintenance→2027-04-30
```

**Re-run these commands before scaffolding.** If a number has moved, update this
ADR in the same commit as `package.json` — never let the two disagree.

## Consequences
- A developer cannot accidentally build with a different Angular or TypeScript;
  the toolchain is reproducible across machines, CI and the Docker build stage.
- **Upgrades become explicit work.** Angular's 6-month major cadence means a
  scheduled upgrade task, not a passive drift. This is intended.
- The TypeScript pin is the fragile one: it is dictated by Angular's peer range,
  so TypeScript may only be upgraded when Angular's range widens. Anyone
  "helpfully" bumping TypeScript to the latest will break the build.
- Strict mode raises the cost of sloppy typing (`any`, non-null assertions) —
  deliberately.
- Standalone-only means examples and answers written for the NgModule era do not
  apply verbatim; this is stated in FRONTEND_BRAIN's agent rules.
