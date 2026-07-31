import { Injectable, computed, inject, signal } from '@angular/core';
import { type ApiError, isApiError } from '../../../core/http';
import { ProductApiService } from '../data/product-api.service';
import { type ProductDetail } from '../model';
import { type RequestStatus } from './product-list.store';

/**
 * Screen-scoped state for the FR-PROD-02 detail modal (AC-PROD-02-01).
 *
 * Separate from {@link ProductListStore} on purpose: the modal is an
 * independent read with its own lifecycle (opened per row, reset on close), and
 * a failing detail read must never disturb the list that is still on screen.
 *
 * `404 MSG-PROD-NOT-FOUND` is surfaced as {@link notFound} so the modal can show
 * the documented "product not found" state instead of a generic error banner —
 * the distinction comes from the backend's `messageKey`, never from a guess
 * (FE-ADR-008 §2).
 */
@Injectable()
export class ProductDetailStore {
  private readonly api = inject(ProductApiService);

  private readonly _productId = signal<number | null>(null);
  private readonly _detail = signal<ProductDetail | null>(null);
  private readonly _status = signal<RequestStatus>('idle');
  private readonly _error = signal<ApiError | null>(null);

  private requestSeq = 0;

  readonly productId = this._productId.asReadonly();
  readonly detail = this._detail.asReadonly();
  readonly status = this._status.asReadonly();
  readonly error = this._error.asReadonly();
  readonly isLoading = computed(() => this._status() === 'loading');
  /** 404 `MSG-PROD-NOT-FOUND` — the documented unknown-product outcome. */
  readonly notFound = computed(() => this._error()?.status === 404);

  load(productId: number): void {
    this._productId.set(productId);
    const token = ++this.requestSeq;
    this._status.set('loading');
    this._error.set(null);
    this._detail.set(null);
    this.api.getById(productId).subscribe({
      next: (detail) => {
        if (token !== this.requestSeq) {
          return;
        }
        this._detail.set(detail);
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
    const current = this._productId();
    if (current !== null) {
      this.load(current);
    }
  }

  reset(): void {
    // Bump the sequence so an in-flight read cannot land after the modal closed.
    this.requestSeq++;
    this._productId.set(null);
    this._detail.set(null);
    this._status.set('idle');
    this._error.set(null);
  }
}
