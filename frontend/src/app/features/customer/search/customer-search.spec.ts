import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { provideCoreHttp } from '../../../core/http';
import { I18nService } from '../../../core/i18n';
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
    size: over.size ?? 20,
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
      providers: [provideCoreHttp(), provideHttpClientTesting()],
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
    expect(req.request.params.get('size')).toBe('20');
    req.flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-results-row-1001')).not.toBeNull();
    expect(byTestId(fixture, 'customer-search-results-row-1002')).not.toBeNull();
  });

  it('renders rows business-keyed with an INERT open-link (scope §2A.5) and a null-safe middle name', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI, ZEYNEP]));
    fixture.detectChanges();
    const link = byTestId(fixture, 'customer-search-results-row-1001-open-link');
    expect(link?.getAttribute('aria-disabled')).toBe('true');
    expect(link?.getAttribute('href')).toBeNull();
    const aliRow = byTestId(fixture, 'customer-search-results-row-1001');
    expect(aliRow?.textContent).not.toContain('null'); // middleName null → empty cell
    const zeynepRow = byTestId(fixture, 'customer-search-results-row-1002');
    expect(zeynepRow?.textContent).toContain('Nur');
  });

  // ---- AC-CUST-01-02 + filters -------------------------------------------

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

  it('renders the 501 filters disabled and unrepresentable in any request (FE-ADR-013)', () => {
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
    expect(account.disabled).toBe(true);
    expect(order.disabled).toBe(true);
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
      envelope([ALI], { totalElements: 137, totalPages: 7, number: 0, last: false }),
    );
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-pagination')?.textContent).toContain('1–20 of 137');

    (byTestId(fixture, 'customer-search-pagination-next') as HTMLButtonElement).click();
    fixture.detectChanges();
    const req = lastListRequest();
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    req.flush(envelope([ZEYNEP], { totalElements: 137, totalPages: 7, number: 1, last: false }));
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-search-pagination')?.textContent).toContain('21–40 of 137');
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

  it('Create new customer is rendered DISABLED — no fake behaviour (FE-ADR-013 §a)', () => {
    const fixture = render();
    lastListRequest().flush(envelope([ALI]));
    fixture.detectChanges();
    expect(
      (byTestId(fixture, 'customer-search-create-new-button') as HTMLButtonElement).disabled,
    ).toBe(true);
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
