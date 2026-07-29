/**
 * customer-service contract types (docs/api/customer-service.md).
 *
 * Every field below is transcribed from the API doc — nothing is invented. Where
 * the doc does not pin a shape, the type carries a `⚠ dokümanda yok` note and the
 * open item is listed in the data-layer report; those are NOT guesses to rely on.
 *
 * The frontend consumes a contract it does not own (FE-ADR-002 §2): these types
 * are strict so a backend rename becomes a compile error, not an `undefined` on
 * screen.
 */

/** Wire values for gender — the API uses capitalised `"Male"`/`"Female"`
 *  (customer-service.md §Atomic create "gender is Male/Female"; scope §2.7),
 *  NOT the mock's lowercase `male`/`female`. */
export type Gender = 'Male' | 'Female';

/**
 * Detail contract (`GET /api/customers/{customerNumber}`) — and, since ADR-005,
 * the SAME shape used for every list row (`Page<CustomerDetailResponse>`).
 * Fields verified against customer-service.md (the JSON at §List and §Atomic
 * create return payload).
 */
export interface CustomerDetailResponse {
  /** Business customer number (CUST.customer_number, seeds start at 1001). NOT
   *  the internal id, which never leaves the service. */
  readonly customerNumber: number;
  readonly firstName: string;
  /** Second/middle name — nullable (JSON shows `"middleName": null`). */
  readonly middleName: string | null;
  readonly lastName: string;
  readonly fatherName: string | null;
  readonly motherName: string | null;
  /** ISO `yyyy-MM-dd` on the wire (scope §2.6); displayed as `dd.MM.yyyy`. */
  readonly birthDate: string;
  readonly gender: Gender;
  /** 11-digit string; kept as a string (never a number — leading digits matter). */
  readonly nationalityId: string;
  /** Party role, e.g. `"Customer"`. Read-only column; no role filter (scope §2.2). */
  readonly role: string;
  /** GNL_ST short code, e.g. `"ACTV"` (only active customers are ever returned). */
  readonly status: string;
}

/**
 * Demographic block of the atomic create (`POST /api/customers`) and the body of
 * the demographic update (`PUT /api/customers/{customerNumber}`).
 *
 * Optional fields are the ones the doc shows as `null`/omitted; required fields
 * are those the create example always sends. Client validation is UX only — the
 * backend is the authority (FE-ADR-007).
 */
export interface DemographicRequest {
  readonly firstName: string;
  readonly middleName?: string | null;
  readonly lastName: string;
  readonly fatherName?: string | null;
  readonly motherName?: string | null;
  readonly birthDate: string; // ISO yyyy-MM-dd (never a localized string)
  readonly gender: Gender;
  readonly nationalityId: string;
}

/** Atomic create body (`POST /api/customers`) — single transaction: demographic
 *  + ≥1 address + contact (customer-service.md §Atomic create). */
/**
 * Answer of `GET /api/customers/nationality-id-availability` (ADR-005 §Addendum).
 *
 * ONE field on purpose: the rule it reports covers soft-deleted customers, so the
 * endpoint deliberately says whether the ID is free and never who holds it. Do not
 * expect a customer number here — its absence is the contract.
 */
export interface NationalityIdAvailabilityResponse {
  readonly available: boolean;
}

export interface CreateCustomerRequest {
  readonly demographic: DemographicRequest;
  readonly addresses: readonly import('./address.model').AddressRequest[];
  readonly contactMedium: import('./contact.model').ContactMediumRequest;
}

/** Demographic update body (`PUT /api/customers/{customerNumber}`). The doc's
 *  example (step J) sends the demographic fields flat, NOT wrapped in
 *  `demographic` — verified against the curl payload. */
export type UpdateCustomerRequest = DemographicRequest;
