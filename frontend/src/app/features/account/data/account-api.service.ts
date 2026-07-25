import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { type Observable } from 'rxjs';
import {
  type AccountResponse,
  type CreateAccountRequest,
  type UpdateAccountRequest,
} from '../model';

/**
 * account-service HTTP client (docs/api/account-service.md; ADR-013/014). The
 * only place `HttpClient` is called for the account domain (FE-ADR-006 §3).
 *
 * ════════════════════════════════════════════════════════════════════════════
 * K-8 — 224 BILLING ACCOUNTS ONLY. The automatic 223 Customer Account is a
 * backend side effect that never appears in any response (ADR-013 §4): the list
 * endpoint returns 224s only, and a 223's number answers 404. There is therefore
 * NO endpoint here to create, read, update or delete a 223, and there must never
 * be one — its absence is the contract, not a gap. Do not add a "223" call.
 * ════════════════════════════════════════════════════════════════════════════
 *
 * Conventions: relative paths only (FE-ADR-004 §1); no error handling here — the
 * core interceptor normalizes every failure to `ApiError` (FE-ADR-008 §5). The
 * documented `messageKey`s (`MSG-ACCT-*`) resolve through the i18n catalogue at
 * the presentation layer, never here.
 */
@Injectable({ providedIn: 'root' })
export class AccountApiService {
  private readonly http = inject(HttpClient);

  private static readonly BASE = '/api/accounts';

  /**
   * `GET /api/accounts?customerId={customerNumber}` — one customer's Billing
   * Accounts. Returns a plain array (NO pagination envelope — unlike the customer
   * list): 224s only, Active + Passive, sorted Active-first then accountNumber
   * ascending (AC-ACCT-01-04). The UI must NOT re-sort — the order is the
   * contract. `customerId` is the public business customer number.
   */
  listByCustomer(customerNumber: number): Observable<readonly AccountResponse[]> {
    const params = new HttpParams().set('customerId', customerNumber);
    return this.http.get<AccountResponse[]>(AccountApiService.BASE, { params });
  }

  /** `GET /api/accounts/{accountNumber}` — single 224 (Active or Passive; Passive
   *  stays readable). A 223's number answers 404 `MSG-ACCT-NOT-FOUND` (K-8). */
  getByNumber(accountNumber: string): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(`${AccountApiService.BASE}/${accountNumber}`);
  }

  /** `POST /api/accounts` — create a Billing Account (type forced to 224; the K-8
   *  223 side effect is entirely server-side). 201 with the representation. */
  create(body: CreateAccountRequest): Observable<AccountResponse> {
    return this.http.post<AccountResponse>(AccountApiService.BASE, body);
  }

  /**
   * `PUT /api/accounts/{accountNumber}` — update `accountName` + `addressId` ONLY.
   * KR-11: `accountNumber` is immutable and is never in the body (the
   * {@link UpdateAccountRequest} type makes it unrepresentable). The path carries
   * the number; the body never does.
   */
  update(accountNumber: string, body: UpdateAccountRequest): Observable<AccountResponse> {
    return this.http.put<AccountResponse>(`${AccountApiService.BASE}/${accountNumber}`, body);
  }

  /**
   * `DELETE /api/accounts/{accountNumber}` — soft passivation (FR-ACCT-04), 204.
   * The row stays list-visible as Passive; the number is never reused. Guards
   * (409 `MSG-ACCT-HAS-PRODUCTS` for active products, 409 `MSG-ACCT-NOT-ACTIVE`
   * for an already-Passive account) surface via the interceptor — this method
   * does not pre-check them. The success toast (`MSG-ACCT-DELETED`) and the
   * confirm prompt (`MSG-ACCT-DELETE-CONFIRM`) are frontend-only.
   */
  delete(accountNumber: string): Observable<void> {
    return this.http.delete<void>(`${AccountApiService.BASE}/${accountNumber}`);
  }
}
