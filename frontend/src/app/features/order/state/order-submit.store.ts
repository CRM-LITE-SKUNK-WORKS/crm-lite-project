import { Injectable, computed, inject, signal } from '@angular/core';
import { isApiError, type ApiError } from '../../../core/http';
import { CATALOG } from '../../../core/i18n';
import { OrderApiService } from '../data/order-api.service';
import type { OrderResponse, SubmitOrderRequest } from '../model';

/**
 * Renderable key for a failed submit, chosen by HTTP STATUS — deliberately not
 * by any error-code string in the body.
 *
 * `POST /api/orders` fans out across six services (ADR-016), so its failure
 * surface grows whenever any of them adds a rule. The most recent example: the
 * product write path now rejects a `serviceAddressId` that does not belong to
 * the customer (400) and reports customer-service being unreachable (503).
 * Binding to a specific code string would mean re-editing this map on every
 * such change and rendering `MSG-INTERNAL-ERROR` in the window before someone
 * does. Branching on status degrades gracefully instead: an unknown 400 still
 * reads as "some fields are invalid", an unknown 503 still reads as "not
 * created, try again".
 *
 * ⚠ ASSUMPTION — VERIFY AFTER MERGE. The 400/503 arms above were written from a
 * description of a dev-branch commit that is not merged here; the real response
 * body shape and the name of its error-code field could not be read from the
 * code. Once that commit is merged, confirm the envelope still carries a
 * `messageKey` (in which case the branches below are never reached for it) and
 * whether a dedicated catalogue key should replace the generic fallback.
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
  // Either way the sale did NOT complete — ADR-016 compensates a partial run —
  // so the user is told it is safe to retry.
  if (status === 0 || status === 502 || status === 503 || status === 504) {
    return 'MSG-SALE-SUBMIT-UNAVAILABLE';
  }
  // Any rejected-input status: a bad characteristic value, a service address
  // that is not the customer's, a basket the server refuses.
  if (status === 400 || status === 409 || status === 422) {
    return 'MSG-VALIDATION-ERROR';
  }
  return 'MSG-INTERNAL-ERROR';
}

/**
 * The single `POST /api/orders` call and its outcome (AC-SALE-01-15).
 *
 * 🔴 **DUPLICATE-SUBMIT GUARD.** `POST /api/orders` is NOT idempotent: a second
 * request creates a SECOND set of products and orphans the first. The backend
 * hit exactly this — httpclient5 re-executed a request that had answered 503 —
 * and disabled transport retries on every outbound client (ADR-016 §5.3b). The
 * client must not reintroduce the same bug from the other side, so `submit()`
 * returns immediately while a request is in flight. The screen ALSO disables
 * the button; two independent guards, because a disabled button is not a check
 * (the same reasoning the backend applies to its own screen-level rules).
 *
 * A failed submit clears the flag: the sale was compensated server-side and the
 * user may legitimately retry. A SUCCEEDED submit does NOT clear it — the order
 * exists and must never be sent twice.
 *
 * Not `providedIn: 'root'` — provided by the wizard, so the outcome dies with
 * the flow (AC-SALE-01-16).
 */
@Injectable()
export class OrderSubmitStore {
  private readonly api = inject(OrderApiService);

  private readonly _inFlight = signal(false);
  private readonly _order = signal<OrderResponse | null>(null);
  private readonly _error = signal<ApiError | null>(null);
  /** Set on ANY failed submit, including one that produced no typed error, so
   *  a rejection can never end up silently invisible. */
  private readonly _failed = signal(false);

  /** True while the POST is open — binds to the button's `loading`/`disabled`. */
  readonly inFlight = this._inFlight.asReadonly();
  /** The created order (201 body); `null` until the submit succeeds. */
  readonly order = this._order.asReadonly();
  readonly error = this._error.asReadonly();

  /**
   * The key the screen renders (FE-ADR-008 §2 — the backend's `message` field
   * is never shown). The backend's OWN `messageKey` wins whenever the catalogue
   * can render it; only an absent or un-catalogued key falls through to
   * {@link fallbackKeyFor}. That ordering keeps the backend the authority on
   * what went wrong and keeps the client honest about what it can display.
   */
  readonly errorKey = computed<string | null>(() => {
    const error = this._error();
    if (error === null) return this._failed() ? 'MSG-INTERNAL-ERROR' : null;
    if (error.messageKey in CATALOG && !HTTP_LAYER_FALLBACKS.has(error.messageKey)) {
      return error.messageKey;
    }
    return fallbackKeyFor(error.status);
  });

  /** Field-level rejections (400 `validationErrors`), e.g.
   *  `MSG-VAL-CHAR-REQUIRED` on one characteristic (AC-SALE-01-19). */
  readonly fieldErrorKeys = computed<readonly string[]>(() =>
    (this._error()?.fieldErrors ?? []).map((e) => e.messageKey),
  );

  /** The screen switches to its success state on this (AC-SALE-01-15). */
  readonly succeeded = computed(() => this._order() !== null);

  /** Once the order exists, no further submit may ever be attempted. */
  readonly locked = computed(() => this._inFlight() || this.succeeded());

  /**
   * The Idempotency-Key for the CURRENT logical submit attempt (ADR-016
   * idempotency addendum) and the exact body it was minted for. `undefined`
   * until the first submit — nothing to reuse yet.
   */
  private _idempotencyKey: string | undefined;
  private _idempotencyKeyBody: string | undefined;

  /**
   * One UUID per logical submit attempt, reused across retries of that SAME
   * attempt — never minted fresh just because `submit()` was called again.
   *
   * "Same attempt" is decided the only way that does not require this store to
   * duplicate the backend's own request-hash logic: byte-identical JSON. A
   * failed/timed-out submit followed by clicking Submit again with an
   * UNCHANGED basket is a retry — reuse the key, so the backend's own replay
   * protection (same key, same normalized body) answers with the original
   * result instead of re-running the three-service orchestration. Editing the
   * basket (or the address, or the campaign) between attempts changes the JSON,
   * which is precisely when a NEW key is required: reusing the old one would
   * hit the backend's same-key-different-payload 409 instead of submitting the
   * edited sale. `OrderSubmitStore` is provided per wizard flow (dies with it),
   * so this key never leaks into an unrelated sale.
   */
  private idempotencyKeyFor(body: SubmitOrderRequest): string {
    const bodyJson = JSON.stringify(body);
    if (this._idempotencyKey !== undefined && this._idempotencyKeyBody === bodyJson) {
      return this._idempotencyKey;
    }
    const key = crypto.randomUUID();
    this._idempotencyKey = key;
    this._idempotencyKeyBody = bodyJson;
    return key;
  }

  submit(body: SubmitOrderRequest): void {
    if (this.locked()) return;
    const idempotencyKey = this.idempotencyKeyFor(body);
    this._inFlight.set(true);
    this._error.set(null);
    this._failed.set(false);

    this.api.submit(body, idempotencyKey).subscribe({
      next: (order) => {
        this._order.set(order);
        // Deliberately NOT clearing `_inFlight` before setting `_order`: between
        // the two writes `locked()` must never dip false, or a queued click
        // could slip a second POST through.
        this._inFlight.set(false);
      },
      error: (err: unknown) => {
        this._error.set(isApiError(err) ? err : null);
        this._failed.set(true);
        this._inFlight.set(false);
      },
    });
  }

  /** Dismiss the error banner without touching the in-flight/success state. */
  clearError(): void {
    this._error.set(null);
    this._failed.set(false);
  }
}
