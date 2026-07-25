import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LookupApiService } from './lookup-api.service';
import { LookupCacheService } from './lookup-cache.service';

describe('LookupCacheService', () => {
  let cache: LookupCacheService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        LookupCacheService,
        LookupApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    cache = TestBed.inject(LookupCacheService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads cities once and exposes them as a signal', () => {
    expect(cache.cities()).toEqual([]);
    expect(cache.citiesLoaded()).toBe(false);

    cache.ensureCities();
    http.expectOne('/api/cities').flush([{ cityId: 1, name: 'İstanbul' }]);

    expect(cache.citiesLoaded()).toBe(true);
    expect(cache.cities()).toEqual([{ cityId: 1, name: 'İstanbul' }]);
  });

  it('does not re-fetch cities once loaded (load once, keep)', () => {
    cache.ensureCities();
    http.expectOne('/api/cities').flush([{ cityId: 1, name: 'İstanbul' }]);
    cache.ensureCities();
    http.expectNone('/api/cities');
  });

  it('collapses a burst of ensureCities calls into a single in-flight request', () => {
    cache.ensureCities();
    cache.ensureCities();
    const reqs = http.match('/api/cities');
    expect(reqs.length).toBe(1);
    reqs[0].flush([]);
  });

  it('a failed city load allows a later retry', () => {
    cache.ensureCities();
    http.expectOne('/api/cities').flush(null, { status: 503, statusText: 'Unavailable' });
    expect(cache.citiesLoaded()).toBe(false);

    cache.ensureCities();
    http.expectOne('/api/cities').flush([{ cityId: 1, name: 'İstanbul' }]);
    expect(cache.citiesLoaded()).toBe(true);
  });

  it('caches districts per city and distinguishes loaded-empty from not-loaded', () => {
    expect(cache.hasDistricts(1)).toBe(false);
    expect(cache.districtsOf(1)).toEqual([]);

    cache.ensureDistricts(1);
    http.expectOne('/api/cities/1/districts').flush([{ districtId: 10, name: 'Kadıköy' }]);

    expect(cache.hasDistricts(1)).toBe(true);
    expect(cache.districtsOf(1)).toEqual([{ districtId: 10, name: 'Kadıköy' }]);
    expect(cache.cachedDistrictCount()).toBe(1);

    // second request for the same city → no HTTP
    cache.ensureDistricts(1);
    http.expectNone('/api/cities/1/districts');
  });

  it('keeps districts of different cities independent', () => {
    cache.ensureDistricts(1);
    cache.ensureDistricts(2);
    http.expectOne('/api/cities/1/districts').flush([{ districtId: 10, name: 'Kadıköy' }]);
    http.expectOne('/api/cities/2/districts').flush([{ districtId: 20, name: 'Çankaya' }]);
    expect(cache.districtsOf(1)).toEqual([{ districtId: 10, name: 'Kadıköy' }]);
    expect(cache.districtsOf(2)).toEqual([{ districtId: 20, name: 'Çankaya' }]);
    expect(cache.cachedDistrictCount()).toBe(2);
  });
});
