import { Component, input } from '@angular/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { provideCoreHttp } from '../../../core/http';
import { AccountSection } from './account-section';
import { type BillingAddressOption } from './billing-address-option';
import { type AccountResponse } from '../model';

/**
 * Billing-account section spec (FR-ACCT-01..04, KR-11, K-8). House style:
 * data-testid selection, rendered-copy assertions, HTTP faked at the boundary
 * (real store + api service + core interceptors run).
 */
const ACTIVE_A: AccountResponse = {
  accountNumber: '1261000010',
  customerNumber: 1001,
  accountName: 'Ali Billing',
  accountTypeCode: '224',
  accountTypeName: 'Billing Account',
  billingAddressId: 1,
  accountStatus: 'Active',
};
const ACTIVE_B: AccountResponse = {
  ...ACTIVE_A,
  accountNumber: '1261000028',
  accountName: 'Second Billing',
  billingAddressId: 2,
};
const PASSIVE: AccountResponse = {
  ...ACTIVE_A,
  accountNumber: '1261000036',
  accountName: 'Old Billing',
  accountStatus: 'Passive',
};

const OPTIONS: readonly BillingAddressOption[] = [
  { addressId: 1, label: 'Kadıköy, Istanbul · Bağdat Cad. 12' },
  { addressId: 2, label: 'Çankaya, Ankara · Yeni Cad. 7' },
];

function apiError(status: number, messageKey: string) {
  return {
    body: { timestamp: '', status, error: 'x', messageKey, message: 'raw', path: '/api/accounts' },
    opts: { status, statusText: 'Error' },
  };
}

@Component({
  imports: [AccountSection],
  template: `
    <app-account-section [customerNumber]="customerNumber()" [addressOptions]="options()" />
  `,
})
class Host {
  readonly customerNumber = input<number>(1001);
  readonly options = input<readonly BillingAddressOption[]>(OPTIONS);
}

