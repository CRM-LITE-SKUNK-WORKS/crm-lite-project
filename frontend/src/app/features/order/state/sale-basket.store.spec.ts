import { TestBed } from '@angular/core/testing';
import { type CampaignResponse, type OfferResponse } from '../../../core/catalog';
import { SaleBasketStore } from './sale-basket.store';

const INTERNET: OfferResponse = {
  offerId: 101,
  offerName: 'ADSL 8MB',
  serviceType: 'INTERNET',
  price: 100,
};
const RESOURCE: OfferResponse = {
  offerId: 102,
  offerName: 'Modem',
  serviceType: 'RESOURCE',
  price: 50,
};
const ACTIVATION: OfferResponse = {
  offerId: 103,
  offerName: 'Setup',
  serviceType: 'ACTIVATION',
  price: 25,
};
const SECOND_INTERNET: OfferResponse = {
  offerId: 104,
  offerName: 'VDSL 16MB',
  serviceType: 'INTERNET',
  price: 150,
};

const CAMPAIGN: CampaignResponse = {
  campaignId: 'CMP-ADSL-01',
  campaignName: 'ADSL Hosgeldin',
  description: null,
  activationEndDate: null,
  totalPrice: 175,
  offers: [
    { ...INTERNET, main: true },
    { ...RESOURCE, main: false },
    { ...ACTIVATION, main: false },
  ],
};

function issueKeys(store: SaleBasketStore): readonly string[] {
  return store.compositionIssues().map((i) => i.messageKey);
}

