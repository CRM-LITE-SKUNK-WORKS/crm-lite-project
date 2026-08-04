import { Injectable, computed, inject, signal } from '@angular/core';
import { type Observable, tap } from 'rxjs';
import { type ApiError, isApiError } from '../../../core/http';
import { CustomerApiService } from '../data/customer-api.service';
import {
  type AddressRequest,
  type AddressResponse,
  type ContactMediumRequest,
  type ContactMediumResponse,
  type CustomerDetailResponse,
  type UpdateCustomerRequest,
} from '../model';
import { type RequestStatus } from './customer-list.store';

/**
 * Screen-scoped state for Customer Info (FE-ADR-006 §1: provided by the detail
 * route, never `providedIn:'root'`).
 *
 * The detail endpoint carries NO addresses/contact (FRONTEND_BRAIN §4), so the
 * screen makes THREE independent reads — demographic / addresses / contact —
 * each with its own status + error, so one failing section never blanks the
 * others.
 *
 * Reads follow the CustomerListStore pattern (store subscribes, exposes
 * signals, race-guarded). MUTATIONS return the observable to the calling
 * dialog/screen (which owns the submit UX: busy state, field errors via
 * `matchFieldErrors`, closing on success) while the store `tap`s the success
 * to keep its signals fresh — one state owner, one request owner.
 *
 * Address mutations REFRESH the list instead of patching it locally: the
 * set-primary PATCH returns only the promoted address (the demoted ex-primary
 * does not come back — data-layer note), and add/delete can change primacy
 * server-side (first address auto-primary). The server is the authority on
 * the resulting list (FE-ADR-013 §e).
 *
 * No error text anywhere: `ApiError.messageKey` flows out and the screen
 * resolves it through i18n (FE-ADR-008 §5).
 */
@Injectable()
export class CustomerDetailStore {
  private readonly api = inject(CustomerApiService);

  private readonly _customerNumber = signal<number | null>(null);

  private readonly _customer = signal<CustomerDetailResponse | null>(null);
  private readonly _detailStatus = signal<RequestStatus>('idle');
  private readonly _detailError = signal<ApiError | null>(null);

  private readonly _addresses = signal<readonly AddressResponse[]>([]);
  private readonly _addressesStatus = signal<RequestStatus>('idle');
  private readonly _addressesError = signal<ApiError | null>(null);

  private readonly _contact = signal<ContactMediumResponse | null>(null);
  private readonly _contactStatus = signal<RequestStatus>('idle');
  private readonly _contactError = signal<ApiError | null>(null);

  /** Monotonic tokens per section — a stale response can never win a race. */
  private detailSeq = 0;
  private addressesSeq = 0;
  private contactSeq = 0;

  // --- read-only state ------------------------------------------------------
  readonly customerNumber = this._customerNumber.asReadonly();
  readonly customer = this._customer.asReadonly();
  readonly detailStatus = this._detailStatus.asReadonly();
  readonly detailError = this._detailError.asReadonly();
  /** 404 on the detail read — the screen's dedicated not-found state. */
  readonly notFound = computed(
    () => this._detailStatus() === 'error' && this._detailError()?.status === 404,
  );

  readonly addresses = this._addresses.asReadonly();
  readonly addressesStatus = this._addressesStatus.asReadonly();
  readonly addressesError = this._addressesError.asReadonly();

  readonly contact = this._contact.asReadonly();
  readonly contactStatus = this._contactStatus.asReadonly();
  readonly contactError = this._contactError.asReadonly();

  // --- intents: reads -------------------------------------------------------

  /** Enter the screen for one customer: three independent section reads. */
  load(customerNumber: number): void {
    this._customerNumber.set(customerNumber);
    this.reloadDetail();
    this.reloadAddresses();
    this.reloadContact();
  }

  /**
   * Enter a screen that needs ONLY this customer's addresses — the §2.7 sale
   * wizard's step 2 (AC-SALE-01-11).
   *
   * Deliberately not `load()`: the wizard shows no demographic and no contact
   * section, so firing those two reads would spend two requests it cannot
   * display and could surface an error banner for data nobody asked for. The
   * wizard still goes through this store rather than the API service directly,
   * because `AddressFormDialog` in API mode writes through it — that is how the
   * FR-ADDR-02 dialog keeps its full `validationErrors` handling in both hosts.
   */
  loadAddresses(customerNumber: number): void {
    this._customerNumber.set(customerNumber);
    this.reloadAddresses();
  }

