import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { type Observable } from 'rxjs';
import { type ProductDetail, type ProductRow } from '../model';

/**
 * product-service HTTP client (docs/api/product-service.md; FR-PROD-01..02).
 * The only place `HttpClient` is called for the product domain (FE-ADR-006 §3).
 *
 * ════════════════════════════════════════════════════════════════════════════
 * PHASE A IS READ-ONLY — TWO GET METHODS, AND THERE IS NOTHING ELSE TO ADD.
 * ════════════════════════════════════════════════════════════════════════════
 * The service exposes no create, no update and no cancellation endpoint
 * (AC-PROD-01-04: the Action column is view-only). Do NOT add a
 * `deactivate()` here "to wire the button up" — the button stays inert until a
 * write slice exists (FE-ADR-013 §a).
 *
 * The catalog endpoints `GET /api/offers` and `GET /api/campaigns` exist and are
 * routed through the gateway, but are DELIBERATELY not wrapped here: their only
 * consumer is the Offer Selection flow, which belongs to the order domain and is
 * out of scope (FE-ADR-013 §Amendment B). Unused client code for an unbuilt
 * screen is the same debt §a exists to prevent.
 *
 * Conventions: relative paths only (FE-ADR-004 §1); no error handling here — the
 * core interceptor normalizes every failure to `ApiError` (FE-ADR-008 §5), whose
 * `messageKey` (`MSG-PROD-NOT-FOUND`, `MSG-ACCT-NOT-FOUND`,
 * `MSG-SERVICE-UNAVAILABLE`, …) is resolved through i18n at the presentation
 * layer, never here.
 */
@Injectable({ providedIn: 'root' })
export class ProductApiService {
  private readonly http = inject(HttpClient);

  private static readonly BASE = '/api/products';

  /**
   * `GET /api/products?accountNumber={kr11}` — the products of ONE billing
   * account (FR-PROD-01).
   *
   * Returns a PLAIN ARRAY: there is no `Page` envelope and no `page`/`size`
   * parameter, because FR-PROD-01 defines no pagination rule. Do not add one —
   * a `size` here would be an invented contract.
   *
   * The list contains Active AND Passive products (AC-PROD-01-03) in the
   * server's order; the client neither filters nor re-sorts. An account with no
   * products answers `200 []` — the "no products" text is frontend-only
   * (`MSG-PROD-NONE`, never returned by the backend). An unknown account number
   * (or a K-8 223's number, indistinguishable by design) answers 404
   * `MSG-ACCT-NOT-FOUND`.
   *
   * @param accountNumber the PUBLIC 10-digit KR-11 billing-account number.
   */
  listByAccountNumber(accountNumber: string): Observable<readonly ProductRow[]> {
    const params = new HttpParams().set('accountNumber', accountNumber);
    return this.http.get<ProductRow[]>(ProductApiService.BASE, { params });
  }

  /**
   * `GET /api/products/{id}` — the FR-PROD-02 detail modal payload.
   *
   * A child product's Service Address is its PARENT'S: the backend walks the
   * parent chain and resolves the address through customer-service, so this is
   * a SINGLE call — the client never fetches a parent or an address itself.
   * An unknown id answers 404 `MSG-PROD-NOT-FOUND`.
   *
   * @param productId the public product identifier from a {@link ProductRow}.
   */
  getById(productId: number): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`${ProductApiService.BASE}/${productId}`);
  }
}
