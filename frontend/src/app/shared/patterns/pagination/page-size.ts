/**
 * The page-size contract — **the single source of truth for every paginated
 * list in the app** (KR-04; ADR-005 §Amendment, 29.07.2026; scope §2.3/§2.4).
 *
 * 🔴 This is NOT a UX preference, it is a server-enforced whitelist. The API
 * accepts `size` ∈ {15, 30, 50} and answers anything else with
 * `400 MSG-VALIDATION-ERROR` (`validationErrors.size = "must be one of 15, 30,
 * 50"`). The superseded 20/50/100 list produced exactly that 400 in the wild,
 * which is why the values now live here instead of inside one feature: a
 * sibling feature may not import `features/customer/model` (FE-ADR-003), so a
 * feature-local copy was guaranteed to be re-typed — and re-typed wrong.
 *
 * Lives beside the `Pagination` pattern and ships through the same
 * `shared/patterns` barrel, so a screen picks up the control and the values it
 * is allowed to feed it in one import. Every list consumes THIS module; no
 * screen writes a page-size literal.
 *
 * KR-04 also requires `size` to be sent EXPLICITLY on every request, so the UI
 * and the server can never silently disagree if the API default moves again.
 */
export const PAGE_SIZE_OPTIONS = [15, 30, 50] as const;

/** The only sizes the API accepts. A `number` is deliberately NOT assignable. */
export type PageSize = (typeof PAGE_SIZE_OPTIONS)[number];

/** API default is also 15 (ADR-005 §Amendment) — kept identical on purpose. */
export const DEFAULT_PAGE_SIZE: PageSize = 15;

/** Narrows an arbitrary number (a route param, a stored preference, a widget
 *  value) to the whitelist. The only sanctioned way in — screens must not
 *  re-implement this check. */
export function isPageSize(value: number): value is PageSize {
  return (PAGE_SIZE_OPTIONS as readonly number[]).includes(value);
}
