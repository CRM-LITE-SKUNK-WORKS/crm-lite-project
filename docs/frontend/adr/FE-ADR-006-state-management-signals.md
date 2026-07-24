# FE-ADR-006: State Management — Angular Signals with a Thin Service Layer

## Status
Accepted (2026-07-23).

## Context
Before choosing a state library it is worth enumerating the state this
application actually has. In the buildable scope (FE-ADR-013):

| State | Lifetime | Shared across features? |
|---|---|---|
| Session (`authenticated`, `username`, `roles`) | Application | Yes — header, guards |
| UI language (`en` \| `tr`) | Application (persisted) | Yes — everything |
| City / district lookups | Application (cacheable) | Yes — create + detail address forms |
| Search filters + result page | Screen | No |
| Create wizard step + form values | Screen | No |
| Customer detail + addresses + contact | Screen | No |
| Toast / dialog visibility | Ephemeral | No |

Three genuinely application-wide items; everything else is request-scoped data
owned by one screen. **The server is the source of truth** — there is no
offline mode, no optimistic update requirement, no undo/redo, no realtime push,
and no client-side cache that must be reconciled across features.

## Decision

### 1. Signals plus small injectable services
State lives in `signal()`s inside services. Derived values use `computed()`.
Side effects that must react to state use `effect()`, sparingly. Application-wide
services (`SessionService`, `I18nService`, `LookupCacheService`) are
`providedIn: 'root'` and live in `core/`; screen-scoped state is provided by the
feature's route or component.

### 2. Services expose read-only state and intent-named methods
Public API is `readonly` signals (via `.asReadonly()` or `computed`), never the
writable signal itself. Mutation happens through named methods
(`applyFilters()`, `goToPage()`, `setLanguage()`), so every state transition has
one findable call site.

### 3. HTTP stays in dedicated data services
`features/customer/data/customer-api.service.ts` holds `HttpClient` calls and
returns typed contract models. State services consume them. A component never
injects `HttpClient` directly.

### 4. NgRx (and Redux-shaped alternatives) are rejected
**Why not:** NgRx's value proposition is a single serialized state tree with
traceable transitions — worth its cost when many features mutate shared state,
when optimistic updates and rollback are needed, or when time-travel debugging
pays for itself. **None of those conditions hold here.** What it would cost:
an action, a reducer case, an effect and a selector for every operation as
trivial as "load page 2 of the customer list"; a second mental model layered on
top of Angular's own reactivity; and a dependency whose major versions must be
kept aligned with Angular's 6-month cadence (FE-ADR-002 §3).

For three screens whose state is overwhelmingly request-scoped, that is
ceremony without benefit. This is the same reasoning FE-ADR-011 applies to
component libraries and FE-ADR-012 to i18n libraries: **the project's dependency
budget is spent only where a real problem exists.**

### 5. Signals rather than `BehaviorSubject` service state
RxJS remains in the dependency set (Angular peer requirement, and `HttpClient`
returns observables), and is the right tool for **event streams** — debounced
search input, request cancellation. It is not used to *hold* state. Signals
integrate with change detection directly, need no `async` pipe, no manual
unsubscribe and no `takeUntilDestroyed` bookkeeping for simple values.

**Rule of thumb:** *state* → signal; *stream of events over time* → RxJS
operator pipeline that ends by writing into a signal.

### 7. Zoneless change detection — `zone.js` is not a dependency
The application runs **zoneless**. `zone.js` is not installed and not shipped.

**Why:** `@angular/core@22.0.8` declares
`peerDependenciesMeta: { "zone.js": { "optional": true } }` — verified on
2026-07-23 — so this is a first-class supported mode, not an experiment.
zone.js works by monkey-patching every async browser primitive
(`setTimeout`, `Promise`, `addEventListener`, `XMLHttpRequest`) and then running
change detection over the whole component tree whenever any of them fires. That
is a blunt instrument, it costs bundle size, and it makes stack traces harder to
read.

Signals already tell Angular precisely what changed and which views depend on
it. Once state lives in signals (§1), zone.js is redundant machinery whose only
remaining job is to notice things the signal graph already knows.

**What this requires in practice:** state that the template reads must be a
signal. Mutating a plain field and expecting the view to update will not work.
That constraint is not a burden here — it is the same discipline §1 and §2
already impose, and going zoneless makes violations fail loudly instead of
working by accident.

`AsyncPipe` continues to work for observables, so the `HttpClient` boundary is
unaffected.

> The exact provider name for zoneless bootstrap should be confirmed against the
> Angular 22 API at scaffold time; the decision recorded here is the **mode**,
> not a specific symbol.

### 6. No global cache invalidation framework
The lookup cache (cities/districts) is immutable reference data — load once,
keep. Customer data is re-fetched on navigation. If a future domain needs real
caching semantics, that is a new decision, made then, with the requirement in
hand.

## Consequences
- Onboarding cost is low: a developer reads one service and sees the whole
  state, its derivations and its transitions in one file.
- No devtools time-travel. Accepted — debugging is `console.log` on a signal or
  Angular DevTools' signal graph, sufficient at this scale.
- **The rejection is revisitable, not permanent.** If FR-SALE arrives with a
  basket that must stay consistent across Offer Selection, Product Configuration
  and Submit Order, that is genuinely shared mutable state across features and a
  store may become justified. Revisiting requires a new ADR that supersedes this
  one — not an ad-hoc `npm install`.
- Discipline required: signals make it *easy* to scatter mutable state into
  components. §2's read-only rule is what prevents that, and it must be enforced
  in review.
