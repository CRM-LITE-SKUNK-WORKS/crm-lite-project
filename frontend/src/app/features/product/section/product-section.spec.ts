import { Component, input } from '@angular/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { provideCoreHttp } from '../../../core/http';
import { ProductSection } from './product-section';
import { type ProductDetail, type ProductRow } from '../model';

/**
 * Product section spec (FR-PROD-01..02). House style: data-testid selection,
 * rendered-copy assertions, HTTP faked at the boundary (real stores + api
 * service + core interceptors run).
 *
 * The four rules this file exists to pin: Passive products stay listed, the
 * empty account shows MSG-PROD-NONE, an unknown product id shows
 * MSG-PROD-NOT-FOUND, and NOTHING anywhere paginates.
 */
const ACCOUNT = '1261000010';

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

function apiError(status: number, messageKey: string) {
  return {
    body: { timestamp: '', status, error: 'x', messageKey, message: 'raw', path: '/api/products' },
    opts: { status, statusText: 'Error' },
  };
}

@Component({
  imports: [ProductSection],
  template: `<app-product-section [accountNumber]="accountNumber()" />`,
})
class Host {
  readonly accountNumber = input<string>(ACCOUNT);
}

describe('ProductSection', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideCoreHttp(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(): ComponentFixture<Host> {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    return fixture;
  }

  function byTestId(fixture: ComponentFixture<Host>, id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  function click(fixture: ComponentFixture<Host>, id: string): void {
    (byTestId(fixture, id) as HTMLButtonElement).click();
    fixture.detectChanges();
    fixture.detectChanges();
  }

  function flushList(fixture: ComponentFixture<Host>, rows: readonly ProductRow[]) {
    const request = http.expectOne((r) => r.url === '/api/products');
    expect(request.request.params.get('accountNumber')).toBe(ACCOUNT);
    request.flush([...rows]);
    fixture.detectChanges();
    return request;
  }

  // ---- FR-PROD-01: the list ------------------------------------------------

  it('AC-PROD-01-03: PASSIVE products STAY in the list, in server order', () => {
    const fixture = render();
    flushList(fixture, [ACTIVE, CAMPAIGNLESS, PASSIVE]);

    const rowIds = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr[data-testid]'),
    ).map((row) => row.getAttribute('data-testid'));
    expect(rowIds).toEqual([
      'customer-detail-product-row-1',
      'customer-detail-product-row-3',
      'customer-detail-product-row-4',
    ]); // exactly as received — the client neither filters nor re-sorts

    // The Passive row is present AND labelled Passive — not hidden, not greyed away.
    expect(byTestId(fixture, 'customer-detail-product-row-4')).not.toBeNull();
    expect(byTestId(fixture, 'customer-detail-product-row-4-status')?.textContent).toContain(
      'Passive',
    );
    expect(byTestId(fixture, 'customer-detail-product-row-1-status')?.textContent).toContain(
      'Active',
    );
  });

  it('AC-PROD-01-03: a campaign-less product renders "-" in BOTH campaign columns', () => {
    const fixture = render();
    flushList(fixture, [ACTIVE, CAMPAIGNLESS]);

    const withCampaign = byTestId(fixture, 'customer-detail-product-row-1');
    expect(withCampaign?.textContent).toContain('ADSL Hosgeldin Kampanyasi');
    // The campaign identity shown is the PUBLIC code, never an internal id.
    expect(withCampaign?.textContent).toContain('CMP-ADSL-01');

    const cells = Array.from(
      byTestId(fixture, 'customer-detail-product-row-3')?.querySelectorAll('td') ?? [],
    ).map((cell) => cell.textContent?.trim());
    expect(cells[2]).toBe('-'); // campaign name
    expect(cells[3]).toBe('-'); // campaign id
  });

  it('AC-PROD-01-02: an account with no products shows the frontend-only MSG-PROD-NONE', () => {
    const fixture = render();
    flushList(fixture, []); // 200 [] — the backend never sends MSG-PROD-NONE

    const empty = byTestId(fixture, 'customer-detail-product-empty-state');
    expect(empty?.textContent).toContain('No products are linked to this billing account.');
    expect(byTestId(fixture, 'customer-detail-product-table')).toBeNull();
  });

  it('NO PAGINATION anywhere: no page/size params, no pagination control', () => {
    const fixture = render();
    const request = flushList(fixture, [ACTIVE, CAMPAIGNLESS, PASSIVE]);

    // FR-PROD-01 defines no pagination rule and the endpoint returns a plain
    // array — the client must neither ask for a page nor render a pager.
    expect(request.request.params.keys()).toEqual(['accountNumber']);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid*="pagination"]')).toBeNull();
    expect(host.querySelector('nav')).toBeNull();
    // All rows render — nothing is truncated to a page size.
    expect(host.querySelectorAll('tbody tr[data-testid]').length).toBe(3);
  });

  it('AC-PROD-01-04: the Action column is view-only — Deactivate is present but INERT', () => {
    const fixture = render();
    flushList(fixture, [ACTIVE]);

    const view = byTestId(fixture, 'customer-detail-product-row-1-view') as HTMLButtonElement;
    expect(view.disabled).toBe(false);
    expect(view.getAttribute('aria-label')).toBe('View product');

    const deactivate = byTestId(
      fixture,
      'customer-detail-product-row-1-deactivate',
    ) as HTMLButtonElement;
    expect(deactivate.disabled).toBe(true);
    expect(deactivate.getAttribute('aria-label')).toContain('coming soon');
    // Clicking the inert control must not produce a request of any kind.
    deactivate.click();
    fixture.detectChanges();
    http.expectNone(() => true);
  });

  it('surfaces a list failure with the backend messageKey, and Retry re-reads', () => {
    const fixture = render();
    const notFound = apiError(404, 'MSG-ACCT-NOT-FOUND'); // unknown account number
    http.expectOne((r) => r.url === '/api/products').flush(notFound.body, notFound.opts);
    fixture.detectChanges();

    const banner = byTestId(fixture, 'customer-detail-product-error-banner');
    expect(banner?.textContent).toContain('Billing account not found.');
    expect(banner?.textContent).not.toContain('raw'); // FE-ADR-008 §2

    click(fixture, 'customer-detail-product-retry-button');
    flushList(fixture, [ACTIVE]);
    expect(byTestId(fixture, 'customer-detail-product-row-1')).not.toBeNull();
  });

  // ---- FR-PROD-02: the detail modal ----------------------------------------

  it('AC-PROD-02-01: View opens a modal with the five contract fields, one GET', () => {
    const fixture = render();
    flushList(fixture, [ACTIVE]);
    click(fixture, 'customer-detail-product-row-1-view');

    const request = http.expectOne('/api/products/1');
    expect(request.request.method).toBe('GET');
    request.flush(DETAIL);
    fixture.detectChanges();

    const dialog = byTestId(fixture, 'customer-detail-product-dialog');
    expect(dialog).not.toBeNull();
    expect(dialog?.textContent).toContain('ADSL 8MB'); // title = the row's name
    expect(byTestId(fixture, 'customer-detail-product-dialog-value-offer-name')?.textContent).toContain(
      'ADSL Data Modem Offer',
    );
    expect(byTestId(fixture, 'customer-detail-product-dialog-value-offer-id')?.textContent).toContain('2');
    expect(byTestId(fixture, 'customer-detail-product-dialog-value-spec-id')?.textContent).toContain('2');
    expect(byTestId(fixture, 'customer-detail-product-dialog-value-campaign')?.textContent).toContain(
      'ADSL Hosgeldin Kampanyasi',
    );
    // The service address is resolved SERVER-side (parent chain included) —
    // exactly one request was made for the whole modal.
    const address = byTestId(fixture, 'customer-detail-product-dialog-service-address');
    expect(address?.textContent).toContain('Bagdat Cad. 12/4');
    expect(address?.textContent).toContain('Kadikoy, Istanbul');

    click(fixture, 'customer-detail-product-dialog-close-action');
    expect(byTestId(fixture, 'customer-detail-product-dialog')).toBeNull();
  });

  it('FR-PROD-02: a null serviceAddress still renders the modal', () => {
    const fixture = render();
    flushList(fixture, [CAMPAIGNLESS]);
    click(fixture, 'customer-detail-product-row-3-view');
    http
      .expectOne('/api/products/3')
      .flush({ ...DETAIL, campaign: null, serviceAddress: null } satisfies ProductDetail);
    fixture.detectChanges();

    expect(byTestId(fixture, 'customer-detail-product-dialog')).not.toBeNull();
    expect(byTestId(fixture, 'customer-detail-product-dialog-service-address-none')).not.toBeNull();
    // Campaign-less detail: em dash, same convention as the read grids.
    expect(byTestId(fixture, 'customer-detail-product-dialog-value-campaign')?.textContent).toContain(
      '—',
    );
  });

  it('404 MSG-PROD-NOT-FOUND renders the not-found state, not a generic error', () => {
    const fixture = render();
    flushList(fixture, [ACTIVE]);
    click(fixture, 'customer-detail-product-row-1-view');

    const gone = apiError(404, 'MSG-PROD-NOT-FOUND');
    http.expectOne('/api/products/1').flush(gone.body, gone.opts);
    fixture.detectChanges();

    const notFound = byTestId(fixture, 'customer-detail-product-dialog-not-found');
    expect(notFound?.textContent).toContain('Product not found.');
    expect(notFound?.textContent).not.toContain('raw');
    expect(byTestId(fixture, 'customer-detail-product-dialog-error')).toBeNull();
    // The list behind the modal is untouched by the failed detail read.
    expect(byTestId(fixture, 'customer-detail-product-row-1')).not.toBeNull();
  });

  it('a non-404 detail failure shows the error banner with Retry', () => {
    const fixture = render();
    flushList(fixture, [ACTIVE]);
    click(fixture, 'customer-detail-product-row-1-view');
    const down = apiError(503, 'MSG-SERVICE-UNAVAILABLE');
    http.expectOne('/api/products/1').flush(down.body, down.opts);
    fixture.detectChanges();

    expect(byTestId(fixture, 'customer-detail-product-dialog-error')?.textContent).toContain(
      'temporarily unavailable',
    );
    click(fixture, 'customer-detail-product-dialog-retry-button');
    http.expectOne('/api/products/1').flush(DETAIL);
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-detail-product-dialog-value-offer-name')).not.toBeNull();
  });
});
