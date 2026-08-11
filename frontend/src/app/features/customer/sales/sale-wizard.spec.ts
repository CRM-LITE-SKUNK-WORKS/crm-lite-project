import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideCoreHttp } from '../../../core/http';
import {
  type CampaignResponse,
  type CharacteristicResponse,
  type OfferResponse,
} from '../../../core/catalog';
import { type OrderStatusResponse } from '../../order';
import { type AddressResponse } from '../model';
import { CustomerFlashService } from '../state/customer-flash.service';
import { SaleWizard } from './sale-wizard';

/**
 * Sale wizard spec (FR-SALE-01/02, §2.7). House style: data-testid selection,
 * rendered-copy assertions, HTTP faked at the boundary so the real stores, api
 * services and core interceptors all run.
 *
 * What this file exists to pin: Next is gated by the basket, every MSG-SALE-*
 * composition branch renders its OWN message, the confirm dialog gates Submit,
 * a second Submit can never leave the client, and nothing is written to
 * Storage anywhere in the flow.
 */
const CUSTOMER = 1261000010;
const ACCOUNT = '2240000015';

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
const OFFERS = [INTERNET, RESOURCE, ACTIVATION, SECOND_INTERNET];

const CAMPAIGNS: readonly CampaignResponse[] = [
  {
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
  },
];

const PRIMARY_ADDRESS: AddressResponse = {
  addressId: 1,
  cityId: 34,
  cityName: 'Istanbul',
  districtId: 3401,
  districtName: 'Kadikoy',
  street: 'Bagdat Cad.',
  houseFlatNumber: '12/4',
  addressDescription: 'Home',
  primary: true,
};

/**
 * One characteristic of every contract data type, mandatory and optional alike
 * — AC-SALE-01-17/18/19 are about the DIFFERENCES between them, so a fixture
 * with a single TEXT field could not express any of them. Offer 103 gets NO
 * characteristics at all, which is AC-SALE-01-21's case.
 */
const CHAR_TEXT: CharacteristicResponse = {
  characteristicId: 11,
  name: 'XDSL No',
  description: null,
  dataType: 'TEXT',
  mandatory: true,
};
const CHAR_NUMBER: CharacteristicResponse = {
  characteristicId: 12,
  name: 'Download Speed (Mbps)',
  description: null,
  dataType: 'NUMBER',
  mandatory: true,
};
const CHAR_BOOLEAN: CharacteristicResponse = {
  characteristicId: 13,
  name: 'Static IP',
  description: null,
  dataType: 'BOOLEAN',
  mandatory: false,
};
const CHAR_DATE: CharacteristicResponse = {
  characteristicId: 14,
  name: 'Commitment End Date',
  description: null,
  dataType: 'DATE',
  mandatory: false,
};

const SCHEMAS: Readonly<Record<number, readonly CharacteristicResponse[]>> = {
  101: [CHAR_TEXT, CHAR_NUMBER, CHAR_BOOLEAN, CHAR_DATE],
  102: [CHAR_TEXT],
  103: [],
  104: [CHAR_TEXT],
};

const ORDER_NUMBER = '2026000018';

/** `POST /api/orders/drafts` → 201 (ADR-018 §6): the WAIT order, created the
 *  instant the wizard opens, before the user has picked a single offer. */
const DRAFT: OrderStatusResponse = {
  orderNumber: ORDER_NUMBER,
  orderStatus: 'WAIT',
  processingStatus: 'DRAFT',
  failureMessageKey: null,
  updatedAt: '2026-08-10T10:00:00Z',
};

/** `POST /api/orders/{n}/submit` → 202: accepted, the saga has started. */
const ACCEPTED: OrderStatusResponse = {
  ...DRAFT,
  orderStatus: 'MIDLWARE',
  processingStatus: 'PROCESSING',
  updatedAt: '2026-08-10T10:00:01Z',
};

/** `GET /api/orders/{n}/status` → 200, terminal success. */
const COMPLETED: OrderStatusResponse = {
  ...ACCEPTED,
  processingStatus: 'COMPLETED',
  updatedAt: '2026-08-10T10:00:05Z',
};

/** `GET /api/orders/{n}/status` → 200, terminal failure. */
const SALE_FAILED: OrderStatusResponse = {
  ...ACCEPTED,
  processingStatus: 'FAILED',
  failureMessageKey: 'MSG-SALE-FAILED',
  updatedAt: '2026-08-10T10:00:05Z',
};

