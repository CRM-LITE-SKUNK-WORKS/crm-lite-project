import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { isApiError, type ApiError } from '../../../core/http';
import { CATALOG } from '../../../core/i18n';
import { OrderApiService } from '../data/order-api.service';
import type { CreateDraftRequest, ProcessingStatus, SubmitDraftRequest } from '../model';

/**
 * Renderable key for a failed draft/submit HTTP call, chosen by STATUS —
 * deliberately not by any error-code string in the body. Both order-service
 * commands (ADR-018 §6) fan out across services whose failure surface grows
 * independently, so binding to a specific code string would mean re-editing
 * this map on every such change. Branching on status degrades gracefully
 * instead: an unknown 400 still reads as "some fields are invalid", an
 * unknown 503 still reads as "not created, try again".
 */
/**
 * Keys the HTTP layer invents when a response carries no envelope at all
 * (`normalizeHttpError` → `fallbackMessageKey`). They are in the catalogue, so
 * they LOOK like a backend answer, but nothing was actually said — which is
 * exactly the case where the sale-specific wording is worth more. Treated as
 * "no key" below so {@link fallbackKeyFor} gets to speak.
 */
const HTTP_LAYER_FALLBACKS: ReadonlySet<string> = new Set([
  'MSG-SERVICE-UNAVAILABLE',
  'MSG-INTERNAL-ERROR',
]);

function fallbackKeyFor(status: number): string {
  // 0 = no response at all (network/connection), 502/503/504 = a hop was down.
  // Either way the command did NOT complete, so the user is told it is safe
  // to retry (draft creation and submit are both re-triable — the idempotency
  // key on each makes a retry safe rather than a duplicate).
  if (status === 0 || status === 502 || status === 503 || status === 504) {
    return 'MSG-SALE-SUBMIT-UNAVAILABLE';
  }
  // Any rejected-input status: a bad characteristic value, a service address
  // that is not the customer's, a basket the server refuses, an account that
  // is not (or no longer) Active.
  if (status === 400 || status === 409 || status === 422) {
    return 'MSG-VALIDATION-ERROR';
  }
  return 'MSG-INTERNAL-ERROR';
}

/** Resolve the key the screen renders for a failed HTTP call: the backend's
 *  own catalogued key wins, an un-catalogued or HTTP-layer-invented one falls
 *  through to the status-derived fallback (FE-ADR-008 §2). */
function renderableKeyFor(error: ApiError | null, status: number): string {
  if (error !== null && error.messageKey in CATALOG && !HTTP_LAYER_FALLBACKS.has(error.messageKey)) {
    return error.messageKey;
  }
  return fallbackKeyFor(status);
}

/** Bounded polling backoff (ADR-018 §6's status resource). Starts fast so the
 *  common case — a saga that finishes in well under a second — reads as
 *  near-instant; a run of transient failures backs off exponentially up to
 *  the cap rather than turning an outage into a request storm. */
const POLL_INTERVAL_MS = 2_000;
const POLL_MAX_BACKOFF_MS = 30_000;

/**
 * The asynchronous SALE lifecycle for ONE sale (ADR-018 §6): draft → submit →
 * poll. Provided by the wizard, not root (dies with the flow, AC-SALE-01-16).
 *
 * 🔴 **ONE draft per store instance.** {@link startDraft} is a no-op once a
 * draft exists or one is already in flight — an effect that re-runs (Angular
 * effects rerun on ANY tracked signal change, not just the first time their
 * condition becomes true) must not mint a second WAIT order for the same
 * wizard session.
 *
 * 🔴 **Two independent Idempotency-Keys** (ADR-018 §3): draft creation and
 * submission are two different logical HTTP commands, each with its own key,
 * reused only across RETRIES of that same command — never minted fresh just
 * because the method was called again, never shared across the two commands
 * (the backend hashes the request PATH into the key's conflict check, so
 * reusing one across them is a 409, not a replay).
 *
 * 🔴 **`submit()` is NOT idempotent from the client's own double-click**, same
 * reasoning as the legacy contract: an in-flight guard plus a disabled button
 * are two independent guards against a second `202` command for the same
 * draft. A SUCCEEDED submit does not clear the guard — {@link accepted} stays
 * true forever once the saga has started.
 */
@Injectable()
export class OrderSubmitStore {
  private readonly api = inject(OrderApiService);
  private readonly destroyRef = inject(DestroyRef);

  // --- Draft (Start New Sale) ------------------------------------------------
  private readonly _draftInFlight = signal(false);
  private readonly _orderNumber = signal<string | null>(null);
  private readonly _draftError = signal<ApiError | null>(null);
  private readonly _draftFailed = signal(false);

