import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import type { ApiError } from '../../../core/http';
import { CustomerApiService } from '../data/customer-api.service';
import type { CustomerDetailResponse, PageResult } from '../model';
import { CustomerListStore } from './customer-list.store';

const ROW: CustomerDetailResponse = {
  customerNumber: 1001,
  firstName: 'Ali',
  middleName: null,
  lastName: 'Yildiz',
  fatherName: null,
  motherName: null,
  birthDate: '1990-05-14',
  gender: 'Male',
  nationalityId: '12345678901',
  role: 'Customer',
  status: 'ACTV',
};

function page(
  rows: CustomerDetailResponse[],
  over: Partial<PageResult<CustomerDetailResponse>> = {},
) {
  return {
    items: rows,
    page: 0,
    size: 20,
    totalElements: rows.length,
    totalPages: rows.length ? 1 : 0,
    isFirst: true,
    isLast: true,
    ...over,
  } satisfies PageResult<CustomerDetailResponse>;
}

/** Controllable fake for the API boundary (mocks the HTTP call, item 12). */
class FakeApi {
  calls: { criteria: unknown; page: number; size: number }[] = [];
  next = new Subject<PageResult<CustomerDetailResponse>>();
  mode: 'subject' | 'sync-ok' | 'error' = 'subject';
  syncValue = page([ROW]);
  errorValue: ApiError = {
    kind: 'api-error',
    status: 503,
    messageKey: 'MSG-SERVICE-UNAVAILABLE',
    fieldErrors: [],
    path: '/api/customers',
    backendText: null,
  };

  list(criteria: unknown, req: { page: number; size: number }) {
    this.calls.push({ criteria, page: req.page, size: req.size });
    if (this.mode === 'sync-ok') return of(this.syncValue);
    if (this.mode === 'error') return throwError(() => this.errorValue);
    return this.next.asObservable();
  }
}

describe('CustomerListStore', () => {
  let store: CustomerListStore;
  let api: FakeApi;

  beforeEach(() => {
    api = new FakeApi();
    TestBed.configureTestingModule({
      providers: [CustomerListStore, { provide: CustomerApiService, useValue: api }],
    });
    store = TestBed.inject(CustomerListStore);
  });

  it('starts idle and empty', () => {
    expect(store.status()).toBe('idle');
    expect(store.customers()).toEqual([]);
  });

  it('browse loads with no criteria, page 0, default size 20 (KR-04)', () => {
    api.mode = 'sync-ok';
    store.browse();
    expect(api.calls.at(-1)).toEqual({ criteria: {}, page: 0, size: 20 });
    expect(store.status()).toBe('loaded');
    expect(store.customers()).toEqual([ROW]);
  });

  it('exposes a loading state until the response arrives', () => {
    store.browse(); // subject mode: no emission yet
    expect(store.isLoading()).toBe(true);
    api.next.next(page([ROW]));
    expect(store.isLoading()).toBe(false);
    expect(store.status()).toBe('loaded');
  });

  it('applyFilters sends the criteria and resets to the first page', () => {
    api.mode = 'sync-ok';
    store.applyFilters({ firstName: 'Ali' });
    expect(api.calls.at(-1)).toEqual({ criteria: { firstName: 'Ali' }, page: 0, size: 20 });
  });

  it('goToPage changes the page but keeps criteria and size', () => {
    api.mode = 'sync-ok';
    store.applyFilters({ lastName: 'Demir' });
    store.goToPage(2);
    expect(api.calls.at(-1)).toEqual({ criteria: { lastName: 'Demir' }, page: 2, size: 20 });
  });

  it('goToPage ignores the current page and negatives', () => {
    api.mode = 'sync-ok';
    store.browse();
    const before = api.calls.length;
    store.goToPage(0); // already on 0
    store.goToPage(-1);
    expect(api.calls.length).toBe(before);
  });

  it('setPageSize resets to page 0 and always passes size explicitly', () => {
    api.mode = 'sync-ok';
    store.browse();
    store.goToPage(3);
    store.setPageSize(50);
    expect(api.calls.at(-1)).toEqual({ criteria: {}, page: 0, size: 50 });
    expect(store.size()).toBe(50);
  });

  it('marks isEmpty only after a successful zero-row load', () => {
    api.mode = 'sync-ok';
    api.syncValue = page([]);
    store.browse();
    expect(store.status()).toBe('loaded');
    expect(store.isEmpty()).toBe(true);
  });

  it('keeps the normalized ApiError and flips to error status on failure', () => {
    api.mode = 'error';
    store.browse();
    expect(store.status()).toBe('error');
    expect(store.error()?.messageKey).toBe('MSG-SERVICE-UNAVAILABLE');
    expect(store.customers()).toEqual([]);
  });

  it('selects a full row without another call, and clears it', () => {
    store.select(ROW);
    expect(store.selected()).toEqual(ROW);
    store.clearSelection();
    expect(store.selected()).toBeNull();
  });

  it('ignores a stale response that resolves after a newer request', () => {
    const first = new Subject<PageResult<CustomerDetailResponse>>();
    const second = new Subject<PageResult<CustomerDetailResponse>>();
    let call = 0;
    api.list = (() => (call++ === 0 ? first.asObservable() : second.asObservable())) as never;

    store.browse(); // request #1
    store.goToPage(1); // request #2 supersedes #1

    second.next(page([ROW], { page: 1 }));
    first.next(page([], { totalElements: 999 })); // late, must be ignored
    expect(store.totalElements()).toBe(1);
    expect(store.page()).toBe(1);
  });

  it('reset returns to idle defaults', () => {
    api.mode = 'sync-ok';
    store.applyFilters({ firstName: 'Ali' });
    store.select(ROW);
    store.reset();
    expect(store.status()).toBe('idle');
    expect(store.criteria()).toEqual({});
    expect(store.size()).toBe(20);
    expect(store.selected()).toBeNull();
    expect(store.customers()).toEqual([]);
  });
});
