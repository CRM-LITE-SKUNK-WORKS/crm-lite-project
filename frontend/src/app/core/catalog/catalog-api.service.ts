import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { type Observable } from 'rxjs';
import {
  type CampaignResponse,
  type CharacteristicResponse,
  type OfferResponse,
} from './catalog.model';

/**
 * HTTP client for the product catalog: offers, campaigns and offer
 * characteristics (`/api/offers/**`, `/api/campaigns`).
 *
 * Same contract as `LookupApiService`, its sibling in this layer:
 *  - **Relative paths only** (FE-ADR-004 §1) — no host, no base-url constant.
 *  - **No error handling** (FE-ADR-008 §5): the core interceptor normalizes
 *    every failure to `ApiError`; this layer neither catches nor maps.
 *  - **No caching, no state** (FE-ADR-006 §3): a thin transport. The sale
 *    wizard's basket is the only sale state and it lives in `features/order/`.
 *
 * NO PAGINATION anywhere here — every endpoint returns a plain array and
 * `product-service` contains no `Pageable`/`Page<` at all (verified against the
 * backend source, scope §2B.4). No `page`/`size` parameter is ever sent.
 */
@Injectable({ providedIn: 'root' })
export class CatalogApiService {
  private readonly http = inject(HttpClient);

  /** `GET /api/offers` — ACTIVE offers only (server-filtered). */
  listOffers(): Observable<readonly OfferResponse[]> {
    return this.http.get<OfferResponse[]>('/api/offers');
  }

  /** `GET /api/campaigns` — active campaigns with member offers and a derived
   *  `totalPrice`. */
  listCampaigns(): Observable<readonly CampaignResponse[]> {
    return this.http.get<CampaignResponse[]>('/api/campaigns');
  }

  /**
   * `GET /api/offers/{offerId}/characteristics` — the AC-SALE-01-10 field
   * schema for ONE offer. An offer with no characteristics answers `200 []`
   * (AC-SALE-01-21), which is a valid empty result and not an error.
   *
   * ⚠ This endpoint is absent from `docs/api/product-service.md`'s endpoint
   * table; the shape below is taken from the backend source (`OfferController`,
   * `CharacteristicResponse`), not guessed (scope §2B).
   */
  getOfferCharacteristics(offerId: number): Observable<readonly CharacteristicResponse[]> {
    return this.http.get<CharacteristicResponse[]>(
      `/api/offers/${encodeURIComponent(String(offerId))}/characteristics`,
    );
  }
}
