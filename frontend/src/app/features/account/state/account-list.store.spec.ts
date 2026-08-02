import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import type { ApiError } from '../../../core/http';
import { AccountApiService } from '../data/account-api.service';
import type { AccountResponse } from '../model';
import { AccountListStore } from './account-list.store';

const ACTIVE: AccountResponse = {
  accountNumber: '1261000010',
  customerNumber: 1001,
  accountName: 'Ali Billing',
  accountTypeCode: '224',
  accountTypeName: 'Billing Account',
  billingAddressId: 1,
  accountStatus: 'Active',
};
const PASSIVE: AccountResponse = {
  ...ACTIVE,
  accountNumber: '1261000036',
  accountStatus: 'Passive',
};

class FakeApi {
  calls: number[] = [];
  next = new Subject<readonly AccountResponse[]>();
  mode: 'subject' | 'sync-ok' | 'error' = 'subject';
  syncValue: readonly AccountResponse[] = [ACTIVE, PASSIVE];
  errorValue: ApiError = {
    kind: 'api-error',
    status: 409,
    messageKey: 'MSG-ACCT-HAS-PRODUCTS',
    fieldErrors: [],
    path: '/api/accounts',
    backendText: null,
  };

  listByCustomer(customerNumber: number) {
    this.calls.push(customerNumber);
    if (this.mode === 'sync-ok') return of(this.syncValue);
    if (this.mode === 'error') return throwError(() => this.errorValue);
    return this.next.asObservable();
  }
}

describe('AccountListStore', () => {
  let store: AccountListStore;
  let api: FakeApi;

  beforeEach(() => {
    api = new FakeApi();
    TestBed.configureTestingModule({
      providers: [AccountListStore, { provide: AccountApiService, useValue: api }],
    });
    store = TestBed.inject(AccountListStore);
  });

  it('starts idle and empty', () => {
    expect(store.status()).toBe('idle');
    expect(store.accounts()).toEqual([]);
    expect(store.customerNumber()).toBeNull();
  });

  it('loads a customer’s billing accounts by business number', () => {
    api.mode = 'sync-ok';
    store.load(1001);
    expect(api.calls).toEqual([1001]);
    expect(store.customerNumber()).toBe(1001);
    expect(store.status()).toBe('loaded');
    expect(store.accounts()).toEqual([ACTIVE, PASSIVE]);
  });

  it('preserves server order — Active before Passive — without re-sorting', () => {
    api.mode = 'sync-ok';
    store.load(1001);
    expect(store.accounts().map((a) => a.accountStatus)).toEqual(['Active', 'Passive']);
  });

  it('exposes a loading state until the response arrives', () => {
    store.load(1001);
    expect(store.isLoading()).toBe(true);
    api.next.next([ACTIVE]);
    expect(store.isLoading()).toBe(false);
    expect(store.status()).toBe('loaded');
  });

  it('marks isEmpty after a successful zero-row load', () => {
    api.mode = 'sync-ok';
    api.syncValue = [];
    store.load(1002);
    expect(store.isEmpty()).toBe(true);
  });

  it('keeps the normalized ApiError on failure', () => {
    api.mode = 'error';
    store.load(1001);
    expect(store.status()).toBe('error');
    expect(store.error()?.messageKey).toBe('MSG-ACCT-HAS-PRODUCTS');
  });

  it('reload re-fetches the current customer (post-mutation refresh)', () => {
    api.mode = 'sync-ok';
    store.load(1001);
    store.reload();
    expect(api.calls).toEqual([1001, 1001]);
  });

  it('reload is a no-op when nothing has been loaded', () => {
    store.reload();
    expect(api.calls).toEqual([]);
  });

  it('ignores a stale response superseded by a newer load', () => {
    const first = new Subject<readonly AccountResponse[]>();
    const second = new Subject<readonly AccountResponse[]>();
    let call = 0;
    api.listByCustomer = (() =>
      call++ === 0 ? first.asObservable() : second.asObservable()) as never;

    store.load(1001);
    store.load(1002);
    second.next([ACTIVE]);
    first.next([ACTIVE, PASSIVE]); // late, must be ignored
    expect(store.accounts()).toEqual([ACTIVE]);
    expect(store.customerNumber()).toBe(1002);
  });

  it('reset clears everything', () => {
    api.mode = 'sync-ok';
    store.load(1001);
    store.reset();
    expect(store.status()).toBe('idle');
    expect(store.accounts()).toEqual([]);
    expect(store.customerNumber()).toBeNull();
  });
});
