import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { type Observable, map } from 'rxjs';
import {
  type AddressRequest,
  type AddressResponse,
  type ContactMediumRequest,
  type ContactMediumResponse,
  type CreateCustomerRequest,
  type CustomerDetailResponse,
  type CustomerSearchCriteria,
  type NationalityIdAvailabilityResponse,
  type PageRequest,
  type PageResult,
  type SpringPage,
  type UpdateCustomerRequest,
  toPageResult,
} from '../model';

/**
 * customer-service HTTP client (docs/api/customer-service.md). The ONLY place
 * `HttpClient` is called for the customer aggregate (FE-ADR-006 §3); state
 * services and screens consume the typed results, never `HttpClient` directly.
 *
 * Conventions this service upholds:
 *  - **Relative paths only** (FE-ADR-004 §1): every URL is `/api/...`; no host,
 *    no base-url constant, ever. The dev proxy / nginx forward these.
 *  - **No error handling here** (FE-ADR-008 §5, §10): the core interceptor
 *    normalizes every failure to `ApiError`; this layer neither catches nor
 *    maps errors nor produces messages. It just issues requests and types the
 *    responses.
 *  - **The Page envelope is read in ONE place** (`toPageResult`, scope §4.4): a
 *    framework shape drift is contained to the model layer, never a screen.
 */
@Injectable({ providedIn: 'root' })
export class CustomerApiService {
  private readonly http = inject(HttpClient);

  private static readonly BASE = '/api/customers';

  /**
   * `GET /api/customers` — canonical list + filter (ADR-005). No criteria ⇒
   * browse all active customers; criteria ⇒ filter. `page` and `size` are ALWAYS
   * sent explicitly (KR-04). `accountNumber`/`orderNumber` are unrepresentable in
   * `CustomerSearchCriteria`, so a 501 filter can never be requested (FE-ADR-013).
   */
  list(
    criteria: CustomerSearchCriteria,
    page: PageRequest,
  ): Observable<PageResult<CustomerDetailResponse>> {
    const params = this.toParams(criteria, page);
    return this.http
      .get<SpringPage<CustomerDetailResponse>>(CustomerApiService.BASE, { params })
      .pipe(map(toPageResult));
  }

  /**
   * `GET /api/customers/nationality-id-availability?nationalityId=` — the
   * duplicate-NAT-ID pre-check for the Create wizard's step 1 (ADR-005
   * §Addendum, added 2026-07-29 for exactly this call).
   *
   * Why NOT the list endpoint: it is active-only by design, while the ADR-003
   * uniqueness rule also covers SOFT-DELETED holders — a customer created,
   * deleted, then re-entered by the same ID. This endpoint asks the rule itself
   * (the very method the create path uses), so it sees those too and the screen
   * can refuse the ID before the user walks the whole wizard.
   *
   * Still ADVISORY, not a reservation (FE-ADR-007 §3): a create landing between
   * the probe and the POST is answered by the 409, which the wizard keeps
   * handling. Returns `false` when the ID is free.
   */
  nationalityIdIsTaken(nationalityId: string): Observable<boolean> {
    const params = new HttpParams().set('nationalityId', nationalityId);
    return this.http
      .get<NationalityIdAvailabilityResponse>(
        `${CustomerApiService.BASE}/nationality-id-availability`,
        { params },
      )
      .pipe(map((response) => !response.available));
  }

  /** `GET /api/customers/{customerNumber}` — active customers only (else 404). */
  getByNumber(customerNumber: number): Observable<CustomerDetailResponse> {
    return this.http.get<CustomerDetailResponse>(`${CustomerApiService.BASE}/${customerNumber}`);
  }

  /** `POST /api/customers` — atomic create; 201 with the detail payload. */
  create(body: CreateCustomerRequest): Observable<CustomerDetailResponse> {
    return this.http.post<CustomerDetailResponse>(CustomerApiService.BASE, body);
  }

  /** `PUT /api/customers/{customerNumber}` — demographic update. */
  update(customerNumber: number, body: UpdateCustomerRequest): Observable<CustomerDetailResponse> {
    return this.http.put<CustomerDetailResponse>(
      `${CustomerApiService.BASE}/${customerNumber}`,
      body,
    );
  }

