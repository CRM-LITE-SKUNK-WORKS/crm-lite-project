import type { ApiFieldError } from './api-error';

/**
 * Binding backend field errors to form controls (FE-ADR-008 §5 — the interceptor
 * classifies, the feature presents).
 *
 * The backend's field paths are NOT uniform: the create body yields
 * `demographic.firstName` / `addresses[0].cityId` / `contactMedium.email`, while
 * `@RequestParam` validation on search yields a bare `firstName`. On top of that,
 * `demographic`, `addresses` and `contactMedium` can arrive as WHOLE-SECTION
 * paths that match no input at all.
 *
 * A feature that simply looked up each control by name would therefore silently
 * drop those — the user would press Save, get a 400, and see nothing. So the API
 * here is deliberately shaped to make that impossible: {@link matchFieldErrors}
 * hands back BOTH the per-control matches and everything left over, and the
 * caller has to do something with `unmatched`.
 */
export interface FieldErrorMatch {
  /** Control name → its error. Keys are exactly the names that were asked for. */
  readonly matched: ReadonlyMap<string, ApiFieldError>;
  /**
   * Errors that matched no requested control — section-level failures, fields
   * from another wizard step, or a field the backend added that the form does
   * not render yet. **Always surface these at form level**; never discard.
   */
  readonly unmatched: readonly ApiFieldError[];
}

/**
 * Match errors to the control names a screen actually renders.
 *
 * A control matches when the requested name equals either the full backend path
 * (`'contactMedium.email'`) or the parsed leaf (`'email'`), so the same call
 * works for the create wizard and the search screen without either knowing the
 * backend's path convention.
 *
 * Each error is consumed at most once, so an error can never be shown twice.
 */
export function matchFieldErrors(
  errors: readonly ApiFieldError[],
  controls: readonly string[],
): FieldErrorMatch {
  const matched = new Map<string, ApiFieldError>();
  const taken = new Set<ApiFieldError>();

  for (const control of controls) {
    // Prefer an exact path match over a leaf match: if a screen asks for
    // 'contactMedium.email' it must not be satisfied by some other 'email'.
    const hit =
      errors.find((error) => !taken.has(error) && error.field === control) ??
      errors.find((error) => !taken.has(error) && error.leaf === control);
    if (hit) {
      matched.set(control, hit);
      taken.add(hit);
    }
  }

  return { matched, unmatched: errors.filter((error) => !taken.has(error)) };
}

/**
 * The errors belonging to one element of a repeated section — e.g. the second
 * address row: `fieldErrorsAt(errors, 'addresses', 1)`. Without this the wizard
 * would attach every address error to the first row.
 */
export function fieldErrorsAt(
  errors: readonly ApiFieldError[],
  scope: string,
  index: number,
): readonly ApiFieldError[] {
  return errors.filter((error) => error.scope === scope && error.index === index);
}
