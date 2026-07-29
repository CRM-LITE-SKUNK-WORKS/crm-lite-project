import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import type {
  AddressRequest,
  CreateCustomerRequest,
  CustomerDetailResponse,
  SpringPage,
} from '../model';
import { CustomerApiService } from './customer-api.service';

const CUSTOMER: CustomerDetailResponse = {
  customerNumber: 1001,
  firstName: 'Ali',
  middleName: null,
  lastName: 'Yildiz',
  fatherName: 'Hasan',
  motherName: 'Ayse',
  birthDate: '1990-05-14',
  gender: 'Male',
  nationalityId: '12345678901',
  role: 'Customer',
  status: 'ACTV',
};

function pageOf(rows: CustomerDetailResponse[]): SpringPage<CustomerDetailResponse> {
  return { content: rows, totalElements: rows.length, totalPages: 1, number: 0, size: 20 };
}

describe('CustomerApiService', () => {
  let service: CustomerApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CustomerApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CustomerApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('browse mode: no criteria still sends page + size explicitly (KR-04)', () => {
    service.list({}, { page: 0, size: 20 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/customers');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
    req.flush(pageOf([CUSTOMER]));
  });

  it('normalizes the FULL verified wire envelope (flat PageImpl, Swagger 2026-07-25), ignoring the noise', () => {
    let result: unknown;
    service.list({}, { page: 1, size: 20 }).subscribe((r) => (result = r));
    // Verbatim shape from the customer-controller Swagger schema: metadata at
    // the top level + pageable/sort/numberOfElements/empty extras we ignore.
    http
      .expectOne((r) => r.url === '/api/customers')
      .flush({
        totalElements: 42,
        totalPages: 3,
        size: 20,
        content: [CUSTOMER],
        number: 2,
        pageable: { offset: 40, paged: true, pageNumber: 2, pageSize: 20 },
        sort: { empty: true, sorted: false, unsorted: true },
        numberOfElements: 1,
        first: false,
        last: true, // server flag wins over the computed value
        empty: false,
      });
    expect(result).toEqual({
      items: [CUSTOMER],
      page: 2,
      size: 20,
      totalElements: 42,
      totalPages: 3,
      isFirst: false,
      isLast: true,
    });
  });

  it('normalizes the Page envelope into a PageResult in one place', () => {
    let result: unknown;
    service.list({}, { page: 0, size: 20 }).subscribe((r) => (result = r));
    http
      .expectOne((r) => r.url === '/api/customers')
      .flush({ content: [CUSTOMER], totalElements: 42, totalPages: 3, number: 1, size: 20 });
    expect(result).toEqual({
      items: [CUSTOMER],
      page: 1,
      size: 20,
      totalElements: 42,
      totalPages: 3,
      isFirst: false,
      isLast: false,
    });
  });

  it('appends only non-empty, trimmed criteria', () => {
    service
      .list(
        { firstName: '  Ali  ', lastName: '', nationalityId: '12345678901' },
        { page: 2, size: 50 },
      )
      .subscribe();
    const req = http.expectOne((r) => r.url === '/api/customers');
    expect(req.request.params.get('firstName')).toBe('Ali');
    expect(req.request.params.get('nationalityId')).toBe('12345678901');
    expect(req.request.params.has('lastName')).toBe(false);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(pageOf([]));
  });

  it('maps customerId criterion to the customerId query param (name kept, scope §2.11)', () => {
    service.list({ customerId: '1001' }, { page: 0, size: 20 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/customers');
    expect(req.request.params.get('customerId')).toBe('1001');
    req.flush(pageOf([CUSTOMER]));
  });

  it('getByNumber hits the detail path', () => {
    service.getByNumber(1001).subscribe();
    const req = http.expectOne('/api/customers/1001');
    expect(req.request.method).toBe('GET');
    req.flush(CUSTOMER);
  });

  it('nationalityIdIsTaken asks the availability endpoint and inverts its answer', () => {
    // ADR-005 §Addendum. NOT the list endpoint: only this one reports the
    // ADR-003 rule with soft-deleted holders included.
    const taken: boolean[] = [];
    service.nationalityIdIsTaken('34567890123').subscribe((value) => taken.push(value));
    const req = http.expectOne(
      (r) => r.url === '/api/customers/nationality-id-availability',
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('nationalityId')).toBe('34567890123');
    req.flush({ available: false });
    expect(taken).toEqual([true]);

    service.nationalityIdIsTaken('99988877766').subscribe((value) => taken.push(value));
    http
      .expectOne((r) => r.url === '/api/customers/nationality-id-availability')
      .flush({ available: true });
    expect(taken).toEqual([true, false]);
  });

  it('create POSTs the atomic body and returns the detail payload', () => {
    const body: CreateCustomerRequest = {
      demographic: {
        firstName: 'Velihan',
        lastName: 'Gözek',
        birthDate: '1992-03-15',
        gender: 'Male',
        nationalityId: '10000000004',
      },
      addresses: [
        {
          cityId: 1,
          districtId: 1,
          street: 'Example Street',
          houseFlatNumber: '10/2',
          addressDescription: 'Home',
          primary: true,
        },
      ],
      contactMedium: { email: 'velihan@example.com', mobilePhone: '05321112233' },
    };
    let created: CustomerDetailResponse | undefined;
    service.create(body).subscribe((c) => (created = c));
    const req = http.expectOne('/api/customers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ ...CUSTOMER, customerNumber: 1004 });
    expect(created?.customerNumber).toBe(1004);
  });

  it('update PUTs the demographic body', () => {
    service
      .update(1001, {
        firstName: 'Ali',
        middleName: 'Kemal',
        lastName: 'Yildiz',
        birthDate: '1990-05-14',
        gender: 'Male',
        nationalityId: '12345678901',
      })
      .subscribe();
    const req = http.expectOne('/api/customers/1001');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.middleName).toBe('Kemal');
    req.flush(CUSTOMER);
  });

  it('delete issues DELETE and accepts a 204', () => {
    let done = false;
    service.delete(1001).subscribe(() => (done = true));
    const req = http.expectOne('/api/customers/1001');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(done).toBe(true);
  });

  it('address endpoints target the nested paths', () => {
    const addr: AddressRequest = {
      cityId: 2,
      districtId: 3,
      street: 'Yeni Cad.',
      houseFlatNumber: '7',
      addressDescription: 'Is',
    };
    // Response shape verified via Swagger (2026-07-25): ids + resolved names.
    const addrResponse = {
      addressId: 4,
      cityId: 2,
      cityName: 'Ankara',
      districtId: 3,
      districtName: 'Çankaya',
      street: 'Yeni Cad.',
      houseFlatNumber: '7',
      addressDescription: 'Is',
      primary: false,
    };

    service.getAddresses(1001).subscribe();
    http.expectOne('/api/customers/1001/addresses').flush([]);

    service.addAddress(1001, addr).subscribe();
    const post = http.expectOne('/api/customers/1001/addresses');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual(addr);
    // The client sends exactly the documented wire fields — never the
    // Swagger-artifact `primaryRequested`.
    expect('primaryRequested' in post.request.body).toBe(false);
    post.flush(addrResponse);

    service.updateAddress(1001, 4, addr).subscribe();
    const put = http.expectOne('/api/customers/1001/addresses/4');
    expect(put.request.method).toBe('PUT');
    put.flush(addrResponse);

    let promoted: unknown;
    service.setPrimaryAddress(1001, 4).subscribe((a) => (promoted = a));
    const patch = http.expectOne('/api/customers/1001/addresses/4/primary');
    expect(patch.request.method).toBe('PATCH');
    patch.flush({ ...addrResponse, primary: true });
    expect((promoted as { primary: boolean }).primary).toBe(true);

    service.deleteAddress(1001, 4).subscribe();
    const del = http.expectOne('/api/customers/1001/addresses/4');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('contact-medium endpoints target the nested path', () => {
    service.getContact(1001).subscribe();
    http.expectOne('/api/customers/1001/contact-medium').flush({
      email: 'a@b.com',
      mobilePhone: '05321112233',
      homePhone: null,
      fax: null,
    });

    service.updateContact(1001, { email: 'a@b.com', mobilePhone: '05321112233' }).subscribe();
    const put = http.expectOne('/api/customers/1001/contact-medium');
    expect(put.request.method).toBe('PUT');
    put.flush({ email: 'a@b.com', mobilePhone: '05321112233', homePhone: null, fax: null });
  });

  it('every request uses a relative /api path (FE-ADR-004 §1)', () => {
    service.list({}, { page: 0, size: 20 }).subscribe();
    service.getByNumber(1001).subscribe();
    for (const req of http.match(() => true)) {
      expect(req.request.url.startsWith('/api/')).toBe(true);
      expect(req.request.url).not.toContain('://');
      req.flush(
        req.request.method === 'GET' && req.request.url === '/api/customers'
          ? pageOf([])
          : CUSTOMER,
      );
    }
  });
});
