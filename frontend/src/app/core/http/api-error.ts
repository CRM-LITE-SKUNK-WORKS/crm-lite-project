import { HttpErrorResponse } from '@angular/common/http';
import { CATALOG } from '../i18n/catalog';

/**
 * Error normalization (FE-ADR-008). Every backend service returns the identical
 * envelope; the gateway (auth) returns the same minus `validationErrors`. This
 * module turns any `HttpErrorResponse` into ONE typed shape whose only
 * user-facing text source is `messageKey` (resolved via the i18n catalogue).
 * Raw backend English is preserved for logging but is NEVER rendered
 * (FE-ADR-008 §2).
 */

/** The raw wire envelope. `validationErrors` is present only on customer-service
 *  400s; absent on the gateway's auth errors (verified against ErrorResponse.java
 *  and GatewayErrorResponse.java, 2026-07-24). */
export interface BackendErrorEnvelope {
  readonly timestamp?: string;
  readonly status?: number;
  readonly error?: string;
  readonly messageKey?: string;
  readonly message?: string;
  readonly path?: string;
  readonly validationErrors?: Record<string, string> | null;
}

/** A single field-level failure, normalized so a feature can bind it to a control.
 *  The backend puts EITHER a catalogue messageKey (e.g. `MSG-VAL-EMAIL`) OR raw
 *  English (e.g. `"cityId is required"`) in each map value — so `messageKey` here
 *  is guaranteed to be a *renderable* catalogue key, and the raw value is kept
 *  only as diagnostics. */
export interface ApiFieldError {
  /** Field path exactly as the backend reports it — NOT uniform across handlers:
   *  body `@Valid` yields a full path (`demographic.firstName`,
   *  `addresses[0].cityId`, `contactMedium.email`) while `@RequestParam`
   *  validation yields a bare name (`firstName`). Kept verbatim; use the parsed
   *  parts below rather than string-matching this. */
  readonly field: string;
  /** ALWAYS a catalogue key: the backend value if that value is itself a known
   *  key, else a per-field frontend key, else the generic `UI-FIELD-INVALID`. */
  readonly messageKey: string;
  /** Raw backend text — English, un-catalogued. DIAGNOSTIC ONLY (FE-ADR-008 §2):
   *  never bind this in a template. */
  readonly backendText: string;
  /** Last path segment, index stripped: `addresses[0].cityId` → `cityId`. This is
   *  what a form control is normally keyed by. */
  readonly leaf: string;
  /** First path segment, index stripped: `addresses[0].cityId` → `addresses`;
   *  `null` for a bare name. Distinguishes `demographic.firstName` from a
   *  same-named search parameter. */
  readonly scope: string | null;
  /** Array index when the backend reported one: `addresses[0].cityId` → `0`.
   *  Lets the wizard attach the error to the RIGHT address row. */
  readonly index: number | null;
  /** `true` when the path names a whole section rather than a control
   *  (`demographic`, `addresses`, `contactMedium`). These have no input to attach
   *  to and MUST be surfaced at form level — see `matchFieldErrors`. */
  readonly structural: boolean;
}

/** The single typed error every failed HTTP call becomes (FE-ADR-008 §5). */
export interface ApiError {
  /** Discriminant so `isApiError` is a reliable narrow across the app. */
  readonly kind: 'api-error';
  /** HTTP status; `0` for a network/connection failure. */
  readonly status: number;
  /** The catalogue key — the ONLY source of user-facing text (FE-ADR-008 §1). */
  readonly messageKey: string;
  /** Field failures from a 400 validation body; `[]` for every other error. */
  readonly fieldErrors: readonly ApiFieldError[];
  /** Envelope `path`, for logging/correlation. */
  readonly path: string | null;
  /** Envelope `message` (or a raw non-JSON body). DIAGNOSTIC ONLY — never
   *  rendered (FE-ADR-008 §2). */
  readonly backendText: string | null;
}

const KEY_GENERIC = 'MSG-INTERNAL-ERROR';
const KEY_UNAVAILABLE = 'MSG-SERVICE-UNAVAILABLE';
const KEY_FIELD_FALLBACK = 'UI-FIELD-INVALID';

/**
 * Frontend-owned text for fields the backend annotates with RAW ENGLISH instead
 * of a catalogue key (`@NotNull(message = "cityId is required")`, …). Without
 * this they would all collapse to one generic sentence.
 *
 * Entries are deliberately **constraint-agnostic**: a field can fail for more
 * than one reason (`street` is both `@NotBlank` and `@Size(max=200)`), and the
 * envelope does not say which. So each key must stay true whatever failed —
 * which is why free-text fields are NOT listed here and keep the generic message:
 * asserting "required" when the real cause was "too long" would be a lie, and
 * the message renders next to its own control anyway, so naming the field adds
 * nothing. Only fields with a single unambiguous meaning are mapped.
 */
const FIELD_FALLBACK_KEYS = new Map<string, string>([
  // Selects: only "missing" or "not a valid code" can fail (ADR-002 lookup).
  ['cityId', 'UI-VAL-SELECT-REQUIRED'],
  ['districtId', 'UI-VAL-SELECT-REQUIRED'],
  ['gender', 'UI-VAL-SELECT-REQUIRED'],
  // Structural: @NotEmpty / @NotNull are the only constraints that can fire.
  ['addresses', 'UI-VAL-ADDRESS-REQUIRED'],
  ['demographic', 'UI-VAL-SECTION-INCOMPLETE'],
  ['contactMedium', 'UI-VAL-SECTION-INCOMPLETE'],
]);

