import type { OfferResponse, ServiceType } from '../../../core/catalog';

/**
 * order-service contract types (docs/api/order-service.md) plus the
 * CLIENT-ONLY basket shape.
 *
 * The split matters: `CreateDraftRequest`/`SubmitDraftRequest`/
 * `OrderStatusResponse` are the LIVE asynchronous wire contract (ADR-018),
 * while `BasketItem` has NO server counterpart at all — there is no basket
 * table and no basket endpoint. The backend learns about a sale exactly once,
 * at Submit, which is what makes AC-SALE-01-16 ("an abandoned sale must never
 * be processed later") true BY CONSTRUCTION rather than by cleanup.
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
 * `POST /api/orders/drafts` — Start New Sale (ADR-018 §6). One field,
 * deliberately: the draft exists to give the sale an identity, not to record
 * a basket. The customer number is resolved from the billing account, never
 * accepted from the browser.
 */
export interface CreateDraftRequest {
  /** The KR-11 billing account the sale is started from (AC-SALE-01-01). */
  readonly accountNumber: string;
}

/**
 * `POST /api/orders/{orderNumber}/submit` — the basket, at the moment the
 * user confirms Submit (AC-SALE-01-15, ADR-018 §6). No `accountNumber`: the
 * account was fixed when the draft was created and is read from the draft,
 * not resent — a caller cannot submit basket A against draft B's account.
 */
export interface SubmitDraftRequest {
  /** Address chosen for the MAIN product (AC-SALE-01-11). */
  readonly serviceAddressId: number;
  /** PUBLIC campaign code, OPTIONAL: a basket assembled offer-by-offer belongs
   *  to no campaign. Omitted rather than sent empty. */
  readonly campaignId?: string;
  readonly items: readonly OrderItemRequest[];
}

/** The four public values of the asynchronous SALE status resource (ADR-018
 *  §6). Not GNL_ST — this is order-service's own API contract, kept
 *  deliberately separate from the analyst-owned `orderStatus` axis
 *  (WAIT / MIDLWARE / CANCELLED). */
export type ProcessingStatus = 'DRAFT' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

/**
 * The asynchronous SALE status resource (ADR-018 §6) — the body of the 201
 * draft, the 202 accepted submit, and every poll of the status endpoint. One
 * shape for all three: a client that just submitted and a client that is
 * polling are asking the same question — where is this sale?
 */
export interface OrderStatusResponse {
  /** KR-12 public number. READ-ONLY: the client never generates, validates,
   *  parses or derives meaning from it. */
  readonly orderNumber: string;
  /** The analyst's GNL_ST ORDER-domain short code: `WAIT` / `MIDLWARE` /
   *  `CANCELLED`. Not a rendered label. */
  readonly orderStatus: string;
  readonly processingStatus: ProcessingStatus;
  /** A catalog message key, present only on a terminal `FAILED` status.
   *  Never an exception message or any other infrastructure detail. */
  readonly failureMessageKey: string | null;
  /** ISO instant of the last state change — what lets a poll loop tell
   *  "still working" from "stuck". Not currently rendered. */
  readonly updatedAt: string;
}

/** One line of the created order. `productId` is filled by the orchestration's
 *  second local transaction; `amount` is a SNAPSHOT taken at creation. */
export interface OrderItemResponse {
  readonly offerId: number;
  readonly productId: number;
  readonly amount: number;
}

/**
 * The order detail representation — `GET /api/orders/{orderNumber}` (200).
 * Also the body of the deprecated `POST /api/orders` (ADR-018 §10), which
 * this client no longer calls; kept for the still-live detail read.
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
