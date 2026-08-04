import { Injectable, computed, signal } from '@angular/core';
import type { CampaignResponse, OfferResponse, ServiceType } from '../../../core/catalog';
import { REQUIRED_SERVICE_TYPES, type BasketItem, type SubmitOrderRequest } from '../model';

/** What the basket is missing / has too many of, expressed as the message key
 *  the SERVER would answer with. Rendering the server's own key here means the
 *  preview and the real rejection say the same sentence (scope §2B.2). */
export interface BasketCompositionIssue {
  readonly serviceType: ServiceType;
  readonly messageKey: string;
}

const NO_OFFER_KEY: Readonly<Record<ServiceType, string>> = {
  INTERNET: 'MSG-SALE-NO-INTERNET',
  RESOURCE: 'MSG-SALE-NO-RESOURCE',
  ACTIVATION: 'MSG-SALE-NO-ACTIVATION',
};
const MULTI_OFFER_KEY: Readonly<Record<ServiceType, string>> = {
  INTERNET: 'MSG-SALE-MULTI-INTERNET',
  RESOURCE: 'MSG-SALE-MULTI-RESOURCE',
  ACTIVATION: 'MSG-SALE-MULTI-ACTIVATION',
};

/**
 * The sale wizard's session state: basket, characteristic values and the
 * chosen service address.
 *
 * 🔴 **NOTHING HERE IS EVER PERSISTED.** No `localStorage`, no
 * `sessionStorage`, no cookie, no server call. The basket lives only in these
 * signals, so a refresh loses it — that is the REQUIREMENT, not a defect
 * (AC-SALE-01-16: an incomplete sale must never be processed later). The
 * backend achieves the same thing structurally by having no basket table at
 * all; this store is the client half of that decision.
 *
 * The mock persists its basket to `eds_sale_basket` and friends. That is
 * deliberately NOT carried over: the mock is binding on DESIGN, not on
 * behaviour (FE-ADR-013 §e; scope §3.7).
 *
 * Not `providedIn: 'root'` — it is provided by the wizard component, so
 * leaving the flow destroys the basket with it.
 */
@Injectable()
export class SaleBasketStore {
  // --- Context: which customer + billing account this sale belongs to -------
  private readonly _customerNumber = signal<number | null>(null);
  private readonly _accountNumber = signal<string | null>(null);
  readonly customerNumber = this._customerNumber.asReadonly();
  readonly accountNumber = this._accountNumber.asReadonly();

  // --- Basket ---------------------------------------------------------------
  private readonly _items = signal<readonly BasketItem[]>([]);
  readonly items = this._items.asReadonly();

  readonly isEmpty = computed(() => this._items().length === 0);
  readonly count = computed(() => this._items().length);

  /** Sum of the basket's offer prices. This is a UX PREVIEW only: the
   *  authoritative total is `totalAmount` on the 201 response, which the server
   *  snapshots at creation (ADR-016 §2.4). The client never rounds or converts
   *  — it only adds up what the catalog returned (scope §3.11). */
  readonly totalAmount = computed(() =>
    this._items().reduce((sum, item) => sum + item.offer.price, 0),
  );

  /** The campaign this basket came from, or `null` when it was assembled
   *  offer-by-offer. A mixed basket (offers from two campaigns, or campaign +
   *  loose offers) has no single origin, so it reports `null` and the request
   *  omits `campaignId`. */
  readonly campaignId = computed<string | null>(() => {
    const ids = new Set(this._items().map((i) => i.campaignId));
    if (ids.size !== 1) return null;
    return this._items()[0]?.campaignId ?? null;
  });

  /**
   * AC-SALE-01-08 preview: exactly one INTERNET + one RESOURCE + one
   * ACTIVATION. Checked in the backend's order (missing before duplicate, per
   * service type) so the first issue shown is the one the server would name.
   *
   * This is UX ONLY (FE-ADR-007 §3). The basket is validated for real in
   * product-service at the point of persistence, and the client never assumes
   * an empty list here means the submit will succeed.
   */
  readonly compositionIssues = computed<readonly BasketCompositionIssue[]>(() => {
    const items = this._items();
    const issues: BasketCompositionIssue[] = [];
    for (const serviceType of REQUIRED_SERVICE_TYPES) {
      const n = items.filter((i) => i.offer.serviceType === serviceType).length;
      if (n === 0) issues.push({ serviceType, messageKey: NO_OFFER_KEY[serviceType] });
      else if (n > 1) issues.push({ serviceType, messageKey: MULTI_OFFER_KEY[serviceType] });
    }
    return issues;
  });

  // --- Configuration (step 2) ----------------------------------------------
  /** `offerId → (characteristicId → raw string value)`. Values stay raw
   *  strings: product-service parses them against the declared `data_type`, and
   *  it is the only authority on whether they fit (AC-SALE-01-19). */
  private readonly _values = signal<ReadonlyMap<number, ReadonlyMap<number, string>>>(new Map());
  readonly values = this._values.asReadonly();