describe('SaleBasketStore', () => {
  let store: SaleBasketStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [SaleBasketStore] });
    store = TestBed.inject(SaleBasketStore);
    store.setContext(1261000010, '2240000015');
  });

  it('starts empty', () => {
    expect(store.isEmpty()).toBe(true);
    expect(store.count()).toBe(0);
    expect(store.totalAmount()).toBe(0);
  });

  // --- AC-SALE-01-16: nothing is ever persisted ------------------------------
  describe('AC-SALE-01-16 — the basket never touches Storage', () => {
    it('writes nothing to localStorage or sessionStorage', () => {
      const localSet = vi.spyOn(Storage.prototype, 'setItem');
      const localRemove = vi.spyOn(Storage.prototype, 'removeItem');

      store.addOffer(INTERNET);
      store.addCampaign(CAMPAIGN);
      store.setValue(101, 5, 'abc');
      store.setServiceAddressId(9);
      store.remove(101);
      store.clear();

      expect(localSet).not.toHaveBeenCalled();
      expect(localRemove).not.toHaveBeenCalled();
      // The mock kept the basket in `eds_sale_basket` and friends; that is
      // deliberately not carried over (scope §3.7).
      expect(localStorage.getItem('eds_sale_basket')).toBeNull();
      expect(sessionStorage.getItem('eds_sale_basket')).toBeNull();

      localSet.mockRestore();
      localRemove.mockRestore();
    });

    it('loses everything when the store instance dies (no shared state)', () => {
      store.addOffer(INTERNET);
      expect(store.count()).toBe(1);

      // A fresh injector stands in for leaving and re-entering the wizard: the
      // store is component-provided, so this IS what the user gets.
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({ providers: [SaleBasketStore] });
      expect(TestBed.inject(SaleBasketStore).isEmpty()).toBe(true);
    });
  });

  // --- AC-SALE-01-03/04/05/06 ------------------------------------------------
  it('adds offers and sums their prices (AC-SALE-01-03)', () => {
    store.addOffer(INTERNET);
    store.addOffer(RESOURCE);
    expect(store.count()).toBe(2);
    expect(store.totalAmount()).toBe(150);
  });

  it('adds every member offer of a campaign at once (AC-SALE-01-04)', () => {
    expect(store.addCampaign(CAMPAIGN)).toBe(true);
    expect(store.items().map((i) => i.offer.offerId)).toEqual([101, 102, 103]);
    expect(store.campaignId()).toBe('CMP-ADSL-01');
  });

  it('refuses a campaign whose member is already in the basket, adding NOTHING (AC-SALE-01-05)', () => {
    store.addOffer(RESOURCE);
    expect(store.addCampaign(CAMPAIGN)).toBe(false);
    // All-or-nothing: the two offers that were not duplicates stayed out too.
    expect(store.items().map((i) => i.offer.offerId)).toEqual([102]);
  });

  it('drops an offer and its configuration together (AC-SALE-01-06)', () => {
    store.addOffer(INTERNET);
    store.setValue(101, 5, 'user typed this');
    store.remove(101);
    expect(store.isEmpty()).toBe(true);
    expect(store.valueOf(101, 5)).toBe('');
  });

  it('keeps the service address when the basket is cleared (AC-SALE-01-06)', () => {
    store.addOffer(INTERNET);
    store.setServiceAddressId(42);
    store.clear();
    // The address belongs to the customer, not to the basket.
    expect(store.serviceAddressId()).toBe(42);
    expect(store.isEmpty()).toBe(true);
  });

  // --- AC-SALE-01-08 composition rule ---------------------------------------
  describe('AC-SALE-01-08 — exactly one INTERNET + RESOURCE + ACTIVATION', () => {
    it('names all three as missing when the basket is empty', () => {
      expect(issueKeys(store)).toEqual([
        'MSG-SALE-NO-INTERNET',
        'MSG-SALE-NO-RESOURCE',
        'MSG-SALE-NO-ACTIVATION',
      ]);
    });

    it('reports no issue for a valid three-item basket', () => {
      store.addOffer(INTERNET);
      store.addOffer(RESOURCE);
      store.addOffer(ACTIVATION);
      expect(issueKeys(store)).toEqual([]);
    });

    it('names the missing type only', () => {
      store.addOffer(INTERNET);
      store.addOffer(RESOURCE);
      expect(issueKeys(store)).toEqual(['MSG-SALE-NO-ACTIVATION']);
    });

    it('names a duplicated type with its own MULTI key', () => {
      store.addOffer(INTERNET);
      store.addOffer(SECOND_INTERNET);
      store.addOffer(RESOURCE);
      store.addOffer(ACTIVATION);
      expect(issueKeys(store)).toEqual(['MSG-SALE-MULTI-INTERNET']);
    });

    it('reports missing and duplicated types side by side, in backend order', () => {
      store.addOffer(RESOURCE);
      store.addOffer(INTERNET);
      store.addOffer(SECOND_INTERNET);
      expect(issueKeys(store)).toEqual(['MSG-SALE-MULTI-INTERNET', 'MSG-SALE-NO-ACTIVATION']);
    });
  });

  // --- toRequest -------------------------------------------------------------
  describe('toRequest', () => {
    it('returns null while the flow is incomplete', () => {
      expect(store.toRequest()).toBeNull(); // empty basket
      store.addOffer(INTERNET);
      expect(store.toRequest()).toBeNull(); // no service address
    });

    it('builds the wire body with the campaign id when the basket has one origin', () => {
      store.addCampaign(CAMPAIGN);
      store.setServiceAddressId(7);
      expect(store.toRequest()).toEqual({
        accountNumber: '2240000015',
        serviceAddressId: 7,
        campaignId: 'CMP-ADSL-01',
        items: [
          { offerId: 101, characteristics: [] },
          { offerId: 102, characteristics: [] },
          { offerId: 103, characteristics: [] },
        ],
      });
    });

    it('omits campaignId for a basket assembled offer-by-offer', () => {
      store.addOffer(INTERNET);
      store.setServiceAddressId(7);
      expect(store.toRequest()).not.toHaveProperty('campaignId');
    });

    it('drops blank OPTIONAL values but still sends blank MANDATORY ones', () => {
      store.addOffer(INTERNET);
      store.setServiceAddressId(7);
      store.setMandatoryIds(new Set([1]));
      store.setValue(101, 1, '   '); // mandatory, blank
      store.setValue(101, 2, ''); // optional, blank
      store.setValue(101, 3, 'kept');

      // The blank mandatory value travels so the SERVER answers
      // MSG-VAL-CHAR-REQUIRED and stays the single authority.
      expect(store.toRequest()?.items[0]?.characteristics).toEqual([
        { characteristicId: 1, value: '   ' },
        { characteristicId: 3, value: 'kept' },
      ]);
    });
  });
});
