import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import type { AccountResponse, CreateAccountRequest, UpdateAccountRequest } from '../model';
import { AccountApiService } from './account-api.service';

const ACCOUNT: AccountResponse = {
  accountNumber: '1261000010',
  customerNumber: 1001,
  accountName: '1261000010',
  accountTypeCode: '224',
  accountTypeName: 'Billing Account',
  billingAddressId: 1,
  accountStatus: 'Active',
};

describe('AccountApiService', () => {
  let service: AccountApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AccountApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AccountApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists a customer’s billing accounts by the public customer number', () => {
    service.listByCustomer(1001).subscribe();
    const req = http.expectOne((r) => r.url === '/api/accounts');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('customerId')).toBe('1001');
    req.flush([ACCOUNT]);
  });

  it('preserves the server order (Active-first, ascending) without re-sorting', () => {
    const passive: AccountResponse = {
      ...ACCOUNT,
      accountNumber: '1261000036',
      accountStatus: 'Passive',
    };
    let rows: readonly AccountResponse[] = [];
    service.listByCustomer(1001).subscribe((r) => (rows = r));
    http.expectOne((r) => r.url === '/api/accounts').flush([ACCOUNT, passive]);
    // The service returns the array as-is — order is the contract (AC-ACCT-01-04).
    expect(rows.map((a) => a.accountNumber)).toEqual(['1261000010', '1261000036']);
  });

  it('getByNumber hits the single-account path', () => {
    service.getByNumber('1261000010').subscribe();
    const req = http.expectOne('/api/accounts/1261000010');
    expect(req.request.method).toBe('GET');
    req.flush(ACCOUNT);
  });

  it('create POSTs {customerId, accountName, addressId} and no account type', () => {
    const body: CreateAccountRequest = {
      customerId: 1002,
      accountName: 'Zeynep Billing',
      addressId: 3,
    };
    service.create(body).subscribe();
    const req = http.expectOne('/api/accounts');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    // KR-11 / K-8: the client never sends an account type or number.
    expect(Object.keys(req.request.body)).toEqual(['customerId', 'accountName', 'addressId']);
    req.flush({ ...ACCOUNT, accountNumber: '1261000044' }, { status: 201, statusText: 'Created' });
  });

  it('update PUTs only accountName + addressId — never the immutable accountNumber (KR-11)', () => {
    const body: UpdateAccountRequest = { accountName: 'Renamed Billing', addressId: 2 };
    service.update('1261000028', body).subscribe();
    const req = http.expectOne('/api/accounts/1261000028');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    expect(Object.keys(req.request.body).sort()).toEqual(['accountName', 'addressId']);
    expect('accountNumber' in req.request.body).toBe(false);
    req.flush({ ...ACCOUNT, accountNumber: '1261000028', accountName: 'Renamed Billing' });
  });

  it('delete issues DELETE and accepts a 204 (passivation)', () => {
    let done = false;
    service.delete('1261000036').subscribe(() => (done = true));
    const req = http.expectOne('/api/accounts/1261000036');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(done).toBe(true);
  });

  it('every request uses a relative /api path (FE-ADR-004 §1)', () => {
    service.listByCustomer(1001).subscribe();
    service.getByNumber('1261000010').subscribe();
    for (const req of http.match(() => true)) {
      expect(req.request.url.startsWith('/api/accounts')).toBe(true);
      expect(req.request.url).not.toContain('://');
      req.flush(req.request.url === '/api/accounts' ? [ACCOUNT] : ACCOUNT);
    }
  });
});
