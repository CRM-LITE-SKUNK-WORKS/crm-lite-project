import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import type { ProductDetail, ProductRow } from '../model';
import { ProductApiService } from './product-api.service';

const ACTIVE: ProductRow = {
  productId: 1,
  productName: 'ADSL 8MB',
  campaignName: 'ADSL Hosgeldin Kampanyasi',
  campaignId: 'CMP-ADSL-01',
  productStatus: 'Active',
};
const CAMPAIGNLESS: ProductRow = {
  productId: 3,
  productName: 'ADSL Activation',
  campaignName: null,
  campaignId: null,
  productStatus: 'Active',
};
const PASSIVE: ProductRow = {
  productId: 4,
  productName: 'ADSL 8MB Legacy',
  campaignName: null,
  campaignId: null,
  productStatus: 'Passive',
};

const DETAIL: ProductDetail = {
  productOfferName: 'ADSL Data Modem Offer',
  productOfferId: 2,
  productSpecId: 2,
  campaign: 'ADSL Hosgeldin Kampanyasi',
  serviceAddress: {
    addressId: 1,
    street: 'Bagdat Cad.',
    houseFlatNumber: '12/4',
    districtName: 'Kadikoy',
    cityName: 'Istanbul',
  },
};

describe('ProductApiService', () => {
  let service: ProductApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProductApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists by the PUBLIC KR-11 account number', () => {
    service.listByAccountNumber('1261000010').subscribe();
    const req = http.expectOne((r) => r.url === '/api/products');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('accountNumber')).toBe('1261000010');
    req.flush([ACTIVE]);
  });

  it('sends NO pagination parameters — FR-PROD-01 defines none', () => {
    service.listByAccountNumber('1261000010').subscribe();
    const req = http.expectOne((r) => r.url === '/api/products');
    expect(req.request.params.keys()).toEqual(['accountNumber']);
    expect(req.request.params.has('page')).toBe(false);
    expect(req.request.params.has('size')).toBe(false);
    req.flush([ACTIVE]);
  });

  it('returns a PLAIN ARRAY (no Page envelope) in server order, passives included', () => {
    let rows: readonly ProductRow[] = [];
    service.listByAccountNumber('1261000010').subscribe((r) => (rows = r));
    http
      .expectOne((r) => r.url === '/api/products')
      .flush([ACTIVE, CAMPAIGNLESS, PASSIVE]);
    // AC-PROD-01-03: Passive stays in the list; the service filters nothing.
    expect(rows.map((p) => p.productId)).toEqual([1, 3, 4]);
    expect(rows.map((p) => p.productStatus)).toEqual(['Active', 'Active', 'Passive']);
  });

  it('passes a campaign-less row through with null campaign fields (the "-" is UI)', () => {
    let rows: readonly ProductRow[] = [];
    service.listByAccountNumber('1261000010').subscribe((r) => (rows = r));
    http.expectOne((r) => r.url === '/api/products').flush([CAMPAIGNLESS]);
    expect(rows[0].campaignId).toBeNull();
    expect(rows[0].campaignName).toBeNull();
  });

  it('getById hits the detail path with the public product id', () => {
    let detail: ProductDetail | undefined;
    service.getById(2).subscribe((d) => (detail = d));
    const req = http.expectOne('/api/products/2');
    expect(req.request.method).toBe('GET');
    req.flush(DETAIL);
    // One call only: the parent-chain service address is resolved server-side.
    expect(detail?.serviceAddress?.cityName).toBe('Istanbul');
  });

  it('exposes ONLY the two read methods — no write surface exists (AC-PROD-01-04)', () => {
    const surface = Object.getOwnPropertyNames(ProductApiService.prototype).filter(
      (name) => name !== 'constructor',
    );
    expect(surface.sort()).toEqual(['getById', 'listByAccountNumber']);
  });

  it('every request uses a relative /api path (FE-ADR-004 §1)', () => {
    service.listByAccountNumber('1261000010').subscribe();
    service.getById(1).subscribe();
    for (const req of http.match(() => true)) {
      expect(req.request.url.startsWith('/api/products')).toBe(true);
      expect(req.request.url).not.toContain('://');
      req.flush(req.request.url === '/api/products' ? [ACTIVE] : DETAIL);
    }
  });
});