describe('SaleWizard', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideCoreHttp(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'customers/:customerNumber/sales/new', component: SaleWizard },
          { path: 'customers/:customerNumber', children: [] },
        ]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());
  // Some tests below drive the async status poll with `vi.useFakeTimers()`;
  // restoring real timers here means a test that throws mid-poll can never
  // leak fake timers into the next one.
  afterEach(() => vi.useRealTimers());

  // --- helpers ---------------------------------------------------------------
  /** Every entry into the wizard creates exactly one WAIT draft (ADR-018 §6),
   *  in parallel with the catalog fetch — both fire from the same component
   *  creation tick. */
  async function enter(): Promise<ComponentFixture<unknown>> {
    await harness.navigateByUrl(
      `/customers/${CUSTOMER}/sales/new?accountNumber=${ACCOUNT}`,
      SaleWizard,
    );
    flushCatalog();
    flushDraft();
    return harness.fixture;
  }

  function flushCatalog(): void {
    http.expectOne((r) => r.url === '/api/offers').flush([...OFFERS]);
    http.expectOne((r) => r.url === '/api/campaigns').flush([...CAMPAIGNS]);
    harness.detectChanges();
  }

  function flushDraft(response: OrderStatusResponse = DRAFT): void {
    http
      .expectOne((r) => r.method === 'POST' && r.url === '/api/orders/drafts')
      .flush(response, { status: 201, statusText: 'Created' });
    harness.detectChanges();
  }

  function el(id: string): HTMLElement | null {
    return (harness.fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  function text(id: string): string {
    return el(id)?.textContent?.trim() ?? '';
  }

  function click(id: string): void {
    (el(id) as HTMLButtonElement).click();
    harness.detectChanges();
    harness.detectChanges();
  }

  function disabled(id: string): boolean {
    const node = el(id);
    const button = node instanceof HTMLButtonElement ? node : node?.querySelector('button');
    return (button as HTMLButtonElement | null)?.disabled ?? false;
  }

  /** Tick the given offer rows and press "Add to Basket". */
  function addOffers(...offers: readonly OfferResponse[]): void {
    for (const offer of offers) click(`sale-offer-row-${offer.offerId}`);
    click('sale-add-to-basket-button');
  }

  /** Step 1 → step 2, answering the characteristic + address reads. */
  function goToConfig(offerIds: readonly number[]): void {
    click('sale-next-button');
    for (const offerId of offerIds) {
      http
        .expectOne((r) => r.url === `/api/offers/${offerId}/characteristics`)
        .flush([...(SCHEMAS[offerId] ?? [])]);
    }
    http
      .expectOne((r) => r.url === `/api/customers/${CUSTOMER}/addresses`)
      .flush([PRIMARY_ADDRESS]);
    harness.detectChanges();
    harness.detectChanges();
  }

  /** Type into a characteristic control the way a user does — through the real
   *  input element, so `numericOnly` sanitizing runs exactly as in the browser. */
  function type(testId: string, value: string): void {
    const input = el(testId) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    harness.detectChanges();
    harness.detectChanges();
  }

  /** Fill every MANDATORY characteristic of the three-offer basket so Next has
   *  nothing left to complain about (AC-SALE-01-18). */
  function fillMandatory(): void {
    type('sale-char-101-11', 'XDSL-4242');
    type('sale-char-101-12', '100');
    type('sale-char-102-11', 'XDSL-9001');
  }

  /** Step 2 → step 3 with a valid configuration. */
  function goToSummary(): void {
    fillMandatory();
    click('sale-next-button');
  }

  /**
   * REGRESSION GUARD — a scroll region inside the catalog card must never carry
   * a min-height.
   *
   * A `min-h-*` on a `flex-1` scroll box is a flex minimum the card cannot
   * honour on a short window: its children then overflow the card's own box and
   * the card FOOTER — which holds `LBL-ADD-TO-BASKET` — spills outside it and
   * stops receiving clicks. Measured in a real browser at the time this guard
   * was written: at 800px viewport height the button's top third was dead, at
   * 720px all of it was, while at 900px everything looked fine — which is
   * exactly why a unit test that never lays anything out has to check the
   * CLASSES instead.
   *
   * The 180px floor the mock draws lives on the CONTENT inside the box, where
   * it paints the same and constrains nothing.
   */
  it('no scroll region in the catalog card carries a min-height (click-target guard)', async () => {
    await enter();

    const scrollBoxes = Array.from(
      (harness.fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(
        '[data-testid="sale-catalog-card"] .overflow-auto, ' +
          '[data-testid="sale-catalog-card"] .overflow-y-auto',
      ),
    );
    expect(scrollBoxes.length).toBeGreaterThan(0);

    for (const box of scrollBoxes) {
      const floors = [...box.classList].filter((c) => /^min-h-(?!0$)/.test(c));
      expect(floors, `scroll box has a min-height: ${box.className}`).toEqual([]);
      expect(box.classList.contains('min-h-0')).toBe(true);
    }
  });

  // ---- Step 1: basket + composition ----------------------------------------

  it('AC-SALE-01-07: Next is disabled while the basket is empty', async () => {
    await enter();
    expect(disabled('sale-next-button')).toBe(true);

    addOffers(INTERNET);
    expect(disabled('sale-next-button')).toBe(false);
  });

  it('AC-SALE-01-08: each missing service type renders its OWN message key', async () => {
    await enter();
    // Empty basket: the panel shows no rules block at all — the empty state does.
    addOffers(INTERNET);
    expect(el('sale-basket-rule-MSG-SALE-NO-INTERNET')).toBeNull();
    // Each branch renders its OWN sentence, not a shared "basket invalid".
    expect(text('sale-basket-rule-MSG-SALE-NO-RESOURCE')).toBe(
      'A resource offer (e.g. modem) is required to continue.',
    );
    expect(text('sale-basket-rule-MSG-SALE-NO-ACTIVATION')).toBe(
      'An activation service offer is required to continue.',
    );

    addOffers(RESOURCE);
    expect(el('sale-basket-rule-MSG-SALE-NO-RESOURCE')).toBeNull();

    addOffers(ACTIVATION);
    expect(el('sale-basket-rules')).toBeNull(); // a valid three-item basket
  });

  it('AC-SALE-01-08: a duplicated service type renders its own MULTI key', async () => {
    await enter();
    addOffers(INTERNET, SECOND_INTERNET, RESOURCE, ACTIVATION);
    expect(text('sale-basket-rule-MSG-SALE-MULTI-INTERNET')).toBe(
      'Only one internet service offer is allowed.',
    );
    expect(el('sale-basket-rule-MSG-SALE-NO-INTERNET')).toBeNull();
  });

  /**
   * The reported defect: Next moved on to Product Configuration with a basket
   * the order could never be built from, and the composition rule only surfaced
   * two screens later as a SUBMIT rejection. AC-SALE-01-08 puts it on the click.
   */
  it('AC-SALE-01-08: Next with an incomplete basket stays on step 1 and says why', async () => {
    await enter();
    addOffers(INTERNET); // no RESOURCE, no ACTIVATION

    // Advisory while the basket is still being assembled — not an alert yet.
    expect(el('sale-basket-rules')?.getAttribute('role')).toBeNull();

    click('sale-next-button');

    // Step 2 never opened: it loads schemas and addresses on creation, and no
    // such request left the client.
    http.expectNone((r) => r.url.includes('/characteristics'));
    http.expectNone((r) => r.url.endsWith('/addresses'));
    expect(el('sale-catalog-card')).not.toBeNull();
    expect(el('sale-config-card-101')).toBeNull();

    // Same words as before, now as the reason Next was refused.
    expect(el('sale-basket-rules')?.getAttribute('role')).toBe('alert');
    expect(text('sale-basket-rule-MSG-SALE-NO-RESOURCE')).toBe(
      'A resource offer (e.g. modem) is required to continue.',
    );
    expect(text('sale-basket-rule-MSG-SALE-NO-ACTIVATION')).toBe(
      'An activation service offer is required to continue.',
    );
  });

  it('AC-SALE-01-08: TOO MANY of a service type is refused the same way', async () => {
    await enter();
    addOffers(INTERNET, SECOND_INTERNET, RESOURCE, ACTIVATION);

    click('sale-next-button');

    http.expectNone((r) => r.url.includes('/characteristics'));
    expect(el('sale-catalog-card')).not.toBeNull();
    expect(el('sale-basket-rules')?.getAttribute('role')).toBe('alert');
    expect(text('sale-basket-rule-MSG-SALE-MULTI-INTERNET')).toBe(
      'Only one internet service offer is allowed.',
    );
  });

  it('AC-SALE-01-08: fixing the basket lets the SAME Next through', async () => {
    await enter();
    addOffers(INTERNET);
    click('sale-next-button');
    expect(el('sale-catalog-card')).not.toBeNull(); // refused

    addOffers(RESOURCE, ACTIVATION);
    expect(el('sale-basket-rules')).toBeNull(); // the refusal clears itself

    goToConfig([101, 102, 103]);
    expect(el('sale-config-card-101')).not.toBeNull();
  });

  /**
   * Offer-selection visual contract (scope §4.32 — the 2026-08-05 mock
   * alignment round). These are the mock's own rules, all of them observable:
   * tab names, the footer counter's two modes, zebra striping and its
   * precedence, the always-present tick box, and the per-member "Includes" cell.
   */
  describe('Offer selection matches the mock', () => {
    it('names the tabs "Catalog" and "Campaign" (D1)', async () => {
      await enter();
      expect(text('sale-tab-offers')).toBe('Catalog');
      expect(text('sale-tab-campaigns')).toBe('Campaign');
    });

    it('counts ROWS until something is ticked, then the selection (D6)', async () => {
      await enter();
      // Nothing ticked: the size of the list in front of the user — never
      // "0 selected", which was the defect.
      expect(text('sale-selected-count')).toBe('4 offers');

      click('sale-offer-row-101');
      expect(text('sale-selected-count')).toBe('1 selected');

      click('sale-tab-campaigns'); // switching tabs clears the selection
      expect(text('sale-selected-count')).toBe('1 campaigns');
    });

    it('reserves the tick cell on every row, ticked or not (D2)', async () => {
      await enter();
      const box = (id: string): HTMLElement | null =>
        el(id)?.querySelector('td:last-child > span') ?? null;

      // Present and equally sized before any selection — this is what stops the
      // columns shifting left when the first row is ticked.
      expect(box('sale-offer-row-101')?.className).toContain('size-5');
      expect(box('sale-offer-row-102')?.className).toContain('size-5');

      click('sale-offer-row-101');
      expect(box('sale-offer-row-102')?.className).toContain('size-5');
    });

    it('zebra-stripes odd rows, and highlight beats the stripe (D3/D4)', async () => {
      await enter();
      // 0-based: rows 101 and 103 are even (transparent), 102 and 104 odd.
      expect(el('sale-offer-row-101')?.className).toContain('bg-transparent');
      expect(el('sale-offer-row-102')?.className).toContain('bg-page');

      // Selecting an odd row replaces its stripe with the highlight …
      click('sale-offer-row-102');
      expect(el('sale-offer-row-102')?.className).toContain('bg-selected');
      expect(el('sale-offer-row-102')?.className).not.toContain('bg-page');

      // … and adding it to the basket KEEPS the highlight while making the row
      // inert, instead of dropping it back to the stripe.
      click('sale-add-to-basket-button');
      expect(el('sale-offer-row-102')?.className).toContain('bg-selected');
      expect(el('sale-offer-row-102')?.className).toContain('cursor-default');
    });

    it('lists each campaign member on its own line, with a readable type (D5)', async () => {
      await enter();
      click('sale-tab-campaigns');

      // One line per member offer — id · NAME, never the raw serviceType enum.
      expect(text('sale-campaign-row-CMP-ADSL-01-includes-101')).toBe('101 · ADSL 8MB');
      expect(text('sale-campaign-row-CMP-ADSL-01-includes-102')).toBe('102 · Modem');
      expect(text('sale-campaign-row-CMP-ADSL-01-includes-103')).toBe('103 · Setup');

      const cell = el('sale-campaign-row-CMP-ADSL-01')?.querySelectorAll('td')[2];
      expect(cell?.textContent).toContain('Internet package');
      expect(cell?.textContent).toContain('Modem');
      expect(cell?.textContent).toContain('Activation package');
      expect(cell?.textContent).not.toContain('INTERNET');
      expect(cell?.textContent).not.toContain('ACTIVATION');
    });

    it('shows the readable service type in the Catalog Category column too', async () => {
      await enter();
      const category = el('sale-offer-row-101')?.querySelectorAll('td')[2];
      expect(category?.textContent?.trim()).toBe('Internet package');
    });
  });

  // The offer tab and the campaign tab must behave IDENTICALLY once a row is in
  // the basket: filled check, row no longer selectable, Add inert.
  describe('AC-SALE-01-05: an added row cannot be re-added — offers and campaigns alike', () => {
    it('an offer already in the basket is ticked, inert, and cannot re-arm Add', async () => {
      await enter();
      addOffers(INTERNET);

      expect(el('sale-offer-row-101-added')).not.toBeNull(); // the filled check

      click('sale-offer-row-101'); // clicking it again must change nothing
      expect(disabled('sale-add-to-basket-button')).toBe(true);
      expect(el('sale-basket-item-101')).not.toBeNull();
    });

    it('a campaign already in the basket behaves the SAME way', async () => {
      await enter();
      click('sale-tab-campaigns');
      click('sale-campaign-row-CMP-ADSL-01');
      click('sale-add-to-basket-button');

      // All three member offers landed (AC-SALE-01-04) — as ONE grouped row
      // carrying all three, which is how the mock's basket shows a campaign
      // (scope §4.33), not as three separate rows.
      expect(el('sale-basket-item-CMP-ADSL-01')).not.toBeNull();
      expect(el('sale-basket-item-CMP-ADSL-01-member-101')).not.toBeNull();
      expect(el('sale-basket-item-CMP-ADSL-01-member-102')).not.toBeNull();
      expect(el('sale-basket-item-CMP-ADSL-01-member-103')).not.toBeNull();

      // … so the campaign row now reads as ADDED, exactly like an offer row.
      const row = el('sale-campaign-row-CMP-ADSL-01');
      expect(el('sale-campaign-row-CMP-ADSL-01-added')).not.toBeNull();

      // The mock keeps a basket row highlighted PERMANENTLY and makes it inert
      // (scope §4.32): the row hands over from "selected" to "in basket", it
      // does not go back to plain. Clicking it again changes nothing.
      expect(row?.className).toContain('bg-selected');
      expect(row?.className).toContain('cursor-default');

      click('sale-campaign-row-CMP-ADSL-01'); // must not re-arm Add
      expect(row?.className).toContain('bg-selected');
      expect(disabled('sale-add-to-basket-button')).toBe(true);
    });

    it('a campaign only PARTLY in the basket stays selectable and answers with the message', async () => {
      await enter();
      addOffers(RESOURCE); // one of the three members

      click('sale-tab-campaigns');
      // Partial overlap is NOT "added": greying it out would hide the reason.
      expect(el('sale-campaign-row-CMP-ADSL-01-added')).toBeNull();

      click('sale-campaign-row-CMP-ADSL-01');
      expect(disabled('sale-add-to-basket-button')).toBe(false);
      click('sale-add-to-basket-button');
      expect(el('sale-dup-offer-warning')).not.toBeNull();
    });
  });

  it('AC-SALE-01-05: a campaign containing a basket offer shows MSG-SALE-DUP-OFFER and adds nothing', async () => {
    await enter();
    addOffers(RESOURCE);

    click('sale-tab-campaigns');
    click('sale-campaign-row-CMP-ADSL-01');
    click('sale-add-to-basket-button');

    expect(el('sale-dup-offer-warning')).not.toBeNull();
    expect(text('sale-dup-offer-warning')).toContain('already');
    // All-or-nothing: only the offer that was there before is in the basket.
    expect(el('sale-basket-item-102')).not.toBeNull();
    expect(el('sale-basket-item-101')).toBeNull();
    expect(el('sale-basket-item-103')).toBeNull();
  });

  /**
   * The basket panel's shape (scope §4.33). The mock groups a campaign into ONE
   * entry that carries its member offers; the wire and the composition rules
   * keep working on the flat offer list underneath.
   */
  describe('the basket panel groups a campaign into one entry', () => {
    function addCampaign(): void {
      click('sale-tab-campaigns');
      click('sale-campaign-row-CMP-ADSL-01');
      click('sale-add-to-basket-button');
    }

    it('shows the campaign name, its code and every member offer with its price', async () => {
      await enter();
      addCampaign();

      const row = el('sale-basket-item-CMP-ADSL-01') as HTMLElement;
      expect(row.textContent).toContain('ADSL Hosgeldin'); // campaign NAME first
      expect(row.textContent).toContain('CMP-ADSL-01 · Campaign');
      // Members: `{offerId} · {offerName}` + a readable service type, never the
      // raw enum, and each with its own price.
      expect(text('sale-basket-item-CMP-ADSL-01-member-101')).toBe('101 · ADSL 8MB');
      expect(row.textContent).toContain('Internet package');
      expect(row.textContent).not.toContain('INTERNET');
      expect(row.textContent).toContain('100.00 TL'); // member price
      expect(row.textContent).toContain('175.00 TL'); // bundle total on the row
    });

    it('counts ENTRIES, so a three-offer campaign reads "1 item"', async () => {
      await enter();
      addCampaign();
      expect(text('sale-basket-count')).toBe('1 item');

      // A loose offer alongside it makes two ENTRIES, not four offers.
      click('sale-tab-offers');
      addOffers(SECOND_INTERNET);
      expect(text('sale-basket-count')).toBe('2 items');
    });

    it('removes the whole bundle from the one control the group offers', async () => {
      await enter();
      addCampaign();
      // One remove control for the group — the mock draws none per member.
      expect(el('sale-basket-item-CMP-ADSL-01-member-101-remove')).toBeNull();

      click('sale-basket-item-CMP-ADSL-01-remove');

      // All-or-nothing on the way out, mirroring AC-SALE-01-04's add.
      expect(el('sale-basket-item-CMP-ADSL-01')).toBeNull();
      expect(el('sale-basket-empty')).not.toBeNull();
      // And the rows are selectable again — nothing is left in the basket.
      click('sale-tab-campaigns');
      expect(el('sale-campaign-row-CMP-ADSL-01-added')).toBeNull();
    });

    it('keeps a loose offer as a plain entry: id over name, no nested block', async () => {
      await enter();
      addOffers(INTERNET);

      const row = el('sale-basket-item-101') as HTMLElement;
      expect(row.textContent).toContain('101');
      expect(row.textContent).toContain('ADSL 8MB');
      expect(row.querySelector('[data-testid*="-member-"]')).toBeNull();
      expect(text('sale-basket-count')).toBe('1 item');
    });
  });

  it('AC-SALE-01-06: removing an offer drops it from the basket and the total', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE);
    expect(text('sale-basket-total')).toContain('150.00');

    // The remove control is per ROW now (scope §4.33): a loose offer's row is
    // keyed by its offer id, so this is still "remove offer 101".
    click('sale-basket-item-101-remove');
    expect(el('sale-basket-item-101')).toBeNull();
    expect(text('sale-basket-total')).toContain('50.00');
  });

  // ---- Step 2 → 3 ----------------------------------------------------------

  it('AC-SALE-01-11: the PRIMARY address is preselected, and Next is enabled', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);

    expect(el('sale-address-tile-1')?.getAttribute('aria-pressed')).toBe('true');
    expect(disabled('sale-next-button')).toBe(false);
  });

  it('AC-SALE-01-11/12: with NO address to preselect, Next is refused and says so', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);

    click('sale-next-button');
    for (const offerId of [101, 102, 103]) {
      http
        .expectOne((r) => r.url === `/api/offers/${offerId}/characteristics`)
        .flush([...(SCHEMAS[offerId] ?? [])]);
    }
    // A customer with no address at all: there is nothing to preselect, so the
    // service address AC-SALE-01-11 requires is missing.
    http.expectOne((r) => r.url === `/api/customers/${CUSTOMER}/addresses`).flush([]);
    harness.detectChanges();
    harness.detectChanges();

    // Next is CLICKABLE (a disabled button could not explain itself) and the
    // click is what refuses — the same shape as the characteristic rules.
    expect(disabled('sale-next-button')).toBe(false);
    goToSummary();

    expect(el('sale-summary-card')).toBeNull();
    expect(el('sale-address-required')).not.toBeNull();
  });

  it('AC-SALE-01-20/21: one card per basket offer, each with its characteristic fields', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);

    expect(el('sale-config-card-101')).not.toBeNull();
    expect(el('sale-config-card-102')).not.toBeNull();
    expect(el('sale-config-card-103')).not.toBeNull();
    expect(el('sale-char-101-11')).not.toBeNull();
    // AC-SALE-01-21: offer 103 declares no characteristics. Its card is still
    // there — and, as in the mock, it ENDS at its header: no field grid and no
    // sentence explaining the absence (scope §4.33).
    const emptyCard = el('sale-config-card-103') as HTMLElement;
    expect(emptyCard.querySelectorAll('input, select, textarea').length).toBe(0);
    expect(emptyCard.textContent).toContain('103'); // the header still names it
  });

  // ---- Characteristic input + validation (AC-SALE-01-12/17/18/19) ----------

  describe('AC-SALE-01-17: the control matches the characteristic data type', () => {
    it('a NUMBER field refuses letters outright', async () => {
      await enter();
      addOffers(INTERNET, RESOURCE, ACTIVATION);
      goToConfig([101, 102, 103]);

      // The reported defect: "hıza harf girilebiliyor". It cannot any more —
      // the non-digits never reach the model OR the DOM.
      type('sale-char-101-12', '100abc');
      expect((el('sale-char-101-12') as HTMLInputElement).value).toBe('100');

      // A decimal is still a legal NUMBER (the backend parses BigDecimal), so
      // the filter is not "digits only".
      type('sale-char-101-12', '12.5');
      expect((el('sale-char-101-12') as HTMLInputElement).value).toBe('12.5');
    });

    it('BOOLEAN renders a select of true/false and DATE renders the date picker', async () => {
      await enter();
      addOffers(INTERNET, RESOURCE, ACTIVATION);
      goToConfig([101, 102, 103]);

      // A BOOLEAN is a choice, so a format error is structurally unreachable.
      const boolean = el('sale-char-101-13');
      expect(boolean?.getAttribute('role')).toBe('combobox');
      boolean?.click();
      harness.detectChanges();
      expect(text('sale-char-101-13-panel')).toContain('Yes');
      expect(text('sale-char-101-13-panel')).toContain('No');

      // A DATE gets the masked picker (its calendar trigger proves which one).
      expect(el('sale-char-101-14-trigger')).not.toBeNull();
    });
  });

  it('AC-SALE-01-12/18: Next with a blank mandatory field stays on step 2 and says why', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);

    // Nothing filled in: this is the reported defect — the step could be
    // skipped and the complaint only surfaced on the order summary.
    click('sale-next-button');

    expect(el('sale-summary-card')).toBeNull(); // never reached step 3
    expect(el('sale-config-card-101')).not.toBeNull();
    expect(text('sale-char-101-11-field-error')).toBe('This field is required.');
    expect(text('sale-char-101-12-field-error')).toBe('This field is required.');
    expect(el('sale-config-invalid-banner')).not.toBeNull();

    // An OPTIONAL characteristic left blank is not an error.
    expect(text('sale-char-101-13-field-error')).toBe('');

    // Filling them clears the messages and lets Next through.
    goToSummary();
    expect(el('sale-summary-card')).not.toBeNull();
  });

  it('AC-SALE-01-19: a value that does not fit the data type blocks Next with MSG-VAL-CHAR-FORMAT', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);

    fillMandatory();
    // "." survives the numeric filter (it is a legal decimal separator) but is
    // not a number — precisely the AC-SALE-01-19 case the SERVER would reject.
    type('sale-char-101-12', '.');
    click('sale-next-button');

    expect(el('sale-summary-card')).toBeNull();
    expect(text('sale-char-101-12-field-error')).toBe(
      'Please enter a value matching the field type.',
    );

    type('sale-char-101-12', '.5');
    click('sale-next-button');
    expect(el('sale-summary-card')).not.toBeNull();
  });

  it('AC-SALE-01-19: a half-typed DATE is a format error, not a silent null', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);

    fillMandatory();
    type('sale-char-101-14', '99.99.2026');
    click('sale-next-button');

    expect(el('sale-summary-card')).toBeNull();
    expect(text('sale-char-101-14-field-error')).toBe(
      'Please enter a value matching the field type.',
    );
  });

  it('AC-SALE-01-14: a rejected Next does not lose what was already typed', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);

    type('sale-char-101-11', 'XDSL-4242'); // 101-12 and 102-11 stay blank
    click('sale-next-button');

    expect(el('sale-summary-card')).toBeNull();
    expect((el('sale-char-101-11') as HTMLInputElement).value).toBe('XDSL-4242');
  });

  it('AC-SALE-01-13/14: Previous returns with the basket AND the typed configuration intact', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);

    const input = el('sale-char-101-11') as HTMLInputElement;
    input.value = 'XDSL-4242';
    input.dispatchEvent(new Event('input'));
    harness.detectChanges();

    click('sale-previous-button');
    // Step 1 remounts, so it refetches the catalog — the BASKET is what has to
    // survive, and it does, because it lives in the wizard-scoped store.
    flushCatalog();
    expect(el('sale-basket-item-101')).not.toBeNull();

    goToConfig([101, 102, 103]);
    expect((el('sale-char-101-11') as HTMLInputElement).value).toBe('XDSL-4242');
  });

  // ---- Step 3: submit (ADR-018 §6 — draft, 202 submit, poll) ---------------

  /** The store's own polling interval (order-submit.store.ts). Duplicated
   *  here deliberately: a test asserting observable timing behaves the same
   *  whether or not it happens to share a literal with the implementation. */
  const POLL_INTERVAL_MS = 2_000;

  /** Confirms and answers `202`; leaves the poll timer PENDING (fake timers
   *  must already be active — see the tests that call this). */
  function submitAndAccept(response: OrderStatusResponse = ACCEPTED): void {
    click('sale-submit-button');
    click('sale-submit-confirm-dialog-confirm-button');
    http
      .expectOne((r) => r.method === 'POST' && r.url === `/api/orders/${ORDER_NUMBER}/submit`)
      .flush(response, { status: 202, statusText: 'Accepted' });
    harness.detectChanges();
  }

  /** Advances to the first poll and answers it with a terminal status. */
  function resolvePoll(status: OrderStatusResponse): void {
    vi.advanceTimersByTime(POLL_INTERVAL_MS);
    http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/status`).flush(status, {
      status: 200,
      statusText: 'OK',
    });
    harness.detectChanges();
  }

  it('AC-SALE-01-12: the summary shows the REAL Order Number from the draft, before Submit', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();

    // The analyst's 2026-08-10 clarification (ADR-018 §1.1): the WAIT draft
    // already exists from Start New Sale, so its real KR-12 number is here —
    // never an em dash, never a client-invented placeholder.
    expect(text('sale-summary-order-number')).toBe(ORDER_NUMBER);
    expect(text('sale-summary-total')).toContain('175.00');
    expect(text('sale-summary-address')).toContain('Kadikoy');
  });

  it('shows a loading label while the draft is still being created, and locks Submit', async () => {
    await harness.navigateByUrl(
      `/customers/${CUSTOMER}/sales/new?accountNumber=${ACCOUNT}`,
      SaleWizard,
    );
    flushCatalog(); // catalog resolves; the draft POST is still open
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();

    expect(text('sale-summary-order-number-pending')).toBe('Creating order…');
    expect(el('sale-summary-order-number')).toBeNull();
    expect(disabled('sale-submit-button')).toBe(true);

    flushDraft();
    expect(text('sale-summary-order-number')).toBe(ORDER_NUMBER);
    expect(disabled('sale-submit-button')).toBe(false);
  });

  it('a failed draft creation shows the error and never fabricates an Order Number', async () => {
    await harness.navigateByUrl(
      `/customers/${CUSTOMER}/sales/new?accountNumber=${ACCOUNT}`,
      SaleWizard,
    );
    flushCatalog();
    http
      .expectOne((r) => r.method === 'POST' && r.url === '/api/orders/drafts')
      .flush({ messageKey: 'MSG-ACCT-NOT-ACTIVE' }, { status: 409, statusText: 'Conflict' });
    harness.detectChanges();

    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();

    expect(el('sale-submit-error-MSG-ACCT-NOT-ACTIVE')).not.toBeNull();
    expect(el('sale-summary-order-number-pending')).toBeNull();
    expect(text('sale-summary-order-number')).toBe('—');
    expect(disabled('sale-submit-button')).toBe(true);
  });

  it('AC-SALE-01-15: Submit opens the confirm dialog and sends NOTHING until Yes', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();

    click('sale-submit-button');
    const dialog = el('sale-submit-confirm-dialog') as HTMLElement;
    expect(dialog).not.toBeNull();
    http.expectNone((r) => r.url.endsWith('/submit'));

    // The body states WHICH account and HOW MUCH rather than a bare "are you
    // sure" (scope §4.33) — both read from the sale's own state, and the total
    // formatted the same way the basket formats it.
    expect(dialog.textContent).toContain(ACCOUNT);
    expect(dialog.textContent).toContain('175.00 TL');

    // Declining closes it, sends no async command, and leaves the WAIT draft
    // and the basket exactly as they were.
    click('sale-submit-confirm-dialog-cancel-button');
    http.expectNone((r) => r.url.endsWith('/submit'));
    http.expectNone((r) => r.method === 'DELETE');
    expect(el('sale-summary-card')).not.toBeNull();
    expect(text('sale-summary-order-number')).toBe(ORDER_NUMBER);
  });

  it('AC-SALE-01-15: confirming submits once, shows processing, and leaves for Customer Info on COMPLETED', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();

    vi.useFakeTimers();
    click('sale-submit-button');
    click('sale-submit-confirm-dialog-confirm-button');

    const request = http.expectOne(
      (r) => r.method === 'POST' && r.url === `/api/orders/${ORDER_NUMBER}/submit`,
    );
    // No accountNumber: the draft already fixed the account (ADR-018 §6).
    expect(request.request.body).toEqual({
      serviceAddressId: 1,
      items: [
        {
          offerId: 101,
          characteristics: [
            { characteristicId: 11, value: 'XDSL-4242' },
            { characteristicId: 12, value: '100' },
          ],
        },
        { offerId: 102, characteristics: [{ characteristicId: 11, value: 'XDSL-9001' }] },
        // AC-SALE-01-21: no characteristics at all, and the blank OPTIONAL
        // BOOLEAN/DATE of offer 101 are omitted rather than sent empty.
        { offerId: 103, characteristics: [] },
      ],
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    request.flush(ACCEPTED, { status: 202, statusText: 'Accepted' });
    harness.detectChanges();

    // The in-place "Order received, processing…" state (ADR-018 §6) — the
    // analyst's own MIDLWARE wording, "Sipariş Alındı, İşleniyor…" in Turkish
    // (OrderContract.java), rendered here through the EN catalog entry the
    // test environment defaults to. The Order Number stays visible, and
    // every footer action is locked.
    expect(text('sale-processing-banner')).toContain('Order received, processing');
    expect(text('sale-summary-order-number')).toBe(ORDER_NUMBER);
    expect(disabled('sale-submit-button')).toBe(true);
    expect(disabled('sale-previous-button')).toBe(true);
    expect(disabled('sale-cancel-button')).toBe(true);
    expect(navigate).not.toHaveBeenCalled();

    resolvePoll(COMPLETED);

    // AC-SALE-01-15 success LEAVES (scope §4.33): straight to Customer Info,
    // on the account tab, with the account of the order already expanded.
    expect(navigate).toHaveBeenCalledWith(['/customers', CUSTOMER], {
      queryParams: { tab: 'account', account: ACCOUNT },
    });

    // The order number is not lost by leaving — it rides along in the flash,
    // together with the product count (read from the BASKET: the async
    // status resource carries no items) and the account.
    expect(TestBed.inject(CustomerFlashService).consume()).toEqual({
      key: 'UI-SALE-TOAST-ORDER-SUBMITTED',
      params: { orderNumber: ORDER_NUMBER, count: 3, accountNumber: ACCOUNT },
    });
  });

  it('AC-SALE-01-15: a terminal FAILED sale keeps the Order Number, shows the backend key, and offers a way back', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();

    vi.useFakeTimers();
    submitAndAccept();
    resolvePoll(SALE_FAILED);

    expect(el('sale-processing-failed-MSG-SALE-FAILED')).not.toBeNull();
    expect(text('sale-summary-order-number')).toBe(ORDER_NUMBER); // never hidden
    expect(disabled('sale-submit-button')).toBe(true);
    expect(disabled('sale-previous-button')).toBe(true);
    expect(disabled('sale-cancel-button')).toBe(true);
    // No success text, no second order attempted from here.
    expect(el('sale-processing-banner')).toBeNull();
    http.expectNone((r) => r.url === '/api/orders/drafts');
    http.expectNone((r) => r.url.endsWith('/submit'));

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    click('sale-return-to-customer-button');
    expect(navigate).toHaveBeenCalledWith(['/customers', CUSTOMER]);
  });

  it('duplicate-submit guard: a second submit can never leave the client', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();

    vi.useFakeTimers();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    submitAndAccept();
    resolvePoll(COMPLETED);

    // The screen navigates away on success, so in practice the button is
    // unreachable. The guard that actually matters is independent of that:
    // Submit stays disabled and cannot even REOPEN the confirm dialog, so no
    // second submit command can be started from the UI (ADR-018 §3).
    expect(disabled('sale-submit-button')).toBe(true);
    click('sale-submit-button');
    expect(el('sale-submit-confirm-dialog-confirm-button')).toBeNull();
    http.expectNone((r) => r.url.endsWith('/submit'));
  });

  it('AC-SALE-01-18: a rejected submit renders the BACKEND message key, not its message, and Submit stays open for retry', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();
    click('sale-submit-button');
    click('sale-submit-confirm-dialog-confirm-button');

    http
      .expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`)
      .flush(
        { messageKey: 'MSG-SALE-OFFER-INACTIVE', message: 'raw backend english' },
        { status: 400, statusText: 'Bad Request' },
      );
    harness.detectChanges();

    expect(el('sale-submit-error-MSG-SALE-OFFER-INACTIVE')).not.toBeNull();
    expect(text('sale-submit-error')).not.toContain('raw backend english');
    // The sale was never accepted, so Submit comes back for a legitimate retry
    // — and it is still the SAME draft, not a second one.
    expect(el('sale-submit-button')).not.toBeNull();
    expect(disabled('sale-submit-button')).toBe(false);
    http.expectNone((r) => r.url === '/api/orders/drafts');
  });

  it('AC-SALE-01-19: per-characteristic rejections are listed, never dropped', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();
    click('sale-submit-button');
    click('sale-submit-confirm-dialog-confirm-button');

    http
      .expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`)
      .flush(
        {
          messageKey: 'MSG-VALIDATION-ERROR',
          validationErrors: { 'items[0].characteristics[0].value': 'MSG-VAL-CHAR-REQUIRED' },
        },
        { status: 400, statusText: 'Bad Request' },
      );
    harness.detectChanges();

    expect(text('sale-submit-error')).toContain('required');
  });

  // ---- AC-SALE-01-16: nothing survives the flow ----------------------------

  it('AC-SALE-01-16: the whole flow writes nothing to Storage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');

    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();

    vi.useFakeTimers();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    submitAndAccept();
    resolvePoll(COMPLETED);

    // The mock stashed eds_sale_basket / eds_sale_address / eds_sale_config /
    // eds_pending_sale; none of that is carried over (scope §3.7).
    const keys = setItem.mock.calls.map((call) => String(call[0]));
    expect(keys.filter((key) => key.startsWith('eds_sale'))).toEqual([]);
    expect(keys).not.toContain('eds_pending_sale');
    setItem.mockRestore();
  });

  it('AC-SALE-01-16: pre-submit Cancel abandons the WAIT draft and destroys the basket', async () => {
    await enter();
    addOffers(INTERNET);
    expect(el('sale-basket-item-101')).not.toBeNull();

    click('sale-cancel-button');
    // The draft is abandoned best-effort — no product and no saga are ever
    // started for it (ADR-018 §6).
    http
      .expectOne((r) => r.method === 'DELETE' && r.url === `/api/orders/${ORDER_NUMBER}/draft`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    // Re-entering starts from an empty basket: the store died with the wizard,
    // so an abandoned sale cannot be picked up and processed later.
    await enter();
    expect(el('sale-basket-empty')).not.toBeNull();
    expect(el('sale-basket-item-101')).toBeNull();
  });

  it('ADR-018 §6: once accepted, Cancel is disabled and never sends an abandon for the submitted order', async () => {
    await enter();
    addOffers(INTERNET, RESOURCE, ACTIVATION);
    goToConfig([101, 102, 103]);
    goToSummary();

    vi.useFakeTimers();
    submitAndAccept();

    expect(disabled('sale-cancel-button')).toBe(true);
    click('sale-cancel-button'); // disabled buttons still don't fire — belt and suspenders
    http.expectNone((r) => r.method === 'DELETE');

    resolvePoll(COMPLETED);
  });
});
