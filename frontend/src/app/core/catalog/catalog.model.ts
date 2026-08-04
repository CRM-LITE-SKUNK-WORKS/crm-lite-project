/**
 * Product-catalog contract types: offers, campaigns and offer characteristics
 * (docs/api/product-service.md; the characteristics endpoint is verified from
 * `OfferController` + `CharacteristicResponse` in the backend source — it is
 * missing from that document's endpoint table, scope §2B).
 *
 * WHY THIS LIVES IN core/ AND NOT features/product/ (scope §1.6 / FE-ADR-003):
 * the catalog is consumed by TWO features — `features/product/` (read side) and
 * the `features/order/` sale wizard — and a feature may never import a sibling
 * feature. `core/lookup/` is the established precedent for exactly this shape:
 * cross-feature reference data, thin API client + typed model, no screen logic.
 */

/**
 * Service type an offer derives from, via its spec (`PROD_SPEC.service_type_id`
 * → central GNL_TP 10/11/12). `PROD_OFR` has no service-type column of its own,
 * so this is a DERIVED value the server computes; the client never re-derives it.
 *
 * AC-SALE-01-08 makes this the basket's structural rule: a valid basket is
 * exactly one of each. AC-SALE-01-09 additionally makes the INTERNET offer the
 * MAIN product and the other two its children — also derived server-side, never
 * declared by the caller.
 */
export type ServiceType = 'INTERNET' | 'RESOURCE' | 'ACTIVATION';

/** The four data types the workbook defines (`PROD_SPEC_CHAR.data_type`). The
 *  backend validates against this same value (AC-SALE-01-19), so both sides
 *  read one source of truth. NOTE: the mock renders an XDSL *password* field,
 *  but `password` is not a contract data type — the contract wins
 *  (FE-ADR-013 §e, scope §2B.7). */
export type CharacteristicDataType = 'NUMBER' | 'BOOLEAN' | 'TEXT' | 'DATE';

/** `GET /api/offers` — the ACTIVE offer catalog (inactive offers are filtered
 *  out server-side, which is why `MSG-SALE-OFFER-INACTIVE` cannot be produced
 *  by picking from this list; scope §2B.3). Plain array, no pagination. */
export interface OfferResponse {
  readonly offerId: number;
  readonly offerName: string;
  readonly serviceType: ServiceType;
  /** Seed fixture pending analyst approval (document-delta P1/P5). Rendered as
   *  received — the client never computes, rounds or converts prices. */
  readonly price: number;
}

/** One member offer inside a campaign (`GET /api/campaigns`). `main` marks the
 *  campaign's headline offer; it is presentation metadata, not the AC-SALE-01-09
 *  main-product rule (that is derived from `serviceType`). */
export interface CampaignOffer extends OfferResponse {
  readonly main: boolean;
}

/** `GET /api/campaigns` — active campaigns with their member offers. Plain
 *  array, no pagination. */
export interface CampaignResponse {
  /** PUBLIC campaign code (e.g. `CMP-ADSL-01`) — never an internal id. This is
   *  the exact value `POST /api/orders` takes as `campaignId`. */
  readonly campaignId: string;
  readonly campaignName: string;
  readonly description: string | null;
  /** ISO instant. */
  readonly activationEndDate: string | null;
  readonly offers: readonly CampaignOffer[];
  /** DERIVED server-side (sum of member prices); `CMPG` has no price column. */
  readonly totalPrice: number;
}

/**
 * `GET /api/offers/{offerId}/characteristics` — the configurable fields the
 * Product Configuration screen must render for one offer (AC-SALE-01-10/17/20).
 *
 * An empty array is a VALID answer, not a 404: an offer with no characteristics
 * still gets its own block on the screen (AC-SALE-01-21).
 */
export interface CharacteristicResponse {
  readonly characteristicId: number;
  readonly name: string;
  readonly description: string | null;
  /** Drives the input control (AC-SALE-01-17) and the client-side format hint. */
  readonly dataType: CharacteristicDataType;
  /** Drives the required marker (AC-SALE-01-20) and `MSG-VAL-CHAR-REQUIRED`. */
  readonly mandatory: boolean;
}