describe('AccountSection', () => {
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
    fixture.detectChanges(); // dialog-open effects → CVA signals → DOM
  }

  function type(fixture: ComponentFixture<Host>, testId: string, value: string): void {
    const inputEl = byTestId(fixture, testId) as HTMLInputElement;
    inputEl.value = value;
    inputEl.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function flushList(fixture: ComponentFixture<Host>, rows: readonly AccountResponse[]): void {
    const request = http.expectOne((r) => r.url === '/api/accounts');
    expect(request.request.params.get('customerId')).toBe('1001');
    request.flush([...rows]);
    fixture.detectChanges();
  }

  // ---- FR-ACCT-01: list ----------------------------------------------------

  it('lists accounts in SERVER order — Passive rows stay visible (AC-ACCT-01-03/04)', () => {
    const fixture = render();
    flushList(fixture, [ACTIVE_A, ACTIVE_B, PASSIVE]);
    const rowIds = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr[data-testid]'),
    ).map((row) => row.getAttribute('data-testid'));
    expect(rowIds).toEqual([
      'customer-detail-account-row-1261000010',
      'customer-detail-account-row-1261000028',
      'customer-detail-account-row-1261000036',
    ]); // exactly as received — never re-sorted
    expect(byTestId(fixture, 'customer-detail-account-row-1261000036-status')?.textContent).toContain(
      'Passive',
    );
    expect(byTestId(fixture, 'customer-detail-account-row-1261000010-status')?.textContent).toContain(
      'Active',
    );
  });

  it('shows the i18n empty state on 200 [] (customer with no billing accounts)', () => {
    const fixture = render();
    flushList(fixture, []);
    const empty = byTestId(fixture, 'customer-detail-account-empty-state');
    expect(empty?.textContent).toContain('No billing accounts');
    expect(byTestId(fixture, 'customer-detail-account-table')).toBeNull();
  });

  it('surfaces a list failure as a banner whose Retry re-issues the read', () => {
    const fixture = render();
    const down = apiError(503, 'MSG-SERVICE-UNAVAILABLE');
    http.expectOne((r) => r.url === '/api/accounts').flush(down.body, down.opts);
    fixture.detectChanges();
    const banner = byTestId(fixture, 'customer-detail-account-error-banner');
    expect(banner?.textContent).toContain('temporarily unavailable');
    expect(banner?.textContent).not.toContain('raw');
    click(fixture, 'customer-detail-account-retry-button');
    flushList(fixture, [ACTIVE_A]);
    expect(byTestId(fixture, 'customer-detail-account-row-1261000010')).not.toBeNull();
  });

  // ---- FR-ACCT-02: create --------------------------------------------------

  it('create POSTs EXACTLY {customerId, accountName, addressId} — no type, nothing else (K-8)', () => {
    const fixture = render();
    flushList(fixture, []);
    click(fixture, 'customer-detail-account-create-button');
    const save = byTestId(
      fixture,
      'customer-detail-account-dialog-save-button',
    ) as HTMLButtonElement;
    expect(save.disabled).toBe(true); // both fields required
    type(fixture, 'customer-detail-account-dialog-name-input', 'Ali Billing');
    click(fixture, 'customer-detail-account-dialog-address-select');
    click(fixture, 'customer-detail-account-dialog-address-select-option-1');
    expect(save.disabled).toBe(false);
    click(fixture, 'customer-detail-account-dialog-save-button');
    const post = http.expectOne('/api/accounts');
    expect(post.request.method).toBe('POST');
    expect(Object.keys(post.request.body as object).sort()).toEqual([
      'accountName',
      'addressId',
      'customerId',
    ]);
    expect(post.request.body).toEqual({
      customerId: 1001,
      accountName: 'Ali Billing',
      addressId: 1,
    });
    post.flush(ACTIVE_A, { status: 201, statusText: 'Created' });
    flushList(fixture, [ACTIVE_A]); // store reloads after the mutation
    expect(byTestId(fixture, 'customer-detail-account-dialog')).toBeNull();
    expect(byTestId(fixture, 'customer-detail-account-toast')?.textContent).toContain(
      'Billing account created successfully.',
    );
  });

  // ---- FR-ACCT-03 + KR-11: update ------------------------------------------

  it('edit shows the KR-11 number as TEXT (no control) and PUTs {accountName, addressId} only', () => {
    const fixture = render();
    flushList(fixture, [ACTIVE_A]);
    click(fixture, 'customer-detail-account-row-1261000010-edit');
    // KR-11: the number is display-only — visible as text, absent from every input.
    expect(byTestId(fixture, 'customer-detail-account-dialog-number-value')?.textContent).toContain(
      '1261000010',
    );
    const inputValues = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('input'),
    ).map((el) => (el as HTMLInputElement).value);
    expect(inputValues).not.toContain('1261000010');
    expect(byTestId(fixture, 'customer-detail-account-dialog-name-input')).not.toBeNull();

    type(fixture, 'customer-detail-account-dialog-name-input', 'Renamed Billing');
    click(fixture, 'customer-detail-account-dialog-save-button');
    const put = http.expectOne('/api/accounts/1261000010');
    expect(put.request.method).toBe('PUT');
    expect(Object.keys(put.request.body as object).sort()).toEqual(['accountName', 'addressId']);
    expect(put.request.body).toEqual({ accountName: 'Renamed Billing', addressId: 1 });
    put.flush({ ...ACTIVE_A, accountName: 'Renamed Billing' });
    flushList(fixture, [{ ...ACTIVE_A, accountName: 'Renamed Billing' }]);
    expect(byTestId(fixture, 'customer-detail-account-row-1261000010')?.textContent).toContain(
      'Renamed Billing',
    );
    expect(byTestId(fixture, 'customer-detail-account-toast')?.textContent).toContain(
      'Billing account updated successfully.',
    );
  });

  it('updating a Passive account surfaces the backend 409 MSG-ACCT-NOT-ACTIVE — not pre-checked', () => {
    const fixture = render();
    flushList(fixture, [PASSIVE]);
    // The edit action stays ENABLED on a Passive row — the backend answers.
    click(fixture, 'customer-detail-account-row-1261000036-edit');
    type(fixture, 'customer-detail-account-dialog-name-input', 'Should fail');
    click(fixture, 'customer-detail-account-dialog-save-button');
    const guard = apiError(409, 'MSG-ACCT-NOT-ACTIVE');
    http.expectOne('/api/accounts/1261000036').flush(guard.body, guard.opts);
    fixture.detectChanges();
    const banner = byTestId(fixture, 'customer-detail-account-dialog-error-banner');
    expect(banner?.textContent).toContain('passive and cannot be modified');
    expect(banner?.textContent).not.toContain('raw');
  });

  // ---- FR-ACCT-04: delete = passivation ------------------------------------

  it('delete confirms, DELETEs, and the row STAYS listed as Passive (AC-ACCT-04-02)', () => {
    const fixture = render();
    flushList(fixture, [ACTIVE_A, ACTIVE_B]);
    click(fixture, 'customer-detail-account-row-1261000028-delete');
    expect(byTestId(fixture, 'customer-detail-account-delete-dialog')?.textContent).toContain(
      'Are you sure to delete this billing account?',
    );
    click(fixture, 'customer-detail-account-delete-dialog-confirm-button');
    const request = http.expectOne('/api/accounts/1261000028');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: '' });
    // Reload: the server re-orders — the passivated row moves after Actives
    // but REMAINS in the list.
    flushList(fixture, [ACTIVE_A, { ...ACTIVE_B, accountStatus: 'Passive' }]);
    expect(byTestId(fixture, 'customer-detail-account-delete-dialog')).toBeNull();
    expect(byTestId(fixture, 'customer-detail-account-row-1261000028')).not.toBeNull();
    expect(
      byTestId(fixture, 'customer-detail-account-row-1261000028-status')?.textContent,
    ).toContain('Passive');
    expect(byTestId(fixture, 'customer-detail-account-toast')?.textContent).toContain(
      'Billing account deleted successfully.',
    );
  });

  it('AC-ACCT-04-03: delete blocked by active products (409) shows the error state', () => {
    const fixture = render();
    flushList(fixture, [ACTIVE_A]);
    click(fixture, 'customer-detail-account-row-1261000010-delete');
    click(fixture, 'customer-detail-account-delete-dialog-confirm-button');
    const guard = apiError(409, 'MSG-ACCT-HAS-PRODUCTS');
    http.expectOne('/api/accounts/1261000010').flush(guard.body, guard.opts);
    fixture.detectChanges();
    const error = byTestId(fixture, 'customer-detail-account-delete-dialog-error');
    expect(error?.textContent).toContain('active product');
    expect(byTestId(fixture, 'customer-detail-account-delete-dialog-confirm-button')).toBeNull();
    click(fixture, 'customer-detail-account-delete-dialog-close-button');
    expect(byTestId(fixture, 'customer-detail-account-delete-dialog')).toBeNull();
  });

  it('re-deleting a Passive account surfaces 409 MSG-ACCT-NOT-ACTIVE in the dialog', () => {
    const fixture = render();
    flushList(fixture, [PASSIVE]);
    click(fixture, 'customer-detail-account-row-1261000036-delete');
    click(fixture, 'customer-detail-account-delete-dialog-confirm-button');
    const guard = apiError(409, 'MSG-ACCT-NOT-ACTIVE');
    http.expectOne('/api/accounts/1261000036').flush(guard.body, guard.opts);
    fixture.detectChanges();
    expect(
      byTestId(fixture, 'customer-detail-account-delete-dialog-error')?.textContent,
    ).toContain('passive');
  });
});
