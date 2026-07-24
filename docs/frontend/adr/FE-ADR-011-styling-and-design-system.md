# FE-ADR-011: Styling and Design System — Tailwind Themed with Real EDS Tokens; No Component Library

## Status
Accepted (2026-07-23) — **revised**. The earlier placeholder wording ("Tailwind
+ a headless component library, tokens TBD") is superseded: the mock turned out
to carry a formally defined design system, and its real token values are now
extracted and recorded.

## Context
The analyst mock (`docs/source/mock-ui/Guncel_Etiya_CRM_Lite_Full_App.html`) is
not a freehand drawing. It is built on **"Etiya EDS Lite Design System"**
(bundle namespace `EtiyaEDSLiteDesignSystem_d00eff`), which ships nine token
stylesheets (`tokens/colors.css`, `typography.css`, `spacing.css`, `radius.css`,
`elevation.css`, `motion.css`, `layout.css`, `base.css`, `styles.css`) and 26
formally defined components.

The bundle was unpacked and analysed on 2026-07-23; every real value is recorded
in `docs/frontend/mock-ui-analysis.md`. That analysis also revealed that the
mock's **inline CSS fallbacks disagree with the token files** in several places
(`var(--eds-space-10, 48px)` where the token is 40px; a
`--eds-type-title-size` that does not exist at all). This is why "read the token
tables, not the mock markup" is a binding rule below.

## Decision

### (a) Tailwind CSS is the styling engine
Pinned at **4.3.3** (FE-ADR-002 §4). Utility classes for layout and spacing;
component classes only where a repeated pattern justifies them.

**Why Tailwind:** the EDS system is token-based, and Tailwind's theme *is* a
token table — the mapping is one-to-one rather than a translation. It also makes
the constraint enforceable: if a value is not in the theme, there is no utility
class for it, so §(f) becomes mechanical rather than a matter of reviewer
vigilance.

### (b) EDS tokens are transferred into the Tailwind theme verbatim
Colour, spacing, radius, typography, elevation, motion, z-index and control
heights come from the **real token values** in
`docs/frontend/mock-ui-analysis.md` §2. Load-bearing examples:

| Token | Value |
|---|---|
| `--eds-color-action-primary-bg` | `#F58220` (Etiya orange) |
| `--eds-color-action-primary-text` | `#242441` — **never white on orange** |
| `--eds-color-bg-inverse` | `#242441` (Etiya navy) |
| `--eds-control-height-md` | **40px** — the default height of every form control |
| `--eds-radius-md` | 6px (button/input/select) |
| `--eds-space-10` | **40px** (not 48px — the mock's inline fallback is wrong) |
| `--eds-font-sans` | Inter 400/500/600 — **700+ is never used** |

Two layers are preserved as the system defines them: **primitives**
(`--eds-orange-*`, `--eds-ink-*`) and **semantics** (`--eds-color-*`).
**Components consume semantic tokens only.**

**Binding rule:** values are taken from the analysis document's tables, **never**
re-derived by reading the mock's inline styles, because those fallbacks are
demonstrably wrong in places. If a value is wrong, the analysis document is
corrected first, then the code.

### (c) No external component library — neither styled nor headless
**Material / PrimeNG / equivalents are rejected:** they arrive with their own
visual language and their own token systems. The work would become continuous
override-fighting against a design system that is already fully specified, and
pixel fidelity to an approved analyst design would be permanently uncertain.

**A headless library is also rejected**, and this is the less obvious call. A
headless kit earns its place by solving focus trapping, positioning and
keyboard interaction for complex overlay widgets — combobox, autocomplete,
multi-select, virtualized listbox. The mock contains **none of those**. The
entire overlay surface is one `Select` panel and one `DatePicker` calendar. A
dependency with its own release cadence and its own Angular-version coupling
(FE-ADR-002 §3) for two widgets is disproportionate.

This is the same dependency-budget reasoning FE-ADR-006 applies to NgRx and
FE-ADR-012 applies to i18n libraries.

### (d) Seven EDS components are written in `shared/ui/`
The mock uses eight EDS components. Seven are implemented as our own Angular
components:

`Icon`, `FormField`, `Button`, `TextInput`, `IconButton`, `Select`, `DatePicker`

**`PasswordInput` is excluded** — FE-ADR-005 §P5. Its only two consumers are the
login screen (now the Keycloak theme) and a Product Configuration field (out of
scope, FE-ADR-013).

Component contracts — props, size tables, variant/state CSS, and `FormField`'s
reserved-height error slot that prevents layout shift — are documented in
`docs/frontend/mock-ui-analysis.md` §3.3–§3.8 and are the specification.

Patterns the EDS bundle does **not** provide (Modal, ConfirmDialog, Toast,
StatusBadge, Pagination, Tabs, Stepper, Card, EmptyState) are built from the
mock's inline markup, which the analysis document reconstructs in §7.2.

### (e) Tables are semantic HTML
`<table><thead><tbody><tr><th><td>` — exactly as the mock does. No table
component, no grid library, no virtualization.

**Why:** the required behaviour is display, zebra striping, one link column and
external pagination. Semantic tables give correct screen-reader announcement of
rows and headers for free; a `<div>` grid would need ARIA reconstruction of what
HTML already provides. If sorting, column resizing or virtualization is ever
required, that is a new decision — pagination is server-side and sorting is
fixed server-side today (`GET /api/customers` accepts no `sort` parameter).

### (f) Arbitrary values are forbidden
> 🔴 An arbitrary hex colour (`#3B82F6`), an arbitrary pixel value
> (`padding: 13px`, `w-[137px]`, `mt-[7px]`) or an off-scale font size **must
> not** be written. Only defined tokens are used.

If a design genuinely needs a value the system lacks, the token is added to the
theme **and** to `docs/frontend/mock-ui-analysis.md` first, with a rationale.
Ad-hoc values are how a design system dies one commit at a time.

Known open item: the mock's sidenav label is `11px`, which has no token. It is
recorded in `docs/frontend/scope-and-conflicts.md` rather than silently
hardcoded.

### (g) Accessibility is our responsibility, in every component
Because no library provides it, each `shared/ui/` component owns:
- **Keyboard operation** — Select opens/navigates/selects/closes with
  Enter/Space/Arrows/Escape; DatePicker is fully keyboard-navigable; modals
  close on Escape.
- **Focus management** — visible focus on every interactive element; focus
  trapped inside modals and restored to the trigger on close; logical tab order.
- **ARIA and semantics** — native elements first (`<button>`, `<label>`,
  `<table>`); `aria-invalid` and `aria-describedby` wiring the field to its
  error text; `role="status"` for toasts, `role="alert"` for errors;
  `aria-current="page"` for pagination.
- **Focus visuals from tokens** — form controls use the
  `--eds-focus-ring-shadow` box-shadow ring; other interactive elements use the
  `base.css` `:focus-visible` outline.
- **Motion honours `prefers-reduced-motion`** — the EDS motion contract
  (§2.14) is implemented, including the documented Spinner exemption.

An accessibility gap in a `shared/ui/` component is a defect in that component,
not a task for later.

## Consequences
- Zero external UI dependencies. Angular upgrades cannot be blocked by a
  component library lagging behind.
- The team owns roughly 7 components plus ~10 patterns. That is real work, but
  bounded and fully specified by the analysis document.
- Pixel fidelity to the approved design is achievable rather than approximate.
- **Accessibility is a standing obligation**, not an inherited feature. This is
  the genuine cost of §(c) and it is accepted deliberately, with §(g) as the
  mitigation.
- Tailwind's theme becomes the single source of design truth in code, mirroring
  `mock-ui-analysis.md` as the single source in documentation. The two must
  never diverge.
