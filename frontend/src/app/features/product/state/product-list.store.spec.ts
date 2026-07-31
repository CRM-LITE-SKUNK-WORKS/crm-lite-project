import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideCoreHttp } from '../../../core/http';
import { type ProductRow } from '../model';
import { ProductListStore } from './product-list.store';

const ACTIVE: ProductRow = {
  productId: 1,
  productName: 'ADSL 8MB',
  campaignName: 'ADSL Hosgeldin Kampanyasi',
  campaignId: 'CMP-ADSL-01',
  productStatus: 'Active',
};
const PASSIVE: ProductRow = {
  productId: 4,
  productName: 'ADSL 8MB Legacy',
  campaignName: null,
  campaignId: null,
  productStatus: 'Passive',
};

describe('ProductListStore', () => {
  let store: ProductListStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ProductListStore,
        provideHttpClient(),
        provideCoreHttp(),
        provideHttpClientTesting(),
      ],
    });
    store = TestBed.inject(ProductListStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts idle and empty', () => {
    expect(store.status()).toBe('idle');
    expect(store.products()).toEqual([]);
    expect(store.isEmpty()).toBe(false); // idle is not "loaded and empty"
  });

  it('keeps the server list verbatim — Passive included, order untouched', () => {
    store.load('1261000010');
    expect(store.isLoading()).toBe(true);
    http.expectOne((r) => r.url === '/api/products').flush([PASSIVE, ACTIVE]);

    expect(store.status()).toBe('loaded');
    // Deliberately NOT re-sorted into Active-first: the server's order is the
    // contract, and no status filtering happens here (AC-PROD-01-03).
    expect(store.products().map((p) => p.productId)).toEqual([4, 1]);
    expect(store.products().map((p) => p.productStatus)).toEqual(['Passive', 'Active']);
  });

  it('flags the 200 [] case as empty (AC-PROD-01-02)', () => {
    store.load('1261000028');
    http.expectOne((r) => r.url === '/api/products').flush([]);
    expect(store.isEmpty()).toBe(true);
    expect(store.error()).toBeNull();
  });

  it('keeps the normalized ApiError on failure and clears it on a successful reload', () => {
    store.load('1261000002');
    http.expectOne((r) => r.url === '/api/products').flush(
      { messageKey: 'MSG-ACCT-NOT-FOUND', message: 'raw', status: 404 },
      { status: 404, statusText: 'Not Found' },
    );
    expect(store.status()).toBe('error');
    expect(store.error()?.messageKey).toBe('MSG-ACCT-NOT-FOUND');

    store.reload();
    http.expectOne((r) => r.url === '/api/products').flush([ACTIVE]);
    expect(store.status()).toBe('loaded');
    expect(store.error()).toBeNull();
  });

  it('ignores a stale response when a newer load has been issued', () => {
    store.load('1261000010');
    const first = http.expectOne((r) => r.url === '/api/products');
    store.load('1261000028');
    const second = http.expectOne((r) => r.url === '/api/products');

    second.flush([ACTIVE]);
    first.flush([PASSIVE]); // late arrival from the abandoned account
    expect(store.products().map((p) => p.productId)).toEqual([1]);
    expect(store.accountNumber()).toBe('1261000028');
  });

  it('exposes NO pagination state and NO mutations (read-only Phase A)', () => {
    const surface = Object.keys(store);
    for (const forbidden of ['page', 'size', 'totalElements', 'create', 'update', 'delete']) {
      expect(surface).not.toContain(forbidden);
      expect(forbidden in store).toBe(false);
    }
  });
});