  /** True while the draft POST is open — the Submit screen shows a loading
   *  row instead of the order number, and Submit stays disabled. */
  readonly draftPending = this._draftInFlight.asReadonly();
  /** The KR-12 number from the WAIT draft; `null` until it exists
   *  (AC-SALE-01-12: available on the Submit screen BEFORE Submit). */
  readonly orderNumber = this._orderNumber.asReadonly();
  /** The key the screen renders for a failed draft creation — never a
   *  fabricated order number, never a fall back to the legacy endpoint. */
  readonly draftErrorKey = computed<string | null>(() => {
    if (!this._draftFailed()) return null;
    const error = this._draftError();
    return renderableKeyFor(error, error?.status ?? 0);
  });

  private _draftIdempotencyKey: string | undefined;
  private _draftIdempotencyKeyBody: string | undefined;

  /** One UUID per logical draft-creation attempt (ADR-018 §3), reused across
   *  retries of that SAME attempt. The body is always `{ accountNumber }` for
   *  one wizard session, so in practice this mints exactly once and reuses
   *  forever — including across a retried failure. */
  private draftIdempotencyKeyFor(body: CreateDraftRequest): string {
    const bodyJson = JSON.stringify(body);
    if (this._draftIdempotencyKey !== undefined && this._draftIdempotencyKeyBody === bodyJson) {
      return this._draftIdempotencyKey;
    }
    const key = crypto.randomUUID();
    this._draftIdempotencyKey = key;
    this._draftIdempotencyKeyBody = bodyJson;
    return key;
  }

  /**
   * Start New Sale (ADR-018 §6). A no-op while a draft is already in flight
   * or once one exists — safe to call from an `effect()` that may re-run.
   * Calling it again after a FAILED draft is a legitimate retry and reuses
   * the same Idempotency-Key.
   */
  startDraft(accountNumber: string): void {
    if (this._draftInFlight() || this._orderNumber() !== null) return;
    const body: CreateDraftRequest = { accountNumber };
    const idempotencyKey = this.draftIdempotencyKeyFor(body);
    this._draftInFlight.set(true);
    this._draftError.set(null);
    this._draftFailed.set(false);

    this.api.createDraft(body, idempotencyKey).subscribe({
      next: (status) => {
        this._orderNumber.set(status.orderNumber);
        this._draftInFlight.set(false);
      },
      error: (err: unknown) => {
        this._draftError.set(isApiError(err) ? err : null);
        this._draftFailed.set(true);
        this._draftInFlight.set(false);
      },
    });
  }

  /**
   * Pre-submit Cancel (AC-SALE-01-16): abandon the WAIT draft, best-effort.
   * A no-op once the sale has been accepted (§ below) — Cancel must never
   * pretend to cancel a running saga — and a no-op if no draft exists yet.
   *
   * Fire-and-forget by design: a WAIT draft can never automatically start
   * (the backend's own guarantee, ADR-018 §6), so a network failure HERE is
   * not a reason to block the user from leaving, retry, or fall back to
   * anything. The draft is left for the backend's own expiry cleanup, which
   * only ever cancels.
   */
  abandonDraft(): void {
    const orderNumber = this._orderNumber();
    if (orderNumber === null || this.accepted()) return;
    this.api.abandonDraft(orderNumber).subscribe({ next: () => undefined, error: () => undefined });
  }

  // --- Submit + processing ---------------------------------------------------
  private readonly _submitting = signal(false);
  private readonly _accepted = signal(false);
  private readonly _processingStatus = signal<ProcessingStatus | null>(null);
  private readonly _failureMessageKey = signal<string | null>(null);
  private readonly _submitError = signal<ApiError | null>(null);
  private readonly _submitFailed = signal(false);

  /** True while the `202` submit POST itself is open (before the response
   *  arrives) — distinct from {@link processingStatus}'s `PROCESSING`, which
   *  covers the SAGA running after the 202 has already come back. */
  readonly submitting = this._submitting.asReadonly();
  /** True once the submit has been accepted — from here on Previous/Cancel
   *  must never return to an editable draft and Submit can never fire again,
   *  REGARDLESS of how the saga eventually resolves. */
  readonly accepted = this._accepted.asReadonly();
  /** `DRAFT` before Submit, `PROCESSING` after the 202 while the saga runs,
   *  then `COMPLETED`/`FAILED`. `null` only before the first status is known
   *  (i.e. before Submit has even been attempted). */
  readonly processingStatus = this._processingStatus.asReadonly();
  /** A catalog key, present only once {@link processingStatus} is `FAILED`. */
  readonly failureMessageKey = this._failureMessageKey.asReadonly();

  readonly succeeded = computed(() => this._processingStatus() === 'COMPLETED');
  readonly failed = computed(() => this._processingStatus() === 'FAILED');

  /** Submit button gate: no draft yet, a request already open, or already
   *  accepted — any of these must refuse a new `submit()` call. */
  readonly locked = computed(() => this.draftPending() || this._submitting() || this._accepted());
  /** Previous/Cancel gate: once accepted, the draft is no longer editable and
   *  Cancel must not pretend to cancel the running saga (ADR-018 §6). Drafting
   *  is intentionally NOT included — the user may still leave while the draft
   *  itself is still being created. */
  readonly navigationLocked = computed(() => this._submitting() || this._accepted());

