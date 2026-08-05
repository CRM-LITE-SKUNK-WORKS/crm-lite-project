# FE-ADR-012: Internationalization — Runtime Translation, Two Catalogues Under One Roof

## Status
Accepted (2026-07-23). Implements **FR-LANG-01** (AC-LANG-01-01/02/03).
**Read together with FE-ADR-008** — error text is served by this mechanism, not
by a parallel one.

**Reviewed 2026-08-05 (FR/AC v8-2, 03.08.2026):** AC-LANG-01-01/02/03 text is
byte-identical to the v8 Final extraction below. **No change to this decision.**
See `docs/requirements/document-delta.md`.

## Context
FR-LANG-01 was extracted verbatim from
`docs/source/requirements/CRM_Lite_FR_AC_v8_Final.docx` §2.8 on 2026-07-23:

| AC | Requirement |
|---|---|
| **AC-LANG-01-01** | A language switcher labelled `LBL-LANGUAGE` is present in the header of **every screen, including Login**; the list contains only Turkish (TR) and English (EN); the default selected language is **English**. |
| **AC-LANG-01-02** | When the language changes, all UI labels (Label Catalogue) and messages (Message Catalogue) are shown in the selected language **instantly**. |
| **AC-LANG-01-03** | The selected language is **preserved for the session** and is not reset when navigating between screens. |

The same document contains the two catalogues themselves — **21 `LBL-*` keys**
and **30 `MSG-*` keys**, each with approved EN and TR text. All 51 are
transcribed in `docs/frontend/mock-ui-analysis.md` §7A.5–§7A.6.

Backend behaviour was verified against the source on 2026-07-23:
`Accept-Language`, `LocaleResolver`, `LocaleContextHolder`, `MessageSource` and
`messages*.properties` yield **zero matches** under `backend/`. The backend is
language-neutral by design and returns `messageKey`s
(`functional-requirements.md`). Localization is entirely a frontend concern.

Keycloak already satisfies AC-LANG-01-01 for the login screen: the realm sets
`internationalizationEnabled: true`, `supportedLocales: ["en","tr"]`,
`defaultLocale: "en"`, and the `crm-lite` theme ships `messages_en.properties`,
`messages_tr.properties` and a locale switcher.

## Decision

### (a) Runtime i18n — one build, one image, instant switching
Translation is resolved at **runtime** from in-memory catalogues. The language is
a `signal` (FE-ADR-006); changing it re-evaluates every template that reads it,
so AC-LANG-01-02's "instantly" is satisfied with no page reload and no
re-navigation.

**Angular's compile-time i18n (`@angular/localize`) is rejected.** It produces a
**separate bundle per locale**, which means either two deployments or a
server-side locale router, and switching language becomes a full-page navigation
to a different bundle — a direct violation of AC-LANG-01-02. It would also
multiply FE-ADR-010's single container image by the number of locales for no
benefit at this scale.

**External i18n libraries are also not required.** The catalogue is small and
fully known, and Angular signals already provide the reactivity that such a
library's main value would be. The dependency-budget reasoning of FE-ADR-006 §4
and FE-ADR-011 §c applies here identically.

### (b) 🔴 ABSOLUTE RULE: no user-visible string is written in a component or template
Every piece of text a user can read — labels, buttons, headings, placeholders,
tooltips, `aria-label`s, empty-state copy, validation messages, dialog text,
toast text — comes from a translation key.

**This is binding at exactly the same level as the `data-testid` rule
(FE-ADR-009).** A hardcoded string is a review rejection, not a follow-up
ticket. There is no "temporary" hardcoded text: a string that ships once is
never found again.

### (c) Two catalogues, one mechanism
| Catalogue | Ownership | Key shape | Rule |
|---|---|---|---|
| **Contract catalogue** | Analyst / backend | `MSG-*`, `LBL-*` — flat, verbatim | **Names are never changed.** `MSG-*` keys are a backend contract (FE-ADR-008); renaming, grouping or camel-casing them breaks error resolution |
| **UI catalogue** | This project | `UI-{FEATURE}-{ELEMENT}` — feature-scoped | Covers everything the analyst catalogue does not: screen titles, field labels, placeholders, empty-state copy |

Both resolve through the **same service and the same lookup**. There is no
second mechanism, no fallback chain between them, and no ambiguity about where a
key lives — the prefix says it.

### (d) Language preference is stored in the browser
`localStorage` key `crm.lang`, and the login redirect carries it to Keycloak:

```
/oauth2/authorization/keycloak?ui_locales={lang}
```

