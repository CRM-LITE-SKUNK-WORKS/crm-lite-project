import { Injectable, computed, inject, signal } from '@angular/core';
import { type ApiError, isApiError } from '../../../core/http';
import { CustomerApiService } from '../data/customer-api.service';
import {
  DEFAULT_PAGE_SIZE,
  type CustomerDetailResponse,
  type CustomerSearchCriteria,
  type PageResult,
  type PageSize,
} from '../model';

/** Coarse request lifecycle — enough for a screen to switch between spinner,
 *  results, empty-state and error surfaces without a boolean soup. */
export type RequestStatus = 'idle' | 'loading' | 'loaded' | 'error';

/**
 * Screen-scoped state for Customer Search (FE-ADR-006 §1: screen state is
 * provided by the feature route, NOT `providedIn:'root'`). Signals hold the
 * filter, the page request and the last result; the screen reads the read-only
 * signals and calls the intent-named methods (FE-ADR-006 §2), so every state
 * transition has exactly one findable call site.
 *
 * No HTTP and no error text here: it delegates to {@link CustomerApiService} and
 * keeps the normalized {@link ApiError} (whose `messageKey` the screen resolves
 * through i18n) — it never builds a message (FE-ADR-008 §5).
 *
 * Because list rows ARE the full detail contract since ADR-005, selecting a row
 * yields the whole record with no extra call — {@link selected} is that
 * "selected record" state.
 */
@Injectable()
export class CustomerListStore {
  private readonly api = inject(CustomerApiService);

  private readonly _criteria = signal<CustomerSearchCriteria>({});
  private readonly _page = signal(0);
  private readonly _size = signal<PageSize>(DEFAULT_PAGE_SIZE);
  private readonly _result = signal<PageResult<CustomerDetailResponse> | null>(null);
  private readonly _status = signal<RequestStatus>('idle');
  private readonly _error = signal<ApiError | null>(null);
  private readonly _selected = signal<CustomerDetailResponse | null>(null);

  /** A monotonic token so a slow earlier response cannot overwrite a newer one. */
  private requestSeq = 0;

  // --- read-only state ------------------------------------------------------
  readonly criteria = this._criteria.asReadonly();
  readonly page = this._page.asReadonly();
  readonly size = this._size.asReadonly();
  readonly status = this._status.asReadonly();
  readonly error = this._error.asReadonly();
  readonly selected = this._selected.asReadonly();

  readonly customers = computed<readonly CustomerDetailResponse[]>(
    () => this._result()?.items ?? [],
  );
  readonly totalElements = computed(() => this._result()?.totalElements ?? 0);
  readonly totalPages = computed(() => this._result()?.totalPages ?? 0);
  readonly isFirst = computed(() => this._result()?.isFirst ?? true);
  readonly isLast = computed(() => this._result()?.isLast ?? true);
  readonly isLoading = computed(() => this._status() === 'loading');
  /** Loaded successfully but zero rows — the empty-state trigger. */
  readonly isEmpty = computed(() => this._status() === 'loaded' && this.customers().length === 0);

  // --- intents --------------------------------------------------------------

  /** Browse mode (AC-CUST-01-00): no criteria, page 0. Runs on screen open. */
  browse(): void {
    this._criteria.set({});
    this._page.set(0);
    this.load();
  }

  /** Apply a new filter set; always resets to the first page. */
  applyFilters(criteria: CustomerSearchCriteria): void {
    this._criteria.set(criteria);
    this._page.set(0);
    this.load();
  }

  /** Navigate to a zero-based page, keeping the current criteria and size. */
  goToPage(page: number): void {
    if (page < 0 || page === this._page()) {
      return;
    }
    this._page.set(page);
    this.load();
  }

  /** Change page size (KR-04: always explicit); resets to the first page. */
  setPageSize(size: PageSize): void {
    if (size === this._size()) {
      return;
    }
    this._size.set(size);
    this._page.set(0);
    this.load();
  }

  /** Select a row (the full record, no extra call) / clear the selection. */
  select(customer: CustomerDetailResponse): void {
    this._selected.set(customer);
  }
  clearSelection(): void {
    this._selected.set(null);
  }

  /** Re-issue the current request unchanged — the "Try again" action after a
   *  failure (customer-search-analysis §6 state 12). */
  reload(): void {
    this.load();
  }

  /** Clear all state back to idle (e.g. on leaving the screen). */
  reset(): void {
    this._criteria.set({});
    this._page.set(0);
    this._size.set(DEFAULT_PAGE_SIZE);
    this._result.set(null);
    this._status.set('idle');
    this._error.set(null);
    this._selected.set(null);
  }

  private load(): void {
    const token = ++this.requestSeq;
    this._status.set('loading');
    this._error.set(null);
    this.api.list(this._criteria(), { page: this._page(), size: this._size() }).subscribe({
      next: (result) => {
        if (token !== this.requestSeq) {
          return; // a newer request superseded this one
        }
        this._result.set(result);
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
}
