import { Injectable, computed, inject, signal } from '@angular/core';
import { type ApiError, isApiError } from '../../../core/http';
import { ProductApiService } from '../data/product-api.service';
import { type ProductRow } from '../model';

export type RequestStatus = 'idle' | 'loading' | 'loaded' | 'error';

/**
 * Screen-scoped state for one billing account's product sub-table
 * (FR-PROD-01; FE-ADR-006 §1 — provided by the section component, not root, so
 * every expanded account row owns an independent instance).
 *
 * Three contract rules live here as ABSENCES, and each one is deliberate:
 * - **No pagination state.** No `page`, no `size`, no total. FR-PROD-01 defines
 *   no pagination rule and the endpoint returns a plain array; adding
 *   client-side paging would invent a contract (scope §4.24/5 precedent).
 * - **No filtering.** Passive products stay in the list (AC-PROD-01-03). The
 *   store never drops or hides a row by status.
 * - **No re-sorting.** The server's order is kept exactly as received.
 *
 * Delegates HTTP to {@link ProductApiService}; keeps the normalized
 * {@link ApiError} for the screen to resolve via i18n, never a message
 * (FE-ADR-008 §5).
 */
@Injectable()
export class ProductListStore {
  private readonly api = inject(ProductApiService);

  private readonly _accountNumber = signal<string | null>(null);
  private readonly _products = signal<readonly ProductRow[]>([]);
  private readonly _status = signal<RequestStatus>('idle');
  private readonly _error = signal<ApiError | null>(null);

  private requestSeq = 0;

  readonly accountNumber = this._accountNumber.asReadonly();
  /** In server order, Active AND Passive — never filtered, never re-sorted. */
  readonly products = this._products.asReadonly();
  readonly status = this._status.asReadonly();
  readonly error = this._error.asReadonly();
  readonly isLoading = computed(() => this._status() === 'loading');
  /** `200 []` — the AC-PROD-01-02 `MSG-PROD-NONE` case (frontend-only text). */
  readonly isEmpty = computed(() => this._status() === 'loaded' && this._products().length === 0);

  /** Load (or reload) the products of one billing account by its KR-11 number. */
  load(accountNumber: string): void {
    this._accountNumber.set(accountNumber);
    const token = ++this.requestSeq;
    this._status.set('loading');
    this._error.set(null);
    this.api.listByAccountNumber(accountNumber).subscribe({
      next: (products) => {
        if (token !== this.requestSeq) {
          return;
        }
        this._products.set(products); // order preserved as received
        this._status.set('loaded');
      },
      error: (error: unknown) => {
        if (token !== this.requestSeq) {
          return;
        }
        this._error.set(isApiError(error) ? error : null);
        this._status.set('error');
      },
    });
  }

  reload(): void {
    const current = this._accountNumber();
    if (current !== null) {
      this.load(current);
    }
  }

  reset(): void {
    this._accountNumber.set(null);
    this._products.set([]);
    this._status.set('idle');
    this._error.set(null);
  }

  // NOTE: no create/update/delete mutations — Phase A has no product write
  // endpoint at all (AC-PROD-01-04). Adding one here would be fake behaviour.
}
