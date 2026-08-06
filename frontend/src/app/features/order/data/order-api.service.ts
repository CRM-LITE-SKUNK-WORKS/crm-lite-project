import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { type Observable } from 'rxjs';
import { type OrderResponse, type SubmitOrderRequest } from '../model';

/** Wire header name for the idempotency contract (ADR-016 idempotency addendum,
 *  2026-08-06) — kept here, not in the model, since it is transport, not payload. */
export const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key';

/**
 * order-service HTTP client (docs/api/order-service.md). The ONLY place
 * `HttpClient` is called for the order aggregate (FE-ADR-006 §3).
 *
 * The endpoint list is deliberately just TWO methods — that is the whole
 * contract:
 *  - **No order list.** No FR asks for one; inventing a list contract would
 *    mean inventing sorting/filtering/pagination rules no analyst has written.
 *    So there is no "my orders" screen and no method for it (scope §2B.5).
 *  - **No cancel.** KR-7 leaves cancellation out of phase; `CANCELLED` is
 *    reached only by the orchestration's own compensation (scope §2B.6).
 *
 * Conventions upheld: relative paths only (FE-ADR-004 §1); no error handling
 * here (FE-ADR-008 §5) — the core interceptor normalizes every failure,
 * including the relayed `MSG-SALE-*` / `MSG-VAL-CHAR-*` keys, to `ApiError`.
 */
@Injectable({ providedIn: 'root' })
export class OrderApiService {
  private readonly http = inject(HttpClient);

  private static readonly BASE = '/api/orders';

  /**
   * `POST /api/orders` — submit the whole basket as one atomic command; 201
   * with the created order (AC-SALE-01-15).
   *
   * The backend is now idempotent PER `Idempotency-Key` (ADR-016 idempotency
   * addendum): the same key + the same body replays the original result
   * instead of creating a second order. That does NOT make this method safe
   * to call speculatively — `idempotencyKey` must be the SAME UUID for every
   * transport-level attempt of one logical submit and a FRESH one for a new
   * attempt (`OrderSubmitStore` owns that decision); a wrong key here would
   * either create a duplicate order (a fresh key on a real retry) or wrongly
   * replay a stale result (a reused key on a genuinely new submit). The
   * in-flight guard in `OrderSubmitStore` still stands — this header closes a
   * different gap (server-observed retries), not the client's own double-click.
   */
  submit(body: SubmitOrderRequest, idempotencyKey: string): Observable<OrderResponse> {
    const headers = new HttpHeaders().set(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
    return this.http.post<OrderResponse>(OrderApiService.BASE, body, { headers });
  }

  /** `GET /api/orders/{orderNumber}` — order detail by its KR-12 public number.
   *  Returns a `CANCELLED` order too: that is a real record of something that
   *  was attempted, and hiding it would make a compensated sale
   *  indistinguishable from one that never happened. */
  getByNumber(orderNumber: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(
      `${OrderApiService.BASE}/${encodeURIComponent(orderNumber)}`,
    );
  }
}
