import { Component, input, signal } from '@angular/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { provideCoreHttp } from '../../../core/http';
import { AccountSection } from './account-section';
import { type BillingAddressOption, type NewBillingAddress } from './billing-address-option';
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
    <app-account-section
      [customerNumber]="customerNumber()"
      [addressOptions]="options()"
      [addressBusy]="addressBusy()"
      [addressErrorText]="addressErrorText()"
      [createdAddressId]="createdAddressId()"
      (newAddressRequested)="requested.push($event)"
    />
  `,
})
class Host {
  readonly customerNumber = input<number>(1001);
  readonly options = input<readonly BillingAddressOption[]>(OPTIONS);
  /** BUG-1: the Host plays Customer Info — it OWNS address creation. */
  readonly requested: NewBillingAddress[] = [];
  readonly addressBusy = signal(false);
  readonly addressErrorText = signal<string | null>(null);
  readonly createdAddressId = signal<number | null>(null);
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

  // ---- BUG-1: inline "Add new address" panel (scope §4.30) -----------------

  const PANEL = 'customer-detail-account-dialog-new-address';

  /** Opens the create dialog and expands the inline panel, serving the cascade
   *  reads the panel issues through `core/lookup`. */
  function openAddressPanel(fixture: ComponentFixture<Host>): void {
    click(fixture, 'customer-detail-account-create-button');
    click(fixture, 'customer-detail-account-dialog-add-address-button');
    http.expectOne('/api/cities').flush([
      { cityId: 34, name: 'Istanbul' },
      { cityId: 6, name: 'Ankara' },
    ]);
    fixture.detectChanges();
  }

  /** Fills all five fields; picking the city also flushes its districts. */
  function fillAddressPanel(fixture: ComponentFixture<Host>): void {
    click(fixture, `${PANEL}-city-select`);
    click(fixture, `${PANEL}-city-select-option-34`);
    http.expectOne('/api/cities/34/districts').flush([{ districtId: 340, name: 'Kadıköy' }]);
    fixture.detectChanges();
    click(fixture, `${PANEL}-district-select`);
    click(fixture, `${PANEL}-district-select-option-340`);
    type(fixture, `${PANEL}-street-input`, ' Bağdat Cad. ');
    type(fixture, `${PANEL}-house-no-input`, ' 12 ');
    type(fixture, `${PANEL}-description-input`, ' Home ');
  }

  // The mock's modal body is a single stacked column, and opening the address
  // list must neither be clipped by the dialog nor RESIZE it: the mock's own
  // dialog is `overflow:visible` with an overlaying panel (scope §4.32, which
  // supersedes the in-flow panel of §4.30 EK).
  it('stacks the dialog fields and shows EVERY address without clipping the list', () => {
    const manyAddresses = Array.from({ length: 9 }, (_, i) => ({
      addressId: i + 1,
      label: `Address ${i + 1}`,
    }));
    const fixture = render();
    fixture.componentRef.setInput('options', manyAddresses);
    flushList(fixture, []);
    click(fixture, 'customer-detail-account-create-button');

    // Stacked, full-width fields — not the former two-column grid.
    const form = byTestId(fixture, 'customer-detail-account-dialog-form') as HTMLElement;
    expect(form.className).toContain('flex-col');
    expect(form.className).not.toContain('grid-cols-2');

    // The dialog declares the mock's shape: overflow visible, no height cap —
    // so the panel is free to hang outside it instead of being clipped.
    const dialog = byTestId(fixture, 'customer-detail-account-dialog') as HTMLElement;
    expect(dialog.className).not.toContain('max-h-full');
    expect(dialog.querySelector(':scope > .overflow-visible')).not.toBeNull();
    expect(dialog.querySelector(':scope > .overflow-y-auto')).toBeNull();

    click(fixture, 'customer-detail-account-dialog-address-select');
    const panel = byTestId(
      fixture,
      'customer-detail-account-dialog-address-select-panel',
    ) as HTMLElement;
    // OVERLAYING ⇒ the open list cannot change the size of the dialog.
    expect(panel.className).toContain('absolute');
    // All nine addresses are there — the client caps nothing.
    expect(panel.querySelectorAll('[role="option"]').length).toBe(9);
  });

  it('offers "Add new address" under the Billing address field and expands it INLINE', () => {
    const fixture = render();
    flushList(fixture, []);
    click(fixture, 'customer-detail-account-create-button');
    // Closed: only the trigger, no panel (mock's accAddrClosed / accAddrOpen).
    expect(byTestId(fixture, 'customer-detail-account-dialog-add-address-button')).not.toBeNull();
    expect(byTestId(fixture, PANEL)).toBeNull();

    click(fixture, 'customer-detail-account-dialog-add-address-button');
    http.expectOne('/api/cities').flush([{ cityId: 34, name: 'Istanbul' }]);
    fixture.detectChanges();

    // Open: the panel, and NO second dialog — one modal only (FE-ADR-011).
    expect(byTestId(fixture, PANEL)).not.toBeNull();
    expect(byTestId(fixture, 'customer-detail-account-dialog-add-address-button')).toBeNull();
    expect(
      (fixture.nativeElement as HTMLElement).querySelectorAll('[role="dialog"]').length,
    ).toBe(1);
    expect(byTestId(fixture, PANEL)?.textContent).toContain('New address');
  });

  it('disables the account Save while the panel is open, and District until a city is picked', () => {
    const fixture = render();
    flushList(fixture, []);
    click(fixture, 'customer-detail-account-create-button');
    type(fixture, 'customer-detail-account-dialog-name-input', 'Ali Billing');
    click(fixture, 'customer-detail-account-dialog-address-select');
    click(fixture, 'customer-detail-account-dialog-address-select-option-1');
    const save = byTestId(
      fixture,
      'customer-detail-account-dialog-save-button',
    ) as HTMLButtonElement;
    expect(save.disabled).toBe(false);

    click(fixture, 'customer-detail-account-dialog-add-address-button');
    http.expectOne('/api/cities').flush([{ cityId: 34, name: 'Istanbul' }]);
    fixture.detectChanges();
    // A half-typed address must not be lost to an account save (mock rule).
    expect(save.disabled).toBe(true);
    // Cascade: no city yet ⇒ District disabled, Save address disabled.
    expect((byTestId(fixture, `${PANEL}-district-select`) as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect((byTestId(fixture, `${PANEL}-save-button`) as HTMLButtonElement).disabled).toBe(true);

    // Cancelling restores the account form untouched.
    click(fixture, `${PANEL}-cancel-button`);
    expect(byTestId(fixture, PANEL)).toBeNull();
    expect(save.disabled).toBe(false);
  });

  it('emits the trimmed draft UPWARD and calls no address API itself (FE-ADR-003)', () => {
    const fixture = render();
    flushList(fixture, []);
    openAddressPanel(fixture);
    fillAddressPanel(fixture);
    click(fixture, `${PANEL}-save-button`);

    // FR-ADDR-02 belongs to customer-service: this feature only asks.
    expect(fixture.componentInstance.requested).toEqual([
      {
        cityId: 34,
        districtId: 340,
        street: 'Bağdat Cad.',
        houseFlatNumber: '12',
        addressDescription: 'Home',
      },
    ]);
    http.expectNone((r) => r.url.includes('/addresses'));
  });

  it('selects the created address once the caller acknowledges it, and saves with that id', () => {
    const fixture = render();
    flushList(fixture, []);
    openAddressPanel(fixture);
    fillAddressPanel(fixture);
    click(fixture, `${PANEL}-save-button`);

    // The caller created it and hands back both the id and the new option.
    fixture.componentInstance.createdAddressId.set(3);
    fixture.componentRef.setInput('options', [
      ...OPTIONS,
      { addressId: 3, label: 'Kadıköy, Istanbul · Bağdat Cad. 12' },
    ]);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(byTestId(fixture, PANEL)).toBeNull(); // collapsed
    expect(
      byTestId(fixture, 'customer-detail-account-dialog-address-select')?.textContent,
    ).toContain('Bağdat Cad. 12'); // auto-selected

    type(fixture, 'customer-detail-account-dialog-name-input', 'Ali Billing');
    click(fixture, 'customer-detail-account-dialog-save-button');
    const post = http.expectOne('/api/accounts');
    expect(post.request.body).toEqual({
      customerId: 1001,
      accountName: 'Ali Billing',
      addressId: 3,
    });
    post.flush({ ...ACTIVE_A, billingAddressId: 3 });
    flushList(fixture, [{ ...ACTIVE_A, billingAddressId: 3 }]);
  });

  it('keeps the panel open and shows the caller failure when the create fails', () => {
    const fixture = render();
    flushList(fixture, []);
    openAddressPanel(fixture);
    fillAddressPanel(fixture);
    click(fixture, `${PANEL}-save-button`);

    fixture.componentInstance.addressErrorText.set('Service temporarily unavailable.');
    fixture.detectChanges();

    expect(byTestId(fixture, PANEL)).not.toBeNull(); // nothing was selected
    expect(byTestId(fixture, `${PANEL}-error`)?.textContent).toContain('temporarily unavailable');
    expect(
      (byTestId(fixture, 'customer-detail-account-dialog-save-button') as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });
});
