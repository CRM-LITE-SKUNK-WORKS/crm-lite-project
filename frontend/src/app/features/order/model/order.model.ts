import type { OfferResponse, ServiceType } from '../../../core/catalog';

/**
 * order-service contract types (docs/api/order-service.md) plus the
 * CLIENT-ONLY basket shape.
 *
 * The split matters: `SubmitOrderRequest`/`OrderResponse` are the wire
 * contract, while `BasketItem` has NO server counterpart at all — there is no
 * basket table and no basket endpoint. The backend learns about a sale exactly
 * once, at Submit, which is what makes AC-SALE-01-16 ("an abandoned sale must
 * never be processed later") true BY CONSTRUCTION rather than by cleanup.
 */

/** One characteristic value the user filled in (AC-SALE-01-10). Values are
 *  forwarded to product-service VERBATIM — order-service writes no validation
 *  of its own, and neither does this client (FE-ADR-007 §3). */
export interface CharacteristicValueRequest {
  readonly characteristicId: number;
  readonly value: string;
}

/** One basket line as the wire sees it. `characteristics` is `[]` for an offer
 *  that has none (AC-SALE-01-21) — the key is always present. */
export interface OrderItemRequest {
  readonly offerId: number;
  readonly characteristics: readonly CharacteristicValueRequest[];
}

/**
 * `POST /api/orders` — the whole sale as ONE atomic command (AC-SALE-01-15).
 * There is no partial/step-wise request: the six-step orchestration
 * (account precondition → order → products → amounts → confirm → involvement)
 * lives entirely inside order-service. The client must never imitate it.
 */
export interface SubmitOrderRequest {
  /** KR-11 billing account the sale started from (AC-SALE-01-01). */
  readonly accountNumber: string;
  /** Address chosen for the MAIN product (AC-SALE-01-11). */
  readonly serviceAddressId: number;
  /** PUBLIC campaign code, OPTIONAL: a basket assembled offer-by-offer belongs
   *  to no campaign. Omitted rather than sent empty. */
  readonly campaignId?: string;
  readonly items: readonly OrderItemRequest[];
}

/** One line of the created order. `productId` is filled by the orchestration's
 *  second local transaction; `amount` is a SNAPSHOT taken at creation. */
export interface OrderItemResponse {
  readonly offerId: number;
  readonly productId: number;
  readonly amount: number;
}

/**
 * The order representation — identical for `POST /api/orders` (201) and
 * `GET /api/orders/{orderNumber}` (200).
 *
 * ⚠ It carries ONLY what `order_db` owns. Offer names, the campaign and the
 * service address are NOT echoed back (ADR-016 §3) — the client assembled the
 * basket and already holds them, so the Submit screen renders those from its
 * own state, not from this response.
 */
export interface OrderResponse {
  /** KR-12 public number. READ-ONLY: the client never generates, validates,
   *  parses or derives meaning from it (scope §3.10). */
  readonly orderNumber: string;
  /** Catalog SHORT CODE (e.g. `MIDLWARE`), not a rendered label — the
   *  user-facing wording is an i18n entry. */
  readonly orderStatus: string;
  readonly accountNumber: string;
  readonly customerNumber: number;
  /** Snapshot total (ADR-016 §2.4). Displayed as received. */
  readonly totalAmount: number;
  readonly items: readonly OrderItemResponse[];
}

/**
 * A basket line — CLIENT-ONLY, session-memory only, never persisted
 * (AC-SALE-01-16; scope §3.7 records that the mock's localStorage basket is
 * deliberately NOT carried over).
 *
 * `campaignId`/`campaignName` are set when the line entered the basket as part
 * of a campaign (AC-SALE-01-04, which adds all member offers at once). They are
 * display metadata AND the source of the request's optional `campaignId`.
 */
export interface BasketItem {
  readonly offer: OfferResponse;
  readonly campaignId: string | null;
  readonly campaignName: string | null;
}

/**
 * How the basket is DISPLAYED (scope §4.33). The wire and the rule engine keep
 * working on the flat {@link BasketItem} list — `POST /api/orders` takes a flat
 * `items[]` and AC-SALE-01-08 counts service types across the whole basket — but
 * the mock's basket panel shows a campaign as ONE entry with its member offers
 * nested underneath, and a loose offer as a plain entry.
 *
 * So this is a projection, not a second source of truth: `groups` is derived
 * from `items`, never stored alongside it.
 */
export interface BasketCampaignGroup {
  readonly kind: 'campaign';
  /** Stable list key: `campaign:{campaignId}`. */
  readonly key: string;
  readonly campaignId: string;
  readonly campaignName: string;
  /** The member offers, in the order they entered the basket. */
  readonly items: readonly BasketItem[];
  /** Sum of the member prices — the figure the mock puts on the entry row. */
  readonly price: number;
}

export interface BasketOfferGroup {
  readonly kind: 'offer';
  /** Stable list key: `offer:{offerId}`. */
  readonly key: string;
  readonly item: BasketItem;
  readonly price: number;
}

export type BasketGroup = BasketCampaignGroup | BasketOfferGroup;

/** The three service types a valid basket must contain exactly one of
 *  (AC-SALE-01-08). Order matters: the backend checks INTERNET → RESOURCE →
 *  ACTIVATION and reports the first failure, so previewing in the same order
 *  shows the user the same rule the server would name. */
export const REQUIRED_SERVICE_TYPES: readonly ServiceType[] = [
  'INTERNET',
  'RESOURCE',
  'ACTIVATION',
];
