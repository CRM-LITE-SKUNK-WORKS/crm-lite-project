import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { type Observable } from 'rxjs';
import { type OrderResponse, type SubmitOrderRequest } from '../model';

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
   * ⚠ NOT IDEMPOTENT. A retry creates a SECOND set of products and orphans the
   * first, which is exactly why the backend disabled transport retries on its
   * own outbound clients (ADR-016 §5.3b). This method therefore never retries,
   * and the caller (`OrderSubmitStore`) guards against duplicate submits with
   * an in-flight flag.
   */
  submit(body: SubmitOrderRequest): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(OrderApiService.BASE, body);
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