/**
 * Fields we DELIBERATELY leave on the generic message (see the note above): free
 * text carrying more than one constraint, where naming a cause would risk lying.
 * Listing them explicitly is what separates "decided" from "nobody looked at it"
 * — only the latter warns.
 */
const INTENTIONALLY_GENERIC: ReadonlySet<string> = new Set([
  'street',
  'houseFlatNumber',
  'addressDescription',
]);

/** Paths that name a whole section, not an input (see `ApiFieldError.structural`). */
const STRUCTURAL_LEAVES: ReadonlySet<string> = new Set([
  'demographic',
  'addresses',
  'contactMedium',
]);

/** Narrow an unknown caught value (interceptors rethrow `ApiError`, not
 *  `HttpErrorResponse`) so features can `catch`/`catchError` type-safely. */
export function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    (value as { kind?: unknown }).kind === 'api-error'
  );
}

/** Split a backend field path into its usable parts. Exported because the parsing
 *  rule belongs in ONE place — features must never string-slice `field`. */
export function parseFieldPath(path: string): {
  leaf: string;
  scope: string | null;
  index: number | null;
} {
  const segments = path.split('.');
  const head = segments.length > 1 ? segments[0] : null;
  const tail = segments[segments.length - 1];

  const headParts = splitIndex(head);
  const tailParts = splitIndex(tail);

  return {
    leaf: tailParts.name,
    scope: headParts.name === '' ? null : headParts.name,
    // The index normally sits on the head (`addresses[0].cityId`), but a bare
    // `addresses[0]` puts it on the tail.
    index: headParts.index ?? tailParts.index,
  };
}

function splitIndex(segment: string | null): { name: string; index: number | null } {
  if (segment === null) {
    return { name: '', index: null };
  }
  const match = /^(.+)\[(\d+)\]$/.exec(segment);
  return match ? { name: match[1], index: Number(match[2]) } : { name: segment, index: null };
}

/** Convert a raw `HttpErrorResponse` into the typed `ApiError` (FE-ADR-008 §5). */
export function normalizeHttpError(error: HttpErrorResponse): ApiError {
  const envelope = asEnvelope(error.error);
  const status = error.status;
  return {
    kind: 'api-error',
    status,
    messageKey: envelope?.messageKey ?? fallbackMessageKey(status),
    fieldErrors: normalizeFieldErrors(envelope?.validationErrors),
    path: envelope?.path ?? null,
    backendText: envelope?.message ?? (typeof error.error === 'string' ? error.error : null),
  };
}

/** A response body counts as an envelope only if it carries a `messageKey`;
 *  anything else (HTML error page, proxy text, network `ProgressEvent`, `null`)
 *  falls back to a status-derived key. */
function asEnvelope(body: unknown): BackendErrorEnvelope | null {
  if (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as BackendErrorEnvelope).messageKey === 'string'
  ) {
    return body as BackendErrorEnvelope;
  }
  return null;
}

/** Only used when the body is NOT a backend envelope (no `messageKey`): a proxy
 *  502/503/504 or a dead upstream reads as "unavailable"; everything else
 *  (network 0, unexpected 5xx, parse failure) is a generic internal error
 *  (FE-ADR-008 §4). */
function fallbackMessageKey(status: number): string {
  return status === 502 || status === 503 || status === 504 ? KEY_UNAVAILABLE : KEY_GENERIC;
}

function normalizeFieldErrors(
  map: Record<string, string> | null | undefined,
): readonly ApiFieldError[] {
  if (!map) {
    return [];
  }
  return Object.entries(map).map(([field, value]) => {
    const { leaf, scope, index } = parseFieldPath(field);
    return {
      field,
      messageKey: resolveFieldMessageKey(value, leaf, field),
      backendText: value,
      leaf,
      scope,
      index,
      structural: index === null && STRUCTURAL_LEAVES.has(leaf),
    };
  });
}

/**
 * Resolution order: backend value if it is itself a catalogue key → per-field
 * frontend key → generic. The raw value is never a candidate (FE-ADR-008 §2).
 *
 * A field that reaches the generic fallback WITHOUT being listed as a deliberate
 * choice is a gap — a backend field nobody has mapped yet. FE-ADR-008 §3 requires
 * such gaps to be "visible to developers and invisible to users", so it warns
 * exactly like an unknown translation key does, and never cries wolf over the
 * fields we chose to leave generic.
 */
function resolveFieldMessageKey(backendValue: string, leaf: string, field: string): string {
  if (Object.prototype.hasOwnProperty.call(CATALOG, backendValue)) {
    return backendValue;
  }
  const mapped = FIELD_FALLBACK_KEYS.get(leaf);
  if (mapped !== undefined) {
    return mapped;
  }
  if (!INTENTIONALLY_GENERIC.has(leaf)) {
    console.warn(
      `[http] field "${field}" carries un-catalogued backend text and has no per-field key; ` +
        `showing ${KEY_FIELD_FALLBACK}. Map it in FIELD_FALLBACK_KEYS or add it to INTENTIONALLY_GENERIC.`,
    );
  }
  return KEY_FIELD_FALLBACK;
}

/** Test/guard hook: every mapped key must exist in the catalogue. */
export const FIELD_FALLBACK_KEY_VALUES: readonly string[] = [
  ...FIELD_FALLBACK_KEYS.values(),
  KEY_FIELD_FALLBACK,
];