  reloadDetail(): void {
    const customerNumber = this._customerNumber();
    if (customerNumber === null) return;
    const token = ++this.detailSeq;
    this._detailStatus.set('loading');
    this._detailError.set(null);
    this.api.getByNumber(customerNumber).subscribe({
      next: (customer) => {
        if (token !== this.detailSeq) return;
        this._customer.set(customer);
        this._detailStatus.set('loaded');
      },
      error: (error: unknown) => {
        if (token !== this.detailSeq) return;
        this._detailError.set(isApiError(error) ? error : null);
        this._detailStatus.set('error');
      },
    });
  }

  reloadAddresses(): void {
    const customerNumber = this._customerNumber();
    if (customerNumber === null) return;
    const token = ++this.addressesSeq;
    this._addressesStatus.set('loading');
    this._addressesError.set(null);
    this.api.getAddresses(customerNumber).subscribe({
      next: (addresses) => {
        if (token !== this.addressesSeq) return;
        this._addresses.set(addresses);
        this._addressesStatus.set('loaded');
      },
      error: (error: unknown) => {
        if (token !== this.addressesSeq) return;
        this._addressesError.set(isApiError(error) ? error : null);
        this._addressesStatus.set('error');
      },
    });
  }

  reloadContact(): void {
    const customerNumber = this._customerNumber();
    if (customerNumber === null) return;
    const token = ++this.contactSeq;
    this._contactStatus.set('loading');
    this._contactError.set(null);
    this.api.getContact(customerNumber).subscribe({
      next: (contact) => {
        if (token !== this.contactSeq) return;
        this._contact.set(contact);
        this._contactStatus.set('loaded');
      },
      error: (error: unknown) => {
        if (token !== this.contactSeq) return;
        this._contactError.set(isApiError(error) ? error : null);
        this._contactStatus.set('error');
      },
    });
  }

  // --- intents: mutations (observable back to the caller, signals tapped) ---

  updateDemographic(body: UpdateCustomerRequest): Observable<CustomerDetailResponse> {
    return this.api
      .update(this.requireNumber(), body)
      .pipe(tap((customer) => this._customer.set(customer)));
  }

  /** Soft delete of the whole aggregate (FR-CUST-05). The CALLER navigates
   *  away on success; on 409 (`MSG-CUST-HAS-PRODUCTS`) it shows the backend's
   *  answer — the guard is never pre-computed here (FE-ADR-013 §a). */
  deleteCustomer(): Observable<void> {
    return this.api.delete(this.requireNumber());
  }

  addAddress(body: AddressRequest): Observable<AddressResponse> {
    return this.api
      .addAddress(this.requireNumber(), body)
      .pipe(tap(() => this.reloadAddresses()));
  }

  updateAddress(addressId: number, body: AddressRequest): Observable<AddressResponse> {
    return this.api
      .updateAddress(this.requireNumber(), addressId, body)
      .pipe(tap(() => this.reloadAddresses()));
  }

  deleteAddress(addressId: number): Observable<void> {
    return this.api
      .deleteAddress(this.requireNumber(), addressId)
      .pipe(tap(() => this.reloadAddresses()));
  }

  setPrimaryAddress(addressId: number): Observable<AddressResponse> {
    return this.api
      .setPrimaryAddress(this.requireNumber(), addressId)
      .pipe(tap(() => this.reloadAddresses()));
  }

  updateContact(body: ContactMediumRequest): Observable<ContactMediumResponse> {
    return this.api
      .updateContact(this.requireNumber(), body)
      .pipe(tap((contact) => this._contact.set(contact)));
  }

  private requireNumber(): number {
    const customerNumber = this._customerNumber();
    if (customerNumber === null) {
      throw new Error('CustomerDetailStore: no customer loaded');
    }
    return customerNumber;
  }
}
