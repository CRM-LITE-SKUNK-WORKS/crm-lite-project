import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideCoreHttp } from '../../../core/http';
import { I18nService } from '../../../core/i18n';
import { CustomerFlashService } from '../state/customer-flash.service';
import type { CustomerDetailResponse } from '../model';
import { CustomerSearch } from './customer-search';

/**
 * Golden-path screen spec (customer-search-analysis §6 states). House style
 * (docs/frontend/testing-conventions.md): selection ONLY via data-testid,
 * copy assertions on RENDERED text so a leaked catalogue key fails, both
 * languages checked where it matters. HTTP is faked at the boundary
 * (HttpTestingController) — the real store + api service run.
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
const ZEYNEP: CustomerDetailResponse = {
  ...ALI,
  customerNumber: 1002,
  firstName: 'Zeynep',
  middleName: 'Nur',
  lastName: 'Demir',
  nationalityId: '23456789012',
};

interface EnvelopeOverrides {
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
  first?: boolean;
  last?: boolean;
}

function envelope(rows: CustomerDetailResponse[], over: EnvelopeOverrides = {}) {
  const totalPages = over.totalPages ?? (rows.length ? 1 : 0);
  const number = over.number ?? 0;
  return {
    content: rows,
    totalElements: over.totalElements ?? rows.length,
    totalPages,
    number,
    size: over.size ?? 15,
    first: over.first ?? number === 0,
    last: over.last ?? number >= totalPages - 1,
  };
}

describe('CustomerSearch', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [CustomerSearch],
      // The REAL core http wiring (normalize + auth interceptors) — the screen's
      // messageKey → i18n chain is part of what this spec proves (FE-ADR-008).
      providers: [provideCoreHttp(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(): ComponentFixture<CustomerSearch> {
    const fixture = TestBed.createComponent(CustomerSearch);
    fixture.detectChanges();
    return fixture;
  }

  function byTestId(fixture: ComponentFixture<CustomerSearch>, id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  function lastListRequest() {
    const requests = http.match((r) => r.url === '/api/customers');
    expect(requests.length).toBeGreaterThan(0);
    return requests[requests.length - 1];
  }

  function type(fixture: ComponentFixture<CustomerSearch>, testId: string, value: string): void {
    const input = byTestId(fixture, testId) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function clickSearch(fixture: ComponentFixture<CustomerSearch>): void {
    (byTestId(fixture, 'customer-search-submit-button') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  // ---- AC-CUST-01-00: browse mode on open --------------------------------

  it('opens in browse mode: criterion-less request with explicit page+size (KR-04)', () => {
    const fixture = render();
    const req = lastListRequest();
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('15');
    req.flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-results-row-1001')).not.toBeNull();
    expect(byTestId(fixture, 'customer-search-results-row-1002')).not.toBeNull();
  });

  it('renders rows business-keyed with a LIVE open-link to Customer Info (scope §2A.5 activated) and a null-safe middle name', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    const link = byTestId(fixture, 'customer-search-results-row-1001-open-link');
    expect(link?.tagName).toBe('A');
    expect(link?.getAttribute('href')).toBe('/customers/1001'); // Detail route shipped
    expect(link?.getAttribute('aria-disabled')).toBeNull();
    const aliRow = byTestId(fixture, 'customer-search-results-row-1001');
    expect(aliRow?.textContent).not.toContain('null'); // middleName null → empty cell
    const zeynepRow = byTestId(fixture, 'customer-search-results-row-1002');
    expect(zeynepRow?.textContent).toContain('Nur');
  });

  it('shows a one-shot flash toast (e.g. after delete) and consumes it exactly once', () => {
    TestBed.inject(CustomerFlashService).set('UI-DETAIL-TOAST-CUSTOMER-DELETED');
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    const toast = byTestId(fixture, 'customer-search-toast');
    expect(toast?.getAttribute('role')).toBe('status');
    expect(toast?.textContent).toContain('Customer deleted successfully.');
    (byTestId(fixture, 'customer-search-toast-dismiss-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-toast')).toBeNull();
    expect(TestBed.inject(CustomerFlashService).consume()).toBeNull(); // already consumed
  });

  // ---- browse vs. search heading (ADR-005 modes) --------------------------

  it('names the card "All customers" while browsing and "Search results" only after a filter is applied', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    const heading = () => byTestId(fixture, 'customer-search-results-heading')?.textContent?.trim();
    expect(heading()).toBe('All customers'); // browse mode: nothing was searched
    type(fixture, 'customer-search-filter-name-input', 'Nur');
    clickSearch(fixture);
    lastListRequest().flush(envelope([ZEYNEP]));
    fixture.detectChanges();
    expect(heading()).toBe('Search results');
    // Clearing the filter returns to browse mode — and to the browse heading.
    type(fixture, 'customer-search-filter-name-input', '');
    lastListRequest().flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    expect(heading()).toBe('All customers');
  });

  it('translates the browse heading (TR) — no leaked catalogue key', () => {
    TestBed.inject(I18nService).setLanguage('tr');
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-results-heading')?.textContent?.trim()).toBe(
      'Tüm müşteriler',
    );
  });

  // ---- AC-CUST-01-02 + filters -------------------------------------------

  it('all five numeric filters accept digits ONLY — letters and symbols never enter the field or the request', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    const numericFilters = [
      'customer-search-filter-id-number-input',
      'customer-search-filter-customer-id-input',
      'customer-search-filter-account-number-input',
      'customer-search-filter-gsm-number-input',
      'customer-search-filter-order-number-input',
    ];
    for (const testId of numericFilters) {
      type(fixture, testId, 'a1b2-c3');
      expect((byTestId(fixture, testId) as HTMLInputElement).value).toBe('123');
      type(fixture, testId, '');
    }
    // and the sanitized value is what the request carries
    type(fixture, 'customer-search-filter-customer-id-input', '');
    type(fixture, 'customer-search-filter-gsm-number-input', '');
    type(fixture, 'customer-search-filter-id-number-input', '1234x5678901');
    clickSearch(fixture);
    const req = lastListRequest();
    expect(req.request.params.get('nationalityId')).toBe('12345678901');
    req.flush(envelope([ALI]));
  });

  it('Search is disabled while every filter is empty, enabled once one is filled', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    const submit = byTestId(fixture, 'customer-search-submit-button') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    type(fixture, 'customer-search-filter-name-input', 'Ali');
    expect(submit.disabled).toBe(false);
  });

  it('sends exactly the filled criteria on Search and resets to page 0 (KR-01 params only)', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-name-input', 'Nur');
    type(fixture, 'customer-search-filter-last-name-input', 'Demir');
    clickSearch(fixture);
    const req = lastListRequest();
    expect(req.request.params.get('firstName')).toBe('Nur'); // "Name" box → firstName
    expect(req.request.params.get('lastName')).toBe('Demir');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.has('nationalityId')).toBe(false);
    expect(req.request.params.has('accountNumber')).toBe(false);
    expect(req.request.params.has('orderNumber')).toBe(false);
    expect(req.request.params.has('sort')).toBe(false); // KR-04: fixed server sort
    req.flush(envelope([ZEYNEP]));
  });

  // ---- KR-02 Account / Order Number filters (scope §1.7c — 501 gate removed) ----

  it('renders the Account and Order Number filters ENABLED, with no deferred hint left', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    const account = byTestId(
      fixture,
      'customer-search-filter-account-number-input',
    ) as HTMLInputElement;
    const order = byTestId(
      fixture,
      'customer-search-filter-order-number-input',
    ) as HTMLInputElement;
    expect(account.disabled).toBe(false);
    expect(order.disabled).toBe(false);
    // The old "Available when the account/order module is released." helper is gone.
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).not.toContain('Available when the account/order module');
  });

  it('Account Number alone enables Search and is sent verbatim as an exact criterion (KR-01)', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    const submit = byTestId(fixture, 'customer-search-submit-button') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    type(fixture, 'customer-search-filter-account-number-input', '1261000010');
    expect(submit.disabled).toBe(false);

    clickSearch(fixture);
    const req = lastListRequest();
    expect(req.request.params.get('accountNumber')).toBe('1261000010');
    expect(req.request.params.has('orderNumber')).toBe(false);
    expect(req.request.params.get('page')).toBe('0');
    // The screen sends the criterion and renders whatever the server resolved —
    // it never filters the already-loaded table (KR-02 is a SERVER rule).
    req.flush(envelope([ALI]));
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-results-row-1001')).not.toBeNull();
    expect(byTestId(fixture, 'customer-search-results-row-1002')).toBeNull();
  });

  it('Order Number alone enables Search and is sent verbatim', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    const submit = byTestId(fixture, 'customer-search-submit-button') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    type(fixture, 'customer-search-filter-order-number-input', '1262000018');
    expect(submit.disabled).toBe(false);

    clickSearch(fixture);
    const req = lastListRequest();
    expect(req.request.params.get('orderNumber')).toBe('1262000018');
    expect(req.request.params.has('accountNumber')).toBe(false);
    req.flush(envelope([ZEYNEP]));
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-results-row-1002')).not.toBeNull();
  });

  it('keeps Account/Order numbers as STRINGS: a leading zero survives typing and the request', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-account-number-input', '0261000010');
    type(fixture, 'customer-search-filter-order-number-input', '0262000018');
    expect(
      (byTestId(fixture, 'customer-search-filter-account-number-input') as HTMLInputElement).value,
    ).toBe('0261000010');
    clickSearch(fixture);
    const req = lastListRequest();
    expect(req.request.params.get('accountNumber')).toBe('0261000010'); // not "261000010"
    expect(req.request.params.get('orderNumber')).toBe('0262000018');
    req.flush(envelope([ALI]));
  });

  it('sanitizes a PASTED non-digit value in both new filters (the shared digitsOnly pattern)', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    for (const testId of [
      'customer-search-filter-account-number-input',
      'customer-search-filter-order-number-input',
    ]) {
      const input = byTestId(fixture, testId) as HTMLInputElement;
      // A paste reaches the control as a plain `input` event with the full text —
      // the same path `digitsOnly` sanitizes, which is why `inputMode` (a mere
      // keyboard hint) could never have covered this.
      input.value = '12-34 AB e+5.6';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(input.value).toBe('123456');
    }
    clickSearch(fixture);
    const req = lastListRequest();
    expect(req.request.params.get('accountNumber')).toBe('123456');
    expect(req.request.params.get('orderNumber')).toBe('123456');
    req.flush(envelope([]));
  });

  it('sends Account/Order Number ALONGSIDE the existing criteria (KR-01 OR groups, server-side)', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-account-number-input', '1261000010');
    type(fixture, 'customer-search-filter-name-input', 'Zeynep');
    clickSearch(fixture);
    const req = lastListRequest();
    expect(req.request.params.get('accountNumber')).toBe('1261000010');
    expect(req.request.params.get('firstName')).toBe('Zeynep');
    expect(req.request.params.has('sort')).toBe(false); // KR-04 fixed server sort
    req.flush(envelope([ALI, ZEYNEP]));
  });

  it('Clear resets the two new filters and returns to browse mode', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-account-number-input', '1261000010');
    type(fixture, 'customer-search-filter-order-number-input', '1262000018');
    clickSearch(fixture);
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();

    (byTestId(fixture, 'customer-search-clear-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(
      (byTestId(fixture, 'customer-search-filter-account-number-input') as HTMLInputElement).value,
    ).toBe('');
    expect(
      (byTestId(fixture, 'customer-search-filter-order-number-input') as HTMLInputElement).value,
    ).toBe('');
    const req = lastListRequest();
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']); // browse again
    req.flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    expect((byTestId(fixture, 'customer-search-submit-button') as HTMLButtonElement).disabled).toBe(
      true,
    );
  });

  it('an unmatched Account Number shows the MSG-CUST-NOT-FOUND empty state (AC-CUST-01-06)', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-account-number-input', '9999999999');
    clickSearch(fixture);
    lastListRequest().flush(envelope([]));
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-empty-state')?.textContent).toContain(
      'No customer found',
    );
    expect(byTestId(fixture, 'customer-search-browse-empty-state')).toBeNull();
  });

  it('binds a 400 validationErrors entry for accountNumber onto that field (backend stays authority)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-account-number-input', '1261000010');
    clickSearch(fixture);
    lastListRequest().flush(
      {
        timestamp: '',
        status: 400,
        error: 'Bad Request',
        messageKey: 'MSG-VALIDATION-ERROR',
        message: 'raw',
        path: '/api/customers',
        validationErrors: { accountNumber: 'must contain digits only' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    expect(
      byTestId(fixture, 'customer-search-filter-account-number-error')?.textContent,
    ).toContain('Please check this field.');
    warn.mockRestore();
  });

  it('fully clearing an applied filter re-runs the search without pressing Search (mock §6.2)', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-name-input', 'Nur');
    clickSearch(fixture);
    lastListRequest().flush(envelope([ZEYNEP]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-name-input', ''); // cleared → filter drops
    const req = lastListRequest();
    expect(req.request.params.has('firstName')).toBe(false); // back to browse
    req.flush(envelope([ALI, ZEYNEP]));
  });

  // ---- client-side UX validation (mock §6.2; backend stays authority) -----

  it('blocks Search with a translated field error for a non-11-digit ID, cleared on typing', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    const before = http.match((r) => r.url === '/api/customers').length;

    type(fixture, 'customer-search-filter-id-number-input', '123');
    clickSearch(fixture);
    expect(http.match((r) => r.url === '/api/customers').length).toBe(before); // no request
    const errorRow = byTestId(fixture, 'customer-search-filter-id-number-error');
    expect(errorRow?.textContent).toContain('ID number must be 11 digits.');

    type(fixture, 'customer-search-filter-id-number-input', '1234'); // typing hides it
    expect(byTestId(fixture, 'customer-search-filter-id-number-error')?.textContent?.trim()).toBe(
      '',
    );
  });

  it('blocks Search when GSM does not start with 05', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-gsm-number-input', '02121112233');
    clickSearch(fixture);
    expect(byTestId(fixture, 'customer-search-filter-gsm-number-error')?.textContent).toContain(
      'GSM number must start with 05.',
    );
  });

  // ---- empty states (3 vs 4) ---------------------------------------------

  it('search with zero hits shows the MSG-CUST-NOT-FOUND empty state (state 4)', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-name-input', 'Yok');
    clickSearch(fixture);
    lastListRequest().flush(envelope([]));
    fixture.detectChanges();
    const empty = byTestId(fixture, 'customer-search-empty-state');
    expect(empty?.textContent).toContain('No customer found');
    expect(byTestId(fixture, 'customer-search-browse-empty-state')).toBeNull();
    expect(byTestId(fixture, 'customer-search-pagination')).toBeNull();
  });

  it('browse with zero customers shows the DIFFERENT browse-empty state (state 3)', () => {
    const fixture = render();
    lastListRequest().flush(envelope([]));
    fixture.detectChanges();
    const empty = byTestId(fixture, 'customer-search-browse-empty-state');
    expect(empty?.textContent).toContain('No customers yet');
    expect(byTestId(fixture, 'customer-search-empty-state')).toBeNull();
  });

  // ---- error states -------------------------------------------------------

  it('503 shows the translated messageKey in a banner, keeps the table, and Retry re-issues', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI], { totalPages: 2, last: false, totalElements: 21 }));
    fixture.detectChanges();

    (byTestId(fixture, 'customer-search-pagination-next') as HTMLButtonElement).click();
    fixture.detectChanges();
    lastListRequest().flush(
      {
        timestamp: '',
        status: 503,
        error: 'x',
        messageKey: 'MSG-SERVICE-UNAVAILABLE',
        message: 'raw',
        path: '/api/customers',
      },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();

    const banner = byTestId(fixture, 'customer-search-error-banner');
    expect(banner?.textContent).toContain('The service is temporarily unavailable.');
    expect(banner?.textContent).not.toContain('raw'); // backend text never rendered
    expect(byTestId(fixture, 'customer-search-results-row-1001')).not.toBeNull(); // last good data

    (byTestId(fixture, 'customer-search-retry-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    lastListRequest().flush(envelope([ALI], { number: 1, totalPages: 2, totalElements: 21 }));
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-error-banner')).toBeNull();
  });

  it('400 validationErrors land on the matching field via the core chain (state 6)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    type(fixture, 'customer-search-filter-customer-id-input', '1001');
    clickSearch(fixture);
    lastListRequest().flush(
      {
        timestamp: '',
        status: 400,
        error: 'Bad Request',
        messageKey: 'MSG-VALIDATION-ERROR',
        message: 'raw',
        path: '/api/customers',
        validationErrors: { customerId: 'customerId must be numeric' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-error-banner')?.textContent).toContain(
      'Some fields are invalid.',
    );
    expect(byTestId(fixture, 'customer-search-filter-customer-id-error')?.textContent).toContain(
      'Please check this field.',
    );
    warn.mockRestore();
  });

  // ---- pagination (KR-04) -------------------------------------------------

  it('next page keeps criteria, sends the new page, and shows the range text', () => {
    const fixture = render();
    lastListRequest().flush(
      envelope([ALI], { totalElements: 137, totalPages: 10, number: 0, last: false }),
    );
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-pagination')?.textContent).toContain('1–15 of 137');

    (byTestId(fixture, 'customer-search-pagination-next') as HTMLButtonElement).click();
    fixture.detectChanges();
    const req = lastListRequest();
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('15');
    req.flush(envelope([ZEYNEP], { totalElements: 137, totalPages: 10, number: 1, last: false }));
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-pagination')?.textContent).toContain('16–30 of 137');
  });

  it('truncates page buttons per the mock rule (1 … around current … last)', () => {
    const fixture = render();
    lastListRequest().flush(
      envelope([ALI], { totalElements: 137, totalPages: 7, number: 0, last: false }),
    );
    fixture.detectChanges();
    // 7 pages → all shown; current is the STORE's page (client state), page 1
    expect(byTestId(fixture, 'customer-search-pagination-page-7')).not.toBeNull();
    expect(
      byTestId(fixture, 'customer-search-pagination-page-1')?.getAttribute('aria-current'),
    ).toBe('page');

    // Navigate to page 5 of a 12-page result → 1 … 4 5 6 … 12
    (byTestId(fixture, 'customer-search-pagination-page-5') as HTMLButtonElement).click();
    fixture.detectChanges();
    const req = lastListRequest();
    expect(req.request.params.get('page')).toBe('4'); // zero-based on the wire
    req.flush(
      envelope([ALI], { totalElements: 231, totalPages: 12, number: 4, first: false, last: false }),
    );
    fixture.detectChanges();
    expect(
      byTestId(fixture, 'customer-search-pagination-page-5')?.getAttribute('aria-current'),
    ).toBe('page');
    expect(byTestId(fixture, 'customer-search-pagination-page-1')).not.toBeNull();
    expect(byTestId(fixture, 'customer-search-pagination-page-4')).not.toBeNull();
    expect(byTestId(fixture, 'customer-search-pagination-page-6')).not.toBeNull();
    expect(byTestId(fixture, 'customer-search-pagination-page-12')).not.toBeNull();
    expect(byTestId(fixture, 'customer-search-pagination-page-2')).toBeNull();
    expect(byTestId(fixture, 'customer-search-pagination-page-11')).toBeNull();
  });

  it('changing per-page sends the explicit size and resets to page 0 (KR-04, scope §2.4)', () => {
    const fixture = render();
    lastListRequest().flush(
      envelope([ALI], { totalElements: 137, totalPages: 7, number: 2, first: false, last: false }),
    );
    fixture.detectChanges();
    (byTestId(fixture, 'customer-search-page-size-select') as HTMLButtonElement).click();
    fixture.detectChanges();
    byTestId(fixture, 'customer-search-page-size-select-option-50')?.click();
    fixture.detectChanges();
    const req = lastListRequest();
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(envelope([ALI], { totalElements: 137, totalPages: 3, size: 50 }));
  });

  // ---- misc contract ------------------------------------------------------

  it('Create new customer is LIVE since the wizard shipped and navigates to /customers/new', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    const navigate = vi
      .spyOn(TestBed.inject(Router), 'navigate')
      .mockResolvedValue(true);
    const button = byTestId(fixture, 'customer-search-create-new-button') as HTMLButtonElement;
    expect(button.disabled).toBe(false);
    button.click();
    expect(navigate).toHaveBeenCalledWith(['/customers', 'new']);
  });

  it('switches language instantly and never leaks a raw catalogue key (AC-LANG-01-02)', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Search customer');
    expect(root.textContent).not.toContain('UI-SEARCH');
    TestBed.inject(I18nService).setLanguage('tr');
    fixture.detectChanges();
    expect(root.textContent).toContain('Müşteri ara');
    expect(root.textContent).not.toContain('UI-SEARCH');
  });
});