so the Keycloak login page opens in the same language (AC-LANG-01-01 "including
Login"). This satisfies AC-LANG-01-03 more durably than `sessionStorage`, which
would reset per tab.

> **This is not an exception to FE-ADR-005 §P3.** That prohibition concerns
> **credentials and tokens** — security material that must never reach the
> browser. A language preference is a non-sensitive display setting whose
> disclosure has no security consequence. The two are different categories, not
> a rule and its exemption.

If no stored value exists, the default is **`en`** — per AC-LANG-01-01. The
browser's `navigator.language` is deliberately **not** consulted; the analyst
specified the default explicitly.

### (e) Culture-dependent formatting
Numbers, and any future currency values (FR-SALE), are formatted per the active
locale.

> ⚠️ **Dates are a deliberate, recorded exception.** By an explicit decision on
> 2026-07-23, the display format is **fixed `dd.MM.yyyy` in both languages**,
> and the DatePicker placeholder is `DD.MM.YYYY` in both.
> **Rationale:** the mock renders `02.11.1996` in its *English* interface, so
> the analyst already specified a language-independent Turkish-style date; and a
> format that flips between `14.05.1990` and `05/14/1990` makes the same record
> readable two different ways, which for identity data is a correctness risk
> rather than a courtesy.
> This exception is logged in `docs/frontend/scope-and-conflicts.md` so it can
> be revisited deliberately rather than discovered later.

**The transport format is always ISO `YYYY-MM-DD`**, in both directions,
regardless of display language (see `docs/frontend/mock-ui-analysis.md` §5A.1).
No localized string is ever sent to the API.

### (f) Mock text seeds the EN catalogue; TR comes from the analyst catalogue
The mock's interface is English. Its strings are the **starting content** of the
EN catalogue, and TR entries are added alongside. For the 51 analyst keys the
approved TR text already exists and is used verbatim — it is not re-translated.

Where the mock and the analyst catalogue disagree on wording, the conflict is
recorded rather than silently resolved. One such case already exists
(`MSG-CUST-NOT-FOUND` vs the mock's empty-state copy) — see
`docs/frontend/scope-and-conflicts.md`.

### (g) Translation files are organized per feature
```
core/i18n/
├── i18n.service.ts
├── catalog/
│   ├── messages.ts        # MSG-*  (analyst + project-authored, marked)
│   └── labels.ts          # LBL-*  (analyst)
features/customer/
├── search/i18n.ts         # UI-SEARCH-*
├── create/i18n.ts         # UI-CREATE-*
└── detail/i18n.ts         # UI-DETAIL-*
```

Every file uses the same `key → { en, tr }` shape, so a key can never exist in
one language and be missing in the other — the structure makes that class of
drift impossible. Keeping the analyst catalogues in one file each mirrors the
source document's table layout, so reconciling against a future revision of the
`.docx` is a diff rather than an audit.

### (h) Addendum (2026-07-24): parameterized translation — approved

Texts like the pagination range (`"1–20 / 137"`) cannot be produced by a plain
`translate(key)`. Approved by the team on 2026-07-24, deliberately narrow:

1. **Signature:** `translate(key, params?: Record<string, string | number>)`;
   the pipe accepts the same optional argument
   (`{{ 'UI-SEARCH-RANGE' | t: rangeParams }}`). The pipe is already impure, so
   language-change reactivity is unchanged.
2. **Named placeholders only** — catalogue text carries `{from}`, `{to}`,
   `{total}`; positional forms (`%s`, `{0}`) are forbidden. TR and EN word
   order differs ("1–20 of 137" vs "137 kayıttan 1–20"), and only named
   placeholders let each translation reorder freely.
3. **No ICU / plural engine** — every known need is pure interpolation, and
   Turkish plural forms do not vary by count ("1 kayıt", "5 kayıt"). Same
   dependency-budget reasoning as §a; if a real plural/select need arrives,
   that is a new decision, not an npm install.
4. **Missing parameter → the placeholder is left as-is + `console.warn`**
   (the FE-ADR-008 §3 stance: visible to developers, never silently swallowed,
   less harmful to users than degrading the whole string to a generic).
5. **Catalogue shape is unchanged** (`key → { en, tr }`) — no migration. The
   integrity test gains one assertion: the EN and TR texts of a key must use
   the **same placeholder set**, so `{total}` present in EN but missing in TR
   turns the suite red.

## Consequences
- The UI is fully translatable without a single backend change — the outcome
  `functional-requirements.md` designed for.
- One build, one container image, one deployment (FE-ADR-010) regardless of
  language count.
- Missing keys are caught at compile time where the key type is derived from the
  catalogue; unknown **runtime** keys arriving from the backend degrade to a
  generic message plus a console warning (FE-ADR-008 §3).
- Adding a third language is a new column in each catalogue file plus an entry
  in the switcher — no structural change. (Not requested: FR-LANG-01 specifies
  TR and EN only.)
- Ten `MSG-*` keys the backend returns have no analyst-approved text and require
  project-authored EN/TR (FE-ADR-008 §7). They are marked as project-authored so
  they remain distinguishable from analyst-approved entries.
- The language switcher must be present in the application header on every
  screen. The mock's static `EN` text in the header is **not** sufficient and is
  replaced by a real TR/EN control.
