import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { provideCoreHttp } from '../../../core/http';
import { I18nService } from '../../../core/i18n';
import { CustomerFlashService } from '../state/customer-flash.service';
import type { AddressResponse, ContactMediumResponse, CustomerDetailResponse } from '../model';
import { CustomerDetail } from './customer-detail';

/**
 * Customer Info screen spec (mock §6.4; FR-CUST-02/04/05, FR-ADDR-01..05,
 * FR-CNTC-01/02). House style: selection ONLY via data-testid, copy asserted
 * on RENDERED text, HTTP faked at the boundary — the real store + api service
 * + core interceptors run (like the Customer Search golden-path spec).
 */
const ALI: CustomerDetailResponse = {
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

const HOME: AddressResponse = {
  addressId: 1,
  cityId: 1,
  cityName: 'Istanbul',
  districtId: 1,
  districtName: 'Kadıköy',
  street: 'Bağdat Cad.',
  houseFlatNumber: '12',
  addressDescription: 'Home',
  primary: true,
};
const WORK: AddressResponse = {
  ...HOME,
  addressId: 2,
  cityId: 2,
  cityName: 'Ankara',
  districtId: 3,
  districtName: 'Çankaya',
  street: 'Yeni Cad.',
  houseFlatNumber: '7',
  addressDescription: 'Work',
  primary: false,
};

const CONTACT: ContactMediumResponse = {
  email: 'ali@example.com',
  mobilePhone: '05321112233',
  homePhone: null,
  fax: null,
};

function apiError(status: number, messageKey: string) {
  return {
    body: { timestamp: '', status, error: 'x', messageKey, message: 'raw', path: '/api' },
    opts: { status, statusText: 'Error' },
  };
}

describe('CustomerDetail', () => {
  let http: HttpTestingController;
  /** Query params the ActivatedRoute stub reports; set BEFORE render(). */
  let queryParams: Record<string, string> = {};

  beforeEach(() => {
    localStorage.clear();
    queryParams = {};
    TestBed.configureTestingModule({
      imports: [CustomerDetail],
      providers: [
        provideCoreHttp(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          // Query params are read through GETTERS so a test can set
          // `queryParams` before it renders — TestBed.overrideProvider is not
          // available once the module has been instantiated, which the
          // HttpTestingController injection below already does. Empty by
          // default: the screen opens on the Info tab with nothing expanded.
          useValue: {
            paramMap: of(convertToParamMap({ customerNumber: '1001' })),
            get queryParamMap() {
              return of(convertToParamMap(queryParams));
            },
            get snapshot() {
              return {
                paramMap: convertToParamMap({ customerNumber: '1001' }),
                queryParamMap: convertToParamMap(queryParams),
              };
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(): ComponentFixture<CustomerDetail> {
    const fixture = TestBed.createComponent(CustomerDetail);
    fixture.detectChanges();
    return fixture;
  }

  function byTestId(fixture: ComponentFixture<CustomerDetail>, id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  function click(fixture: ComponentFixture<CustomerDetail>, id: string): void {
    (byTestId(fixture, id) as HTMLButtonElement).click();
    // Two passes: dialog-open effects write signals (form prefill via CVA) that
    // need one more sync to reach the DOM under zoneless TestBed.
    fixture.detectChanges();
    fixture.detectChanges();
  }

  function type(fixture: ComponentFixture<CustomerDetail>, testId: string, value: string): void {
    const input = byTestId(fixture, testId) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  /** Flush the screen's three independent section reads. */
  function flushInitial(fixture: ComponentFixture<CustomerDetail>): void {
    http.expectOne('/api/customers/1001').flush(ALI);
    http.expectOne('/api/customers/1001/addresses').flush([HOME, WORK]);
    http.expectOne('/api/customers/1001/contact-medium').flush(CONTACT);
    fixture.detectChanges();
  }

  // ---- read views ----------------------------------------------------------

  it('loads THREE independent sections and renders the demographic read view', () => {
    const fixture = render();
    flushInitial(fixture);
    expect(byTestId(fixture, 'customer-detail-name')?.textContent).toContain('Ali Yildiz');
    expect(byTestId(fixture, 'customer-detail-info-value-second-name')?.textContent).toContain('—');
    expect(byTestId(fixture, 'customer-detail-info-value-birth-date')?.textContent).toContain(
      '14.05.1990', // ISO on the wire, dd.MM.yyyy on screen (scope §2.6)
    );
    expect(byTestId(fixture, 'customer-detail-info-value-nationality-id')?.textContent).toContain(
      '12345678901',
    );
  });

  it('shows the not-found state on a 404 detail (soft-deleted / unknown customer)', () => {
    const fixture = render();
    const notFound = apiError(404, 'MSG-CUST-NOT-FOUND');
    http.expectOne('/api/customers/1001').flush(notFound.body, notFound.opts);
    http.expectOne('/api/customers/1001/addresses').flush([]);
    http.expectOne('/api/customers/1001/contact-medium').flush(CONTACT);
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-detail-not-found')).not.toBeNull();
    expect(byTestId(fixture, 'customer-detail-back-button')).not.toBeNull();
    expect(byTestId(fixture, 'customer-detail-tabs')).toBeNull();
  });

  it('surfaces a non-404 detail failure as a banner whose Retry re-issues the read', () => {
    const fixture = render();
    const down = apiError(503, 'MSG-SERVICE-UNAVAILABLE');
    http.expectOne('/api/customers/1001').flush(down.body, down.opts);
    http.expectOne('/api/customers/1001/addresses').flush([HOME]);
    http.expectOne('/api/customers/1001/contact-medium').flush(CONTACT);
    fixture.detectChanges();
    const banner = byTestId(fixture, 'customer-detail-error-banner');
    expect(banner?.textContent).toContain('temporarily unavailable');
    expect(banner?.textContent).not.toContain('raw');
    click(fixture, 'customer-detail-retry-button');
    http.expectOne('/api/customers/1001').flush(ALI);
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-detail-name')?.textContent).toContain('Ali Yildiz');
  });

  // ---- account tab: the account (F6) and product (2026-07-31) sections ------

  const BILLING_ACCOUNT = {
    accountNumber: '1261000010',
    customerNumber: 1001,
    accountName: 'Ali Billing',
    accountTypeCode: '224',
    accountTypeName: 'Billing Account',
    billingAddressId: 1,
    accountStatus: 'Active',
  };

  function openAccountTab(fixture: ComponentFixture<CustomerDetail>): void {
    click(fixture, 'customer-detail-tabs-account');
    const request = http.expectOne((r) => r.url === '/api/accounts');
    expect(request.request.params.get('customerId')).toBe('1001');
    request.flush([BILLING_ACCOUNT]);
    fixture.detectChanges();
  }

  it('account tab loads the billing accounts through the account section', () => {
    const fixture = render();
    flushInitial(fixture);
    openAccountTab(fixture);
    expect(byTestId(fixture, 'customer-detail-account-row-1261000010')).not.toBeNull();
    expect(
      (byTestId(fixture, 'customer-detail-account-create-button') as HTMLButtonElement).disabled,
    ).toBe(false); // functional since F6
  });

  // BUG-1 (scope §4.30): the account dialog COLLECTS the address, Customer Info
  // CREATES it — through the very `addAddress` the Address tab uses, so there is
  // exactly one FR-ADDR-02 path and the account feature stays free of it.
  it('creates a billing address asked for by the account dialog, then auto-selects it', () => {
    const fixture = render();
    flushInitial(fixture);
    openAccountTab(fixture);

    click(fixture, 'customer-detail-account-create-button');
    click(fixture, 'customer-detail-account-dialog-add-address-button');
    http.expectOne('/api/cities').flush([{ cityId: 2, name: 'Ankara' }]);
    fixture.detectChanges();

    const panel = 'customer-detail-account-dialog-new-address';
    click(fixture, `${panel}-city-select`);
    click(fixture, `${panel}-city-select-option-2`);
    http.expectOne('/api/cities/2/districts').flush([{ districtId: 3, name: 'Çankaya' }]);
    fixture.detectChanges();
    click(fixture, `${panel}-district-select`);
    click(fixture, `${panel}-district-select-option-3`);
    type(fixture, `${panel}-street-input`, 'Yeni Cad.');
    type(fixture, `${panel}-house-no-input`, '7');
    type(fixture, `${panel}-description-input`, 'Work');
    click(fixture, `${panel}-save-button`);

    // The documented standalone-address body: five fields, no `primary`.
    const post = http.expectOne('/api/customers/1001/addresses');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      cityId: 2,
      districtId: 3,
      street: 'Yeni Cad.',
      houseFlatNumber: '7',
      addressDescription: 'Work',
    });
    post.flush(WORK, { status: 201, statusText: 'Created' });
    // `addAddress` refreshes the list, which is what re-feeds the Select.
    http.expectOne('/api/customers/1001/addresses').flush([HOME, WORK]);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(byTestId(fixture, panel)).toBeNull();
    expect(
      byTestId(fixture, 'customer-detail-account-dialog-address-select')?.textContent,
    ).toContain('Yeni Cad.');
  });

  it('the products section is NOT read until its account row is expanded', () => {
    const fixture = render();
    flushInitial(fixture);
    openAccountTab(fixture);
    // Collapsed: no product markup and, critically, no product request at all.
    expect(byTestId(fixture, 'customer-detail-product-section')).toBeNull();
    http.expectNone((r) => r.url === '/api/products');
    // The former FE-ADR-013 §A3 "Coming soon" block is gone for good.
    expect(byTestId(fixture, 'customer-detail-products-coming-soon')).toBeNull();
  });

  it('expanding an account row renders the product feature section for THAT account', () => {
    const fixture = render();
    flushInitial(fixture);
    openAccountTab(fixture);

    click(fixture, 'customer-detail-account-row-1261000010-expander');
    const products = http.expectOne((r) => r.url === '/api/products');
    // The two sibling features meet ONLY through the accountNumber string.
    expect(products.request.params.get('accountNumber')).toBe('1261000010');
    products.flush([
      {
        productId: 1,
        productName: 'ADSL 8MB',
        campaignName: 'ADSL Hosgeldin Kampanyasi',
        campaignId: 'CMP-ADSL-01',
        productStatus: 'Active',
      },
    ]);
    fixture.detectChanges();

    expect(byTestId(fixture, 'customer-detail-account-row-1261000010-expansion')).not.toBeNull();
    expect(byTestId(fixture, 'customer-detail-product-row-1')?.textContent).toContain('ADSL 8MB');

    // Collapsing removes the section again (and issues nothing further).
    click(fixture, 'customer-detail-account-row-1261000010-expander');
    expect(byTestId(fixture, 'customer-detail-product-section')).toBeNull();
    http.expectNone((r) => r.url === '/api/products');
  });

  /**
   * The landing contract the sale wizard uses after a successful submit
   * (scope §4.33): `?tab=account&account=…` opens the account tab with THAT
   * billing account already expanded, so the freshly created products are on
   * screen without a click — and they are READ FROM THE BACKEND, never carried
   * across from the wizard.
   */
  describe('?tab= / ?account= landing', () => {
    function renderWithQuery(query: Record<string, string>): ComponentFixture<CustomerDetail> {
      queryParams = query;
      return render();
    }

    it('opens the account tab with the named account expanded and re-reads its products', () => {
      const fixture = renderWithQuery({ tab: 'account', account: '1261000010' });
      flushInitial(fixture);

      // The account tab is active WITHOUT a click, so the list loads at once.
      http.expectOne((r) => r.url === '/api/accounts').flush([BILLING_ACCOUNT]);
      fixture.detectChanges();

      // …and the row is already open, which is what triggers the product read.
      // The products come from the SERVER — nothing was injected client-side.
      const products = http.expectOne((r) => r.url === '/api/products');
      expect(products.request.params.get('accountNumber')).toBe('1261000010');
      products.flush([
        {
          productId: 7,
          productName: 'ADSL 8MB',
          campaignName: null,
          campaignId: null,
          productStatus: 'Active',
        },
      ]);
      fixture.detectChanges();

      expect(byTestId(fixture, 'customer-detail-account-row-1261000010-expansion')).not.toBeNull();
      expect(byTestId(fixture, 'customer-detail-product-row-7')?.textContent).toContain('ADSL 8MB');
    });

    it('ignores an account the customer does not own, and an unknown tab', () => {
      const fixture = renderWithQuery({ tab: 'nope', account: '9999999999' });
      flushInitial(fixture);

      // Unknown tab ⇒ the default Info tab, so no account read happens at all.
      expect(byTestId(fixture, 'customer-detail-info-value-second-name')).not.toBeNull();
      http.expectNone((r) => r.url === '/api/accounts');
    });

    it('renders a PARAMETERIZED flash as a toast (order submitted)', () => {
      TestBed.inject(CustomerFlashService).set('UI-SALE-TOAST-ORDER-SUBMITTED', {
        orderNumber: '2026000018',
        count: 3,
        accountNumber: '1261000010',
      });
      const fixture = render();
      flushInitial(fixture);

      const toast = byTestId(fixture, 'customer-detail-toast');
      expect(toast?.textContent).toContain('2026000018');
      expect(toast?.textContent).toContain('3 products');
      expect(toast?.textContent).toContain('1261000010');
      // No placeholder survives into the DOM (FE-ADR-012 §h.4).
      expect(toast?.textContent).not.toContain('{');
    });
  });

  // ---- sale entry point (AC-SALE-01-01/02, AC-SALE-02-01) ------------------

  /** Expand an account row and answer the product read it triggers. */
  function expandRow(fixture: ComponentFixture<CustomerDetail>, accountNumber: string): void {
    click(fixture, `customer-detail-account-row-${accountNumber}-expander`);
    http.expectOne((r) => r.url === '/api/products').flush([]);
    fixture.detectChanges();
  }

  it('AC-SALE-01-01: Start New Sale opens the wizard for THAT billing account', () => {
    const fixture = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    flushInitial(fixture);
    openAccountTab(fixture);
    expandRow(fixture, '1261000010');

    click(fixture, 'customer-detail-account-row-1261000010-start-sale');

    // The account travels as a QUERY param: it qualifies which billing account
    // the sale is for, and the products created by it must come back to it.
    expect(navigate).toHaveBeenCalledWith(['/customers', 1001, 'sales', 'new'], {
      queryParams: { accountNumber: '1261000010' },
    });
  });

  it('AC-SALE-02-01: the action is disabled on a PASSIVE account row', () => {
    const fixture = render();
    flushInitial(fixture);

    click(fixture, 'customer-detail-tabs-account');
    http
      .expectOne((r) => r.url === '/api/accounts')
      .flush([{ ...BILLING_ACCOUNT, accountNumber: '1261000028', accountStatus: 'Passive' }]);
    fixture.detectChanges();
    expandRow(fixture, '1261000028');

    // Drawn but inert: the user sees WHY the sale is unavailable rather than
    // hunting for a button that is not there. The mock renders this as an
    // unfocusable aria-disabled span, not a disabled <button> (scope §4.32) —
    // so assert INERTNESS, not the tag: no real button, nothing to activate.
    const action = byTestId(fixture, 'customer-detail-account-row-1261000028-start-sale');
    expect(action).not.toBeNull();
    expect(action?.getAttribute('aria-disabled')).toBe('true');
    expect(action?.tagName).toBe('SPAN');
    expect(action?.hasAttribute('tabindex')).toBe(false);
  });

  it('AC-SALE-01-02: no billing account ⇒ no action, and the user is told to create one', () => {
    const fixture = render();
    flushInitial(fixture);

    click(fixture, 'customer-detail-tabs-account');
    http.expectOne((r) => r.url === '/api/accounts').flush([]);
    fixture.detectChanges();

    expect(byTestId(fixture, 'customer-detail-account-row-1261000010-start-sale')).toBeNull();
    expect(byTestId(fixture, 'customer-detail-account-empty-hint')?.textContent).toContain(
      'Create a billing account before starting a sale',
    );
  });

  // ---- address tab (FR-ADDR-01..05) ----------------------------------------

  it('renders address cards; the primary card is marked and its delete is disabled', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-tabs-address');
    expect(byTestId(fixture, 'customer-detail-address-1')?.textContent).toContain(
      'Kadıköy, Istanbul',
    );
    expect(byTestId(fixture, 'customer-detail-address-1')?.textContent).toContain('Primary');
    expect(
      (byTestId(fixture, 'customer-detail-address-1-delete') as HTMLButtonElement).disabled,
    ).toBe(true);
    expect(byTestId(fixture, 'customer-detail-address-1-set-primary')).toBeNull();
    expect(byTestId(fixture, 'customer-detail-address-2-set-primary')).not.toBeNull();
  });

  it('set-primary PATCHes and then re-reads the list (server is the authority)', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-tabs-address');
    click(fixture, 'customer-detail-address-2-set-primary');
    http.expectOne('/api/customers/1001/addresses/2/primary').flush({ ...WORK, primary: true });
    http.expectOne('/api/customers/1001/addresses').flush([
      { ...HOME, primary: false },
      { ...WORK, primary: true },
    ]);
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-detail-address-2')?.textContent).toContain('Primary');
    expect(byTestId(fixture, 'customer-detail-address-1-set-primary')).not.toBeNull();
  });

  it('address delete asks confirmation (AC-ADDR-04-03), then DELETEs and refreshes', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-tabs-address');
    click(fixture, 'customer-detail-address-2-delete');
    expect(byTestId(fixture, 'customer-detail-address-delete-dialog')?.textContent).toContain(
      'Are you sure to delete this address?',
    );
    click(fixture, 'customer-detail-address-delete-dialog-confirm-button');
    http.expectOne('/api/customers/1001/addresses/2').flush(null, { status: 204, statusText: '' });
    http.expectOne('/api/customers/1001/addresses').flush([HOME]);
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-detail-address-delete-dialog')).toBeNull();
    expect(byTestId(fixture, 'customer-detail-address-2')).toBeNull();
  });

  it('a backend delete guard (409) flips the confirm dialog into its error state — never pre-computed', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-tabs-address');
    click(fixture, 'customer-detail-address-2-delete');
    click(fixture, 'customer-detail-address-delete-dialog-confirm-button');
    const guard = apiError(409, 'MSG-ADDR-IN-USE');
    http.expectOne('/api/customers/1001/addresses/2').flush(guard.body, guard.opts);
    fixture.detectChanges();
    const error = byTestId(fixture, 'customer-detail-address-delete-dialog-error');
    expect(error?.textContent).toContain('linked to an active billing account');
    expect(error?.textContent).not.toContain('raw');
    // AC-ADDR-04-04: the address survives a refused delete — no optimistic
    // removal, and no client-side "is it in use?" guess (the backend check is
    // still a customer-service no-op today; scope §5.9 / §4.27).
    expect(byTestId(fixture, 'customer-detail-address-2')).not.toBeNull();
    expect(http.match((r) => r.url === '/api/customers/1001/addresses').length).toBe(0);
  });

  it('add-address opens the dialog, loads the city cascade and disables save until complete', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-tabs-address');
    click(fixture, 'customer-detail-address-add-button');
    http.expectOne('/api/cities').flush([
      { cityId: 1, name: 'Istanbul' },
      { cityId: 2, name: 'Ankara' },
    ]);
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-detail-address-dialog')).not.toBeNull();
    expect(
      (byTestId(fixture, 'customer-detail-address-dialog-save-button') as HTMLButtonElement)
        .disabled,
    ).toBe(true);
    // choosing a city loads its districts (cascade — never hardcoded, ADR-002)
    click(fixture, 'customer-detail-address-dialog-city-select');
    click(fixture, 'customer-detail-address-dialog-city-select-option-1');
    http.expectOne('/api/cities/1/districts').flush([{ districtId: 1, name: 'Kadıköy' }]);
    fixture.detectChanges();
    click(fixture, 'customer-detail-address-dialog-district-select');
    expect(
      byTestId(fixture, 'customer-detail-address-dialog-district-select-option-1'),
    ).not.toBeNull();
  });

  it('edit-address pre-fills, PUTs the documented body and refreshes the list', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-tabs-address');
    click(fixture, 'customer-detail-address-2-edit');
    http.expectOne('/api/cities').flush([{ cityId: 2, name: 'Ankara' }]);
    http.expectOne('/api/cities/2/districts').flush([{ districtId: 3, name: 'Çankaya' }]);
    fixture.detectChanges();
    const street = byTestId(
      fixture,
      'customer-detail-address-dialog-street-input',
    ) as HTMLInputElement;
    expect(street.value).toBe('Yeni Cad.');
    type(fixture, 'customer-detail-address-dialog-street-input', 'Güncel Cad.');
    click(fixture, 'customer-detail-address-dialog-save-button');
    const put = http.expectOne('/api/customers/1001/addresses/2');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({
      cityId: 2,
      districtId: 3,
      street: 'Güncel Cad.',
      houseFlatNumber: '7',
      addressDescription: 'Work',
    });
    expect('primary' in (put.request.body as object)).toBe(false); // PATCH-only concern
    put.flush({ ...WORK, street: 'Güncel Cad.' });
    http
      .expectOne('/api/customers/1001/addresses')
      .flush([HOME, { ...WORK, street: 'Güncel Cad.' }]);
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-detail-address-dialog')).toBeNull();
    expect(byTestId(fixture, 'customer-detail-address-2')?.textContent).toContain('Güncel Cad.');
  });

  // ---- demographic edit (FR-CUST-04) ---------------------------------------

  it('demographic edit pre-fills, PUTs the FLAT body and shows the success toast', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-edit-info-button');
    const first = byTestId(
      fixture,
      'customer-detail-info-dialog-first-name-input',
    ) as HTMLInputElement;
    expect(first.value).toBe('Ali');
    type(fixture, 'customer-detail-info-dialog-first-name-input', 'Velihan');
    click(fixture, 'customer-detail-info-dialog-save-button');
    const put = http.expectOne('/api/customers/1001');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toMatchObject({ firstName: 'Velihan', gender: 'Male' });
    expect('demographic' in (put.request.body as object)).toBe(false); // flat, not wrapped
    put.flush({ ...ALI, firstName: 'Velihan' });
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-detail-info-dialog')).toBeNull();
    expect(byTestId(fixture, 'customer-detail-name')?.textContent).toContain('Velihan');
    expect(byTestId(fixture, 'customer-detail-toast')?.textContent).toContain(
      'Customer updated successfully.',
    );
  });

  it('demographic client checks gate the save (11-digit ID) and clear on typing — UX only', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-edit-info-button');
    type(fixture, 'customer-detail-info-dialog-nationality-id-input', '123');
    click(fixture, 'customer-detail-info-dialog-save-button');
    expect(http.match((r) => r.method === 'PUT').length).toBe(0);
    expect(
      byTestId(fixture, 'customer-detail-info-dialog-nationality-id-error')?.textContent,
    ).toContain('11 digits');
    type(fixture, 'customer-detail-info-dialog-nationality-id-input', '12345678901');
    expect(
      byTestId(fixture, 'customer-detail-info-dialog-nationality-id-error')?.textContent?.trim(),
    ).toBe('');
  });

  // ---- contact tab (FR-CNTC-01/02) -----------------------------------------

  it('renders the contact read view in the recorded Email → Home → Mobile → Fax order', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-tabs-contact');
    const grid = byTestId(fixture, 'customer-detail-contact-grid');
    expect(grid).not.toBeNull();
    expect(byTestId(fixture, 'customer-detail-contact-value-email')?.textContent).toContain(
      'ali@example.com',
    );
    expect(byTestId(fixture, 'customer-detail-contact-value-home')?.textContent).toContain('—');
    const ids = Array.from(
      grid?.querySelectorAll('[data-testid^="customer-detail-contact-value-"]') ?? [],
    ).map((el) => el.getAttribute('data-testid'));
    expect(ids).toEqual([
      'customer-detail-contact-value-email',
      'customer-detail-contact-value-home',
      'customer-detail-contact-value-mobile',
      'customer-detail-contact-value-fax',
    ]);
  });

  it('contact edit validates mobile (05, 11 digits) client-side, then PUTs and toasts', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-tabs-contact');
    click(fixture, 'customer-detail-edit-contact-button');
    type(fixture, 'customer-detail-contact-dialog-mobile-input', '02121112233');
    click(fixture, 'customer-detail-contact-dialog-save-button');
    expect(byTestId(fixture, 'customer-detail-contact-dialog-mobile-error')?.textContent).toContain(
      'valid phone number',
    );
    type(fixture, 'customer-detail-contact-dialog-mobile-input', '05327778899');
    click(fixture, 'customer-detail-contact-dialog-save-button');
    const put = http.expectOne('/api/customers/1001/contact-medium');
    expect(put.request.body).toEqual({
      email: 'ali@example.com',
      mobilePhone: '05327778899',
      homePhone: null,
      fax: null,
    });
    put.flush({ ...CONTACT, mobilePhone: '05327778899' });
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-detail-contact-dialog')).toBeNull();
    expect(byTestId(fixture, 'customer-detail-contact-value-mobile')?.textContent).toContain(
      '05327778899',
    );
    expect(byTestId(fixture, 'customer-detail-toast')?.textContent).toContain(
      'Contact medium updated successfully.',
    );
  });

  // ---- delete customer (FR-CUST-05) ----------------------------------------

  it('delete: confirm → DELETE → flash queued for Customer Search + navigation home', () => {
    const fixture = render();
    flushInitial(fixture);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    click(fixture, 'customer-detail-delete-customer-button');
    expect(byTestId(fixture, 'customer-detail-delete-dialog')?.textContent).toContain(
      'Are you sure to delete this customer?',
    );
    click(fixture, 'customer-detail-delete-dialog-confirm-button');
    const request = http.expectOne('/api/customers/1001');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: '' });
    fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith(['/customers']);
    expect(TestBed.inject(CustomerFlashService).consume()).toEqual({
      key: 'UI-DETAIL-TOAST-CUSTOMER-DELETED',
    });
  });

  it('delete blocked by the backend (409 MSG-CUST-HAS-PRODUCTS) shows the mock error state', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-delete-customer-button');
    click(fixture, 'customer-detail-delete-dialog-confirm-button');
    const guard = apiError(409, 'MSG-CUST-HAS-PRODUCTS');
    http.expectOne('/api/customers/1001').flush(guard.body, guard.opts);
    fixture.detectChanges();
    const error = byTestId(fixture, 'customer-detail-delete-dialog-error');
    expect(error?.textContent).toContain('active products');
    expect(byTestId(fixture, 'customer-detail-delete-dialog-confirm-button')).toBeNull();
    click(fixture, 'customer-detail-delete-dialog-close-button');
    expect(byTestId(fixture, 'customer-detail-delete-dialog')).toBeNull();
  });

  // ---- i18n ----------------------------------------------------------------

  it('switches language instantly and never leaks a raw catalogue key (AC-LANG-01-02)', () => {
    const fixture = render();
    flushInitial(fixture);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Customer info');
    expect(root.textContent).not.toContain('UI-DETAIL');
    TestBed.inject(I18nService).setLanguage('tr');
    fixture.detectChanges();
    expect(root.textContent).toContain('Müşteri bilgisi');
    expect(root.textContent).not.toContain('UI-DETAIL');
  });
});