  /** The key the screen renders for a submit-time HTTP rejection (400/409/503
   *  before acceptance) — NOT the same thing as a terminal `FAILED` saga,
   *  which renders {@link failureMessageKey} instead. */
  readonly submitErrorKey = computed<string | null>(() => {
    if (!this._submitFailed()) return null;
    const error = this._submitError();
    return renderableKeyFor(error, error?.status ?? 0);
  });

  /** Field-level rejections (400 `validationErrors`), e.g.
   *  `MSG-VAL-CHAR-REQUIRED` on one characteristic (AC-SALE-01-19). */
  readonly fieldErrorKeys = computed<readonly string[]>(() =>
    (this._submitError()?.fieldErrors ?? []).map((e) => e.messageKey),
  );

  private _submitIdempotencyKey: string | undefined;
  private _submitIdempotencyKeyBody: string | undefined;

  /** One UUID per logical submit attempt (ADR-018 §3), reused across retries
   *  of that SAME attempt — a SEPARATE key from the draft's own, and minted
   *  fresh only when the body actually changed since the last attempt (the
   *  user edited the basket, the address or the campaign). */
  private submitIdempotencyKeyFor(body: SubmitDraftRequest): string {
    const bodyJson = JSON.stringify(body);
    if (this._submitIdempotencyKey !== undefined && this._submitIdempotencyKeyBody === bodyJson) {
      return this._submitIdempotencyKey;
    }
    const key = crypto.randomUUID();
    this._submitIdempotencyKey = key;
    this._submitIdempotencyKeyBody = bodyJson;
    return key;
  }

  /**
   * Confirm Submit (AC-SALE-01-15). Requires a draft to already exist
   * ({@link locked} covers this) and starts polling the instant the `202`
   * lands — never before, and never a second time for the same order.
   */
  submit(body: SubmitDraftRequest): void {
    const orderNumber = this._orderNumber();
    if (this.locked() || orderNumber === null) return;
    const idempotencyKey = this.submitIdempotencyKeyFor(body);
    this._submitting.set(true);
    this._submitError.set(null);
    this._submitFailed.set(false);

    this.api.submitDraft(orderNumber, body, idempotencyKey).subscribe({
      next: (status) => {
        this._processingStatus.set(status.processingStatus);
        this._failureMessageKey.set(status.failureMessageKey);
        // Deliberately NOT clearing `_submitting` before setting `_accepted`:
        // between the two writes `locked()` must never dip false, or a queued
        // click could slip a second submit through.
        this._accepted.set(true);
        this._submitting.set(false);
        this.startPolling(orderNumber);
      },
      error: (err: unknown) => {
        this._submitError.set(isApiError(err) ? err : null);
        this._submitFailed.set(true);
        this._submitting.set(false);
      },
    });
  }

  /** Dismiss the submit error banner without touching in-flight/accepted state. */
  clearSubmitError(): void {
    this._submitError.set(null);
    this._submitFailed.set(false);
  }

  // --- Status polling ---------------------------------------------------------
  private pollTimer: ReturnType<typeof setTimeout> | undefined;
  private pollBackoffMs = POLL_INTERVAL_MS;

  constructor() {
    // Stops polling on component destruction — the store is provided by the
    // wizard, so this fires when the flow is left, success or not.
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  private startPolling(orderNumber: string): void {
    this.stopPolling(); // exactly ONE polling stream per order
    this.pollBackoffMs = POLL_INTERVAL_MS;
    this.schedulePoll(orderNumber, this.pollBackoffMs);
  }

  private schedulePoll(orderNumber: string, delayMs: number): void {
    this.pollTimer = setTimeout(() => this.pollOnce(orderNumber), delayMs);
  }

  private pollOnce(orderNumber: string): void {
    this.api.status(orderNumber).subscribe({
      next: (status) => {
        this._processingStatus.set(status.processingStatus);
        this._failureMessageKey.set(status.failureMessageKey);
        if (status.processingStatus === 'COMPLETED' || status.processingStatus === 'FAILED') {
          this.stopPolling();
          return;
        }
        // A GOOD response resets the backoff — only a run of transient
        // failures should stretch the interval.
        this.pollBackoffMs = POLL_INTERVAL_MS;
        this.schedulePoll(orderNumber, this.pollBackoffMs);
      },
      error: () => {
        // A temporary status-request failure is NOT a SALE failure (ADR-018
        // §8): the saga keeps running server-side regardless of whether this
        // poll landed. Back off, bounded, and try again — never mark FAILED
        // on a client-observed transport error.
        this.pollBackoffMs = Math.min(this.pollBackoffMs * 2, POLL_MAX_BACKOFF_MS);
        this.schedulePoll(orderNumber, this.pollBackoffMs);
      },
    });
  }

  private stopPolling(): void {
    if (this.pollTimer !== undefined) {
      clearTimeout(this.pollTimer);
      this.pollTimer = undefined;
    }
  }
}