  private readonly _serviceAddressId = signal<number | null>(null);
  readonly serviceAddressId = this._serviceAddressId.asReadonly();

  /** Characteristic ids the catalog marked mandatory, collected by step 2 when
   *  it loads the schemas. Kept HERE rather than passed around because the
   *  Submit button lives in the wizard footer (mock layout) while the schema is
   *  known only to the configuration step. */
  private readonly _mandatoryIds = signal<ReadonlySet<number>>(new Set());
  readonly mandatoryIds = this._mandatoryIds.asReadonly();

  // --- Context ------------------------------------------------------------
  setContext(customerNumber: number, accountNumber: string): void {
    this._customerNumber.set(customerNumber);
    this._accountNumber.set(accountNumber);
  }

  // --- Basket mutations -----------------------------------------------------
  has(offerId: number): boolean {
    return this._items().some((i) => i.offer.offerId === offerId);
  }

  /** AC-SALE-01-03. Adding an offer already in the basket is a no-op: the
   *  screen keeps `LBL-ADD-TO-BASKET` disabled for such rows (AC-SALE-01-05),
   *  so this is a guard, not a user-visible path. */
  addOffer(offer: OfferResponse): void {
    if (this.has(offer.offerId)) return;
    this._items.update((items) => [...items, { offer, campaignId: null, campaignName: null }]);
  }

  /**
   * AC-SALE-01-04: adding a campaign adds ALL its member offers at once.
   *
   * Returns `false` WITHOUT adding anything when any member offer is already in
   * the basket — AC-SALE-01-05 requires `MSG-SALE-DUP-OFFER` and requires the
   * campaign not to be added. All-or-nothing on purpose: a partial add would
   * leave a basket the user never asked for.
   */
  addCampaign(campaign: CampaignResponse): boolean {
    if (campaign.offers.some((o) => this.has(o.offerId))) return false;
    const added: BasketItem[] = campaign.offers.map((o) => ({
      offer: { offerId: o.offerId, offerName: o.offerName, serviceType: o.serviceType, price: o.price },
      campaignId: campaign.campaignId,
      campaignName: campaign.campaignName,
    }));
    this._items.update((items) => [...items, ...added]);
    return true;
  }

  /** AC-SALE-01-06: a removed offer must not appear in later steps or in the
   *  order summary — so its characteristic values are dropped with it. */
  remove(offerId: number): void {
    this._items.update((items) => items.filter((i) => i.offer.offerId !== offerId));
    this._values.update((map) => {
      if (!map.has(offerId)) return map;
      const next = new Map(map);
      next.delete(offerId);
      return next;
    });
  }

  /** AC-SALE-01-06 `LBL-CLEAR`: empties the basket and everything derived
   *  from it. The service address survives — it is a property of the customer,
   *  not of the basket. */
  clear(): void {
    this._items.set([]);
    this._values.set(new Map());
  }

  // --- Configuration mutations ---------------------------------------------
  setValue(offerId: number, characteristicId: number, value: string): void {
    this._values.update((map) => {
      const next = new Map(map);
      const forOffer = new Map(next.get(offerId) ?? []);
      forOffer.set(characteristicId, value);
      next.set(offerId, forOffer);
      return next;
    });
  }

  valueOf(offerId: number, characteristicId: number): string {
    return this._values().get(offerId)?.get(characteristicId) ?? '';
  }

  setServiceAddressId(addressId: number | null): void {
    this._serviceAddressId.set(addressId);
  }

  setMandatoryIds(ids: ReadonlySet<number>): void {
    this._mandatoryIds.set(ids);
  }

  /**
   * Build the wire request (AC-SALE-01-15). Returns `null` when the flow is not
   * complete enough to submit — the caller keeps the button disabled rather
   * than sending a request the server would certainly reject.
   *
   * Blank OPTIONAL values are omitted: persisting an empty `prod_char_val` row
   * would fabricate a configuration the user never made (the backend drops them
   * too). Blank MANDATORY values are still sent, so the server answers with
   * `MSG-VAL-CHAR-REQUIRED` and stays the single authority (FE-ADR-007 §3).
   */
  toRequest(): SubmitOrderRequest | null {
    const mandatoryIds = this._mandatoryIds();
    const accountNumber = this._accountNumber();
    const serviceAddressId = this._serviceAddressId();
    const items = this._items();
    if (!accountNumber || serviceAddressId === null || items.length === 0) return null;

    const campaignId = this.campaignId();
    return {
      accountNumber,
      serviceAddressId,
      ...(campaignId ? { campaignId } : {}),
      items: items.map((item) => {
        const forOffer = this._values().get(item.offer.offerId);
        const characteristics = [...(forOffer?.entries() ?? [])]
          .filter(([id, value]) => value.trim() !== '' || mandatoryIds.has(id))
          .map(([characteristicId, value]) => ({ characteristicId, value }));
        return { offerId: item.offer.offerId, characteristics };
      }),
    };
  }
}
