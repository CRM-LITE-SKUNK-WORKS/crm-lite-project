import { Injectable, computed, inject, signal } from '@angular/core';
import { type ApiError, isApiError } from '../../../core/http';
import { AccountApiService } from '../data/account-api.service';
import { type AccountResponse } from '../model';

export type RequestStatus = 'idle' | 'loading' | 'loaded' | 'error';

/**
 * Screen-scoped state for the Customer Info account section (FE-ADR-006 §1 —
 * provided by the feature route, not root). Holds one customer's Billing
 * Accounts.
 *
 * K-8: only 224 Billing Accounts ever arrive (the 223 is invisible, ADR-013 §4).
 * The list ORDER is the server's contract (Active-first, then accountNumber
 * ascending — AC-ACCT-01-04); this store keeps it as received and NEVER re-sorts.
 *
 * Delegates HTTP to {@link AccountApiService}; keeps the normalized
 * {@link ApiError} for the screen to resolve via i18n, never a message
 * (FE-ADR-008 §5).
 */
@Injectable()
export class AccountListStore {
  private readonly api = inject(AccountApiService);

  private readonly _customerNumber = signal<number | null>(null);
  private readonly _accounts = signal<readonly AccountResponse[]>([]);
  private readonly _status = signal<RequestStatus>('idle');
  private readonly _error = signal<ApiError | null>(null);

  private requestSeq = 0;

  readonly customerNumber = this._customerNumber.asReadonly();
  /** In server order — Active-first, then ascending (never re-sorted). */
  readonly accounts = this._accounts.asReadonly();
  readonly status = this._status.asReadonly();
  readonly error = this._error.asReadonly();
  readonly isLoading = computed(() => this._status() === 'loading');
  readonly isEmpty = computed(() => this._status() === 'loaded' && this._accounts().length === 0);

  /** Load (or reload) the Billing Accounts of one customer by business number. */
  load(customerNumber: number): void {
    this._customerNumber.set(customerNumber);
    const token = ++this.requestSeq;
    this._status.set('loading');
    this._error.set(null);
    this.api.listByCustomer(customerNumber).subscribe({
      next: (accounts) => {
        if (token !== this.requestSeq) {
          return;
        }
        this._accounts.set(accounts); // order preserved as received
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

  /** Re-fetch after a mutation (create/update/delete performed via the API
   *  service by the screen). A passivated account stays in the list as Passive
   *  (FR-ACCT-04), so a reload is the correct post-delete refresh. */
  reload(): void {
    const current = this._customerNumber();
    if (current !== null) {
      this.load(current);
    }
  }

  reset(): void {
    this._customerNumber.set(null);
    this._accounts.set([]);
    this._status.set('idle');
    this._error.set(null);
  }
}
