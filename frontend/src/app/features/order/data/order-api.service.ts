import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { type Observable } from 'rxjs';
import {
  type CreateDraftRequest,
  type OrderResponse,
  type OrderStatusResponse,
  type SubmitDraftRequest,
} from '../model';

/** Wire header name for the idempotency contract (ADR-016 idempotency addendum,
 *  2026-08-06) — kept here, not in the model, since it is transport, not payload. */
export const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key';

/**
 * order-service HTTP client (docs/api/order-service.md). The ONLY place
 * `HttpClient` is called for the order aggregate (FE-ADR-006 §3).
 *
 * The LIVE SALE flow is the asynchronous draft/submit/poll contract
 * (ADR-018): `createDraft` → `submitDraft` → `status`, with `abandonDraft`
 * for a pre-submit Cancel. The deprecated `POST /api/orders` is no longer
 * called from here — this is the frontend PR ADR-018 §10 names as the one
 * that moves the browser over.
 *
 * What stays absent, deliberately:
 *  - **No order list.** No FR asks for one; inventing a list contract would
 *    mean inventing sorting/filtering/pagination rules no analyst has written.
 *    So there is no "my orders" screen and no method for it (scope §2B.5).
 *  - **No user-facing cancel of a submitted order.** KR-7 leaves cancellation
 *    out of phase; `CANCELLED` is reached only by the orchestration's own
 *    compensation or by abandoning a still-`WAIT` draft (scope §2B.6).
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
   * `POST /api/orders/drafts` — Start New Sale; 201 with the WAIT order and
   * its KR-12 number (ADR-018 §6). `idempotencyKey` identifies ONE draft-
   * creation attempt — the SAME UUID for every retry of it, a FRESH one only
   * for a genuinely new attempt (`OrderSubmitStore` owns that decision). A
   * replayed key returns the original draft, never a second one.
   */
  createDraft(body: CreateDraftRequest, idempotencyKey: string): Observable<OrderStatusResponse> {
    const headers = new HttpHeaders().set(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
    return this.http.post<OrderStatusResponse>(`${OrderApiService.BASE}/drafts`, body, {
      headers,
    });
  }

  /** `DELETE /api/orders/{orderNumber}/draft` — Cancel before submit; 204,
   *  idempotent, `WAIT`-only (ADR-018 §6). No `Idempotency-Key`: DELETE of a
   *  named resource is already idempotent by identity. */
  abandonDraft(orderNumber: string): Observable<void> {
    return this.http.delete<void>(
      `${OrderApiService.BASE}/${encodeURIComponent(orderNumber)}/draft`,
    );
  }

  /**
   * `POST /api/orders/{orderNumber}/submit` — Confirm Submit; 202 once the
   * saga has started (AC-SALE-01-15, ADR-018 §6). `idempotencyKey` is a
   * SEPARATE logical command from the draft's own key — reused across
   * retries of this SAME submit attempt, fresh only for a new one.
   */
  submitDraft(
    orderNumber: string,
    body: SubmitDraftRequest,
    idempotencyKey: string,
  ): Observable<OrderStatusResponse> {
    const headers = new HttpHeaders().set(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
    return this.http.post<OrderStatusResponse>(
      `${OrderApiService.BASE}/${encodeURIComponent(orderNumber)}/submit`,
      body,
      { headers },
    );
  }

  /** `GET /api/orders/{orderNumber}/status` — the resource a poll loop reads
   *  after a 202: business status + asynchronous processing status
   *  (ADR-018 §6). Cheap by design; safe to call on an interval. */
  status(orderNumber: string): Observable<OrderStatusResponse> {
    return this.http.get<OrderStatusResponse>(
      `${OrderApiService.BASE}/${encodeURIComponent(orderNumber)}/status`,
    );
  }

  /** `GET /api/orders/{orderNumber}` — order detail by its KR-12 public number.
   *  Returns a `CANCELLED` order too: that is a real record of something that
   *  was attempted, and hiding it would make a compensated sale
   *  indistinguishable from one that never happened. Not part of the SALE
   *  wizard flow (which reads {@link status} instead); kept for detail lookups. */
  getByNumber(orderNumber: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(
      `${OrderApiService.BASE}/${encodeURIComponent(orderNumber)}`,
    );
  }
}
