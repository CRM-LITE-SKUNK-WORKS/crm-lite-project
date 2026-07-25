import { Injectable, computed, inject, signal } from '@angular/core';
import { type Observable, tap } from 'rxjs';
import { type ApiError, isApiError } from '../../../core/http';
import { AccountApiService } from '../data/account-api.service';
import {
  type AccountResponse,
  type CreateAccountRequest,
  type UpdateAccountRequest,
} from '../model';

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

  // --- mutations (added with the account section, F6) -----------------------
  // Same contract as CustomerDetailStore: the observable goes back to the
  // calling dialog/screen (which owns busy state, field errors, closing), the
  // store `tap`s the success to refresh its list. A reload — not a local patch
  // — is correct for all three: create can K-8-side-effect on the server,
  // delete leaves the row as Passive, and ORDER is the server's contract.

  /** `POST /api/accounts` — the store supplies the loaded customer's number
   *  (`customerId` is the public business number). */
  create(body: Omit<CreateAccountRequest, 'customerId'>): Observable<AccountResponse> {
    const request: CreateAccountRequest = { customerId: this.requireNumber(), ...body };
    return this.api.create(request).pipe(tap(() => this.reload()));
  }

  /** `PUT /api/accounts/{accountNumber}` — `{accountName, addressId}` ONLY
   *  (KR-11: the number travels in the path, never the body). A Passive
   *  account answers 409 `MSG-ACCT-NOT-ACTIVE` — surfaced, never pre-checked. */
  update(accountNumber: string, body: UpdateAccountRequest): Observable<AccountResponse> {
    return this.api.update(accountNumber, body).pipe(tap(() => this.reload()));
  }

  /** `DELETE /api/accounts/{accountNumber}` — passivation; the reload keeps the
   *  row visible as Passive (AC-ACCT-04-02). Guards (409) go to the caller. */
  delete(accountNumber: string): Observable<void> {
    return this.api.delete(accountNumber).pipe(tap(() => this.reload()));
  }

  private requireNumber(): number {
    const customerNumber = this._customerNumber();
    if (customerNumber === null) {
      throw new Error('AccountListStore: no customer loaded');
    }
    return customerNumber;
  }
}