  /** `DELETE /api/customers/{customerNumber}` — soft delete of the aggregate (204). */
  delete(customerNumber: number): Observable<void> {
    return this.http.delete<void>(`${CustomerApiService.BASE}/${customerNumber}`);
  }

  // --- Address sub-module (§K) ----------------------------------------------

  /** `GET /api/customers/{customerNumber}/addresses` — active addresses. */
  getAddresses(customerNumber: number): Observable<readonly AddressResponse[]> {
    return this.http.get<AddressResponse[]>(this.addressesUrl(customerNumber));
  }

  /** `POST /api/customers/{customerNumber}/addresses` — add (first becomes
   *  primary automatically); 201 with the created address. */
  addAddress(customerNumber: number, body: AddressRequest): Observable<AddressResponse> {
    return this.http.post<AddressResponse>(this.addressesUrl(customerNumber), body);
  }

  /** `PUT /api/customers/{customerNumber}/addresses/{addressId}` — update fields. */
  updateAddress(
    customerNumber: number,
    addressId: number,
    body: AddressRequest,
  ): Observable<AddressResponse> {
    return this.http.put<AddressResponse>(this.addressUrl(customerNumber, addressId), body);
  }

  /** `DELETE /api/customers/{customerNumber}/addresses/{addressId}` — soft delete.
   *  Guards (409 `MSG-ADDR-LAST-DELETE` / `MSG-ADDR-PRIMARY-DELETE`) surface via
   *  the interceptor; this method does not pre-check them. */
  deleteAddress(customerNumber: number, addressId: number): Observable<void> {
    return this.http.delete<void>(this.addressUrl(customerNumber, addressId));
  }

  /** `PATCH /api/customers/{customerNumber}/addresses/{addressId}/primary` —
   *  make primary (demotes the previous). Returns the updated address (shape
   *  verified via Swagger, 2026-07-25). NOTE: only the PATCHED address comes
   *  back — the demoted ex-primary does not, so callers still refresh the list
   *  via `getAddresses` to keep every card consistent. */
  setPrimaryAddress(customerNumber: number, addressId: number): Observable<AddressResponse> {
    return this.http.patch<AddressResponse>(
      `${this.addressUrl(customerNumber, addressId)}/primary`,
      null,
    );
  }

  // --- Contact-medium sub-module (§L) ---------------------------------------

  /** `GET /api/customers/{customerNumber}/contact-medium`. */
  getContact(customerNumber: number): Observable<ContactMediumResponse> {
    return this.http.get<ContactMediumResponse>(this.contactUrl(customerNumber));
  }

  /** `PUT /api/customers/{customerNumber}/contact-medium`. */
  updateContact(
    customerNumber: number,
    body: ContactMediumRequest,
  ): Observable<ContactMediumResponse> {
    return this.http.put<ContactMediumResponse>(this.contactUrl(customerNumber), body);
  }

  // --- helpers --------------------------------------------------------------

  private addressesUrl(customerNumber: number): string {
    return `${CustomerApiService.BASE}/${customerNumber}/addresses`;
  }
  private addressUrl(customerNumber: number, addressId: number): string {
    return `${this.addressesUrl(customerNumber)}/${addressId}`;
  }
  private contactUrl(customerNumber: number): string {
    return `${CustomerApiService.BASE}/${customerNumber}/contact-medium`;
  }

  /** Build query params: only non-empty criteria are appended; `page`/`size` are
   *  always present (KR-04). Trimming keeps a stray space from becoming a filter. */
  private toParams(criteria: CustomerSearchCriteria, page: PageRequest): HttpParams {
    let params = new HttpParams().set('page', page.page).set('size', page.size);
    for (const [key, value] of Object.entries(criteria)) {
      const trimmed = typeof value === 'string' ? value.trim() : value;
      if (trimmed !== undefined && trimmed !== null && trimmed !== '') {
        params = params.set(key, trimmed);
      }
    }
    return params;
  }
}
