import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LookupApiService } from './lookup-api.service';

describe('LookupApiService', () => {
  let service: LookupApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LookupApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LookupApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getStatuses without domain hits the bare path', () => {
    service.getStatuses().subscribe();
    const req = http.expectOne('/api/lookups/statuses');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([{ id: 1, shortCode: 'ACTV', name: 'Active', statusDomain: 'GENERAL' }]);
  });

  it('getStatuses forwards the domain filter', () => {
    service.getStatuses('GENERAL').subscribe();
    const req = http.expectOne((r) => r.url === '/api/lookups/statuses');
    expect(req.request.params.get('domain')).toBe('GENERAL');
    req.flush([]);
  });

  it('getStatus encodes the short code into the path', () => {
    service.getStatus('ACTV').subscribe();
    const req = http.expectOne('/api/lookups/statuses/ACTV');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 1, shortCode: 'ACTV', name: 'Active', statusDomain: 'GENERAL' });
  });

  it('getTypes forwards the GENDER domain', () => {
    service.getTypes('GENDER').subscribe();
    const req = http.expectOne((r) => r.url === '/api/lookups/types');
    expect(req.request.params.get('domain')).toBe('GENDER');
    req.flush([{ id: 1, shortCode: 'MALE', name: 'Male', typeDomain: 'GENDER' }]);
  });

  it('getType hits the type-by-code path', () => {
    service.getType('INDV').subscribe();
    http.expectOne('/api/lookups/types/INDV').flush({
      id: 1,
      shortCode: 'INDV',
      name: 'Individual',
      typeDomain: 'PARTY_TYPE',
    });
  });

  it('getCities and getDistricts hit the cascade paths', () => {
    service.getCities().subscribe();
    http.expectOne('/api/cities').flush([{ cityId: 1, name: 'İstanbul' }]);

    service.getDistricts(1).subscribe();
    const req = http.expectOne('/api/cities/1/districts');
    expect(req.request.method).toBe('GET');
    req.flush([{ districtId: 1, name: 'Kadıköy' }]);
  });

  it('every request uses a relative /api path (FE-ADR-004 §1)', () => {
    service.getStatuses().subscribe();
    service.getCities().subscribe();
    service.getDistricts(2).subscribe();
    for (const req of http.match(() => true)) {
      expect(req.request.url.startsWith('/api/')).toBe(true);
      expect(req.request.url).not.toContain('://');
      req.flush([]);
    }
  });
});
