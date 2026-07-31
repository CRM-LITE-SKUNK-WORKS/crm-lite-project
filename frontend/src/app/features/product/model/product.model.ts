/**
 * product-service contract types (docs/api/product-service.md; FR-PROD-01..02).
 *
 * ════════════════════════════════════════════════════════════════════════════
 * PHASE A IS READ-ONLY.
 * ════════════════════════════════════════════════════════════════════════════
 * The service exposes exactly two product endpoints — a list and a detail — and
 * NOTHING else: no product creation, no provisioning, no cancellation, no
 * basket, no order. AC-PROD-01-04 is explicit that the Action column offers a
 * view icon only. So there is deliberately no `CreateProductRequest`,
 * no `UpdateProductRequest` and no `DeactivateProductRequest` in this file, and
 * there must never be one until the write slice exists (FE-ADR-013 §a).
 * ════════════════════════════════════════════════════════════════════════════
 *
 * Two contract facts this layer encodes structurally:
 * - **No pagination.** `GET /api/products` returns a PLAIN ARRAY, not a `Page`
 *   envelope — FR-PROD-01 defines no pagination rule. There is therefore no
 *   `Page<ProductRow>` type here, and `shared/patterns/Pagination` is not used
 *   by this feature.
 * - **`campaignId` is the PUBLIC campaign code** (`cmpg.campaign_code`, e.g.
 *   `"CMP-ADSL-01"`), never the internal `cmpg.id`. Internal campaign ids never
 *   leave the service, so the UI has exactly one campaign identity to show.
 */

/** Derived lifecycle status of a product, computed SERVER-side from
 *  `PROD.status_id`. Both values are listed (AC-PROD-01-03): a Passive product
 *  stays in the table, it is never filtered out client-side. */
export type ProductStatus = 'Active' | 'Passive';

/**
 * One row of the FR-PROD-01 per-account product table (AC-PROD-01-03 columns),
 * verbatim from `docs/api/product-service.md` §Representations and the
 * `ProductRowResponse` record.
 *
 * Campaign fields are `null` together for a product bought outside a campaign —
 * rendering that as `"-"` is explicitly the frontend's job (backend note). The
 * response carries NO `Action` field: that column is pure UI (AC-PROD-01-04).
 */
export interface ProductRow {
  /** Public product identifier (`prod.id`) — the `{id}` of the detail call.
   *  There is no KR-11-style product number: business numbers exist only for
   *  accounts. */
  readonly productId: number;
  readonly productName: string;
  /** `null` when the product was bought outside a campaign. */
  readonly campaignName: string | null;
  /** PUBLIC campaign code (`cmpg.campaign_code`); `null` outside a campaign. */
  readonly campaignId: string | null;
  /** Server-derived — the client never recomputes it. */
  readonly productStatus: ProductStatus;
}

/**
 * The Service Address block of the detail modal, resolved by the backend
 * through customer-service. A child product shows ITS PARENT'S address — the
 * parent chain is walked server-side, so the client makes NO second call and
 * performs no parent lookup of its own.
 */
export interface ProductServiceAddress {
  readonly addressId: number;
  readonly street: string;
  readonly houseFlatNumber: string;
  readonly districtName: string;
  readonly cityName: string;
}

/**
 * `GET /api/products/{id}` — the AC-PROD-02-01 detail modal fields.
 *
 * Note what is NOT here, on purpose: no `productId`, no `productName`, no
 * campaign CODE. The modal is always opened from a row the user just saw, so
 * the product's identity comes from that {@link ProductRow}; the detail
 * response carries the campaign as a NAME only (`campaign`), and the contract
 * exposes no campaign code on this endpoint.
 */
export interface ProductDetail {
  readonly productOfferName: string;
  readonly productOfferId: number;
  readonly productSpecId: number;
  /** Campaign NAME; `null` outside a campaign. */
  readonly campaign: string | null;
  /** `null` when neither the product nor any ancestor carries a service address,
   *  or when the referenced address has been soft-deleted — the modal still
   *  renders (documented backend behaviour). */
  readonly serviceAddress: ProductServiceAddress | null;
}
