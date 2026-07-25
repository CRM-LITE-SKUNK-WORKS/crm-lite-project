import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { provideCoreHttp } from '../../../core/http';
import { I18nService } from '../../../core/i18n';
import { CustomerFlashService } from '../state/customer-flash.service';
import type {
  AddressResponse,
  ContactMediumResponse,
  CustomerDetailResponse,
} from '../model';
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

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [CustomerDetail],
      providers: [
        provideCoreHttp(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ customerNumber: '1001' })),
            snapshot: { paramMap: convertToParamMap({ customerNumber: '1001' }) },
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

  // ---- account tab: the account feature's section (F6); products still A3 --

  it('account tab loads the billing accounts through the account section; products stay Coming soon', () => {
    const fixture = render();
    flushInitial(fixture);
    click(fixture, 'customer-detail-tabs-account');
    const request = http.expectOne((r) => r.url === '/api/accounts');
    expect(request.request.params.get('customerId')).toBe('1001');
    request.flush([
      {
        accountNumber: '1261000010',
        accountName: 'Ali Billing',
        accountTypeCode: '224',
        accountTypeName: 'Billing Account',
        billingAddressId: 1,
        accountStatus: 'Active',
      },
    ]);
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-detail-account-row-1261000010')).not.toBeNull();
    expect(
      (byTestId(fixture, 'customer-detail-account-create-button') as HTMLButtonElement).disabled,
    ).toBe(false); // functional since F6
    const products = byTestId(fixture, 'customer-detail-products-coming-soon');
    expect(products?.getAttribute('aria-disabled')).toBe('true');
    expect(products?.textContent).toContain('Coming soon');
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
    http
      .expectOne('/api/customers/1001/addresses')
      .flush([{ ...HOME, primary: false }, { ...WORK, primary: true }]);
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
    expect(byTestId(fixture, 'customer-detail-address-dialog-district-select-option-1')).not.toBeNull();
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
    http.expectOne('/api/customers/1001/addresses').flush([HOME, { ...WORK, street: 'Güncel Cad.' }]);
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
    const ids = Array.from(grid?.querySelectorAll('[data-testid^="customer-detail-contact-value-"]') ?? []).map(
      (el) => el.getAttribute('data-testid'),
    );
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
    expect(TestBed.inject(CustomerFlashService).consume()).toBe(
      'UI-DETAIL-TOAST-CUSTOMER-DELETED',
    );
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
