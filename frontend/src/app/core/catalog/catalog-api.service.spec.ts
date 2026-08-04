import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogApiService } from './catalog-api.service';
import type { CampaignResponse, CharacteristicResponse, OfferResponse } from './catalog.model';

const OFFER: OfferResponse = {
  offerId: 1,
  offerName: 'ADSL 8MB Offer',
  serviceType: 'INTERNET',
  price: 299.0,
};

describe('CatalogApiService', () => {
  let service: CatalogApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CatalogApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CatalogApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listOffers hits /api/offers with NO pagination params (scope §2B.4)', () => {
    let result: readonly OfferResponse[] | undefined;
    service.listOffers().subscribe((r) => (result = r));

    const req = http.expectOne((r) => r.url === '/api/offers');
    expect(req.request.method).toBe('GET');
    // product-service has no Pageable at all — sending page/size would invent a
    // contract, and the KR-04 whitelist has no consumer here.
    expect(req.request.params.keys()).toEqual([]);
    req.flush([OFFER]);

    expect(result).toEqual([OFFER]);
  });

  it('listCampaigns returns the campaign envelope with member offers and derived total', () => {
    let result: readonly CampaignResponse[] | undefined;
    service.listCampaigns().subscribe((r) => (result = r));

    const req = http.expectOne((r) => r.url === '/api/campaigns');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([
      {
        campaignId: 'CMP-ADSL-01',
        campaignName: 'ADSL Hosgeldin Kampanyasi',
        description: 'ADSL 8MB + modem birlikte',
        activationEndDate: '2026-12-31T23:59:59Z',
        offers: [{ ...OFFER, main: true }],
        totalPrice: 497.0,
      },
    ]);

    expect(result?.[0].campaignId).toBe('CMP-ADSL-01');
    expect(result?.[0].offers[0].main).toBe(true);
    // Derived server-side; the client displays it and never recomputes.
    expect(result?.[0].totalPrice).toBe(497.0);
  });

  it('getOfferCharacteristics targets the nested path', () => {
    let result: readonly CharacteristicResponse[] | undefined;
    service.getOfferCharacteristics(1).subscribe((r) => (result = r));

    const req = http.expectOne((r) => r.url === '/api/offers/1/characteristics');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        characteristicId: 1,
        name: 'Speed',
        description: 'Downstream speed',
        dataType: 'NUMBER',
        mandatory: true,
      },
    ]);

    expect(result?.[0].dataType).toBe('NUMBER');
    expect(result?.[0].mandatory).toBe(true);
  });

  it('an offer with no characteristics is an empty 200, not an error (AC-SALE-01-21)', () => {
    let result: readonly CharacteristicResponse[] | undefined;
    let errored = false;
    service.getOfferCharacteristics(3).subscribe({
      next: (r) => (result = r),
      error: () => (errored = true),
    });

    http.expectOne((r) => r.url === '/api/offers/3/characteristics').flush([]);

    expect(errored).toBe(false);
    expect(result).toEqual([]);
  });

  it('every request uses a relative /api path (FE-ADR-004 §1)', () => {
    service.listOffers().subscribe();
    service.listCampaigns().subscribe();
    service.getOfferCharacteristics(2).subscribe();

    for (const req of http.match(() => true)) {
      expect(req.request.url.startsWith('/api/')).toBe(true);
      req.flush([]);
    }
  });
});
