import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import type { CharacteristicDataType, CharacteristicResponse } from './catalog.model';

/**
 * Client-side characteristic validation (AC-SALE-01-17/18/19) — the MIRROR of
 * product-service's `CharacteristicValidationRules`.
 *
 * WHY THIS EXISTS AT ALL. The first cut of the Product Configuration screen
 * deliberately shipped with NO client validator: the server owns `data_type`,
 * so the server decides. That is still true — but AC-SALE-01-12/18/19 do not
 * ask "who decides", they ask WHERE and WHEN the user is told: under the field,
 * on `LBL-NEXT`, before the Submit screen opens. A rejection that only arrives
 * from `POST /api/orders` lands two screens later, on a field the user can no
 * longer see. So the rules are duplicated here ON PURPOSE.
 *
 * 🔑 THE DUPLICATION IS A CONTRACT, NOT A CONVENIENCE. Every predicate below
 * is written against the SAME source as
 * `product-service/…/product/rules/CharacteristicValidationRules.java`, rule for
 * rule:
 *
 *   TEXT     always valid                       (`case TYPE_TEXT -> true`)
 *   NUMBER   parses as `new BigDecimal(value)`
 *   BOOLEAN  exactly `true`/`false`, any case   (NOT "1"/"0"/"yes")
 *   DATE     parses as `LocalDate.parse(value)` — strict ISO `yyyy-MM-dd`
 *   unknown  INVALID (a fifth type means the catalog grew one this code has
 *            never seen; accepting it would send a value nothing can interpret)
 *
 * Values are compared TRIMMED and blankness is `isBlank()` semantics (a
 * whitespace-only mandatory value is missing, not present), because that is what
 * the backend does. If the two ever diverge, the backend wins and this file is
 * the one that is wrong (FE-ADR-007 §3): the client check is an EARLY answer,
 * never the authoritative one — `POST /api/orders` still re-validates and its
 * `MSG-VAL-CHAR-*` keys still render on step 3.
 */

/**
 * `new BigDecimal(String)` accepts: an optional sign, then digits with an
 * optional fractional part (either side may be empty — `5.` and `.5` both
 * parse, `.` alone does not), then an optional exponent.
 */
const NUMBER_PATTERN = /^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?$/;

/** `LocalDate.parse` is strict: exactly 4-2-2 digits, and a real calendar day. */
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** Backend `value.isBlank()` — null, empty, or whitespace only. */
export function isBlankValue(value: string | null | undefined): boolean {
  return value === null || value === undefined || value.trim() === '';
}

function isNumberValue(value: string): boolean {
  return NUMBER_PATTERN.test(value);
}

function isBooleanValue(value: string): boolean {
  const lower = value.toLowerCase();
  return lower === 'true' || lower === 'false';
}

/**
 * Rejects `2026-02-30` the way `LocalDate.parse` does. Built through
 * `setUTCFullYear` rather than `Date.UTC`, which silently maps years 0–99 into
 * the 1900s and would pass `0099-01-01` off as valid.
 */
function isIsoDateValue(value: string): boolean {
  if (!ISO_DATE_PATTERN.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  if (month < 1 || month > 12 || day < 1 || day > 31) return false;
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  return (
    date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
  );
}

/**
 * Does a NON-BLANK value fit its declared data type? Blank is not this
 * function's business — that is the mandatory check (`MSG-VAL-CHAR-REQUIRED`),
 * and a blank OPTIONAL value is legal on both sides.
 */
export function matchesDataType(dataType: CharacteristicDataType | string, value: string): boolean {
  switch (dataType?.toUpperCase()) {
    case 'TEXT':
      return true;
    case 'NUMBER':
      return isNumberValue(value);
    case 'BOOLEAN':
      return isBooleanValue(value);
    case 'DATE':
      return isIsoDateValue(value);
    default:
      return false;
  }
}

/** `MSG-VAL-CHAR-REQUIRED` (AC-SALE-01-18). Whitespace does not satisfy it. */
export function charRequiredValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null =>
    isBlankValue(control.value as string | null) ? { charRequired: true } : null;
}

/**
 * Today as ISO `yyyy-MM-dd` in the USER'S OWN calendar.
 *
 * NOT `toISOString().slice(0, 10)`: that answers in UTC, so in `Europe/Istanbul`
 * (UTC+3) every value between 00:00 and 03:00 local would report YESTERDAY as
 * "today" and quietly accept a date the user can see is in the past.
 */
export function todayIso(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/**
 * ⚠ FRONTEND-ONLY — deliberately NOT part of the product-service mirror above.
 *
 * A DATE characteristic on a NEW order describes something that has not happened
 * yet (the catalog's only one today is "Commitment End Date"), so a past date is
 * a typo rather than history. The SERVER does not enforce this: product-service's
 * `CharacteristicValidationRules` only asks whether the value parses as a
 * `LocalDate`. So this is a data-entry guard on this screen, not a contract rule
 * — a direct `POST /api/orders` with a past date is still accepted today. If the
 * rule is ever meant to be real, it belongs in product-service FIRST and this
 * comment goes away.
 *
 * A value that is not a valid ISO date is left alone: `charFormatValidator`
 * already owns that case, and reporting both would put two errors on one field.
 */
export function charNotPastValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string | null;
    if (isBlankValue(value)) return null;
    const iso = (value as string).trim();
    if (!isIsoDateValue(iso)) return null;
    // Zero-padded ISO sorts lexicographically the same way it sorts by date.
    return iso < todayIso() ? { charDatePast: true } : null;
  };
}

/** `MSG-VAL-CHAR-FORMAT` (AC-SALE-01-19). A blank value never fails FORMAT — it
 *  is either legal (optional) or already `charRequired` (mandatory). */
export function charFormatValidator(dataType: CharacteristicDataType): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string | null;
    if (isBlankValue(value)) return null;
    return matchesDataType(dataType, (value as string).trim()) ? null : { charFormat: true };
  };
}

/** The full validator set for one characteristic, in the order the backend
 *  applies them: required first, then format — then the frontend-only
 *  past-date guard, which only DATE fields can fail. */
export function characteristicValidators(field: CharacteristicResponse): ValidatorFn[] {
  const validators: ValidatorFn[] = [];
  if (field.mandatory) validators.push(charRequiredValidator());
  validators.push(charFormatValidator(field.dataType));
  if (field.dataType === 'DATE') validators.push(charNotPastValidator());
  return validators;
}

/**
 * The analyst message key for a characteristic control's errors, or `null` when
 * it is valid.
 *
 * `charDatePast` answers with a `UI-*` key ON PURPOSE. `MSG-*` keys are the
 * backend/analyst contract resolved against `error.messageKey` (catalog/messages
 * header, FE-ADR-012 §c); the past-date rule has no server counterpart, so
 * inventing an `MSG-VAL-CHAR-*` name for it would put a key in that namespace
 * that no backend will ever send.
 *
 * Everything else maps to `MSG-VAL-CHAR-FORMAT`, which is how `DatePicker`'s own
 * `{ invalidDate: true }` (raised for half-typed text on blur) reaches the user
 * as "value does not fit the field's type" — the same sentence the server would
 * answer with.
 */
export function characteristicErrorKey(errors: ValidationErrors | null): string | null {
  if (!errors) return null;
  if (errors['charRequired']) return 'MSG-VAL-CHAR-REQUIRED';
  if (errors['charDatePast']) return 'UI-VAL-CHAR-DATE-PAST';
  return 'MSG-VAL-CHAR-FORMAT';
}
