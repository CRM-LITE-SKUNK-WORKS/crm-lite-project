import { Component, ElementRef, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import {
  CatalogApiService,
  type CampaignOffer,
  type CampaignResponse,
  type OfferResponse,
  type ServiceType,
} from '../../../core/catalog';
import { isApiError } from '../../../core/http';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { EmptyState, Skeleton } from '../../../shared/patterns';
import {
  Button,
  FormField,
  Icon,
  IconButton,
  Select,
  TextInput,
  type SelectOption,
} from '../../../shared/ui';
import { SaleBasketStore, type BasketItem } from '../../order';
import { formatPrice } from './money';

type Tab = 'offers' | 'campaigns';

/**
 * `serviceType` → catalogue key. The wire value is a contract enum
 * (`INTERNET` / `RESOURCE` / `ACTIVATION`) and is never printed as-is; every
 * place that shows a service type — the Category filter, the Category column
 * and the campaign "Includes" column — resolves it through here (scope §4.32).
 */
const SERVICE_TYPE_KEYS: Record<ServiceType, string> = {
  INTERNET: 'UI-SALE-SERVICE-TYPE-INTERNET',
  RESOURCE: 'UI-SALE-SERVICE-TYPE-RESOURCE',
  ACTIVATION: 'UI-SALE-SERVICE-TYPE-ACTIVATION',
};

/** One line of the campaign "Includes" cell — the mock renders every member
 *  offer on its OWN row, `{offerId} · {offerName}` next to its type label. */
export interface CampaignIncludeLine {
  readonly offerId: number;
  readonly label: string;
  readonly typeLabel: string;
}

/** One member offer nested under a campaign row of the basket panel. Same two
 *  lines as {@link CampaignIncludeLine}, plus the price the mock right-aligns. */
export interface BasketMemberView {
  readonly offerId: number;
  readonly label: string;
  readonly typeLabel: string;
  readonly priceLabel: string;
}

/**
 * One ROW of the basket panel — fully resolved for rendering (scope §4.33).
 *
 * Exactly one of `campaignId` / `offerId` is set: that is what the row's remove
 * control acts on. A campaign row carries `members`; an offer row's `members`
 * is empty, so the template needs no second branch for the nested block.
 */
export interface BasketRowView {
  readonly key: string;
  readonly campaignId: string | null;
  readonly offerId: number | null;
  readonly testId: string;
  /** Campaign row: the campaign name. Offer row: the offer id. */
  readonly title: string;
  /** Campaign row: `{campaignId} · Campaign`. Offer row: the offer name. */
  readonly subtitle: string;
  readonly priceLabel: string;
  readonly members: readonly BasketMemberView[];
}

/**
 * Step 1 — Offer selection (AC-SALE-01-03..08).
 *
 * Mock layout (scope §2B.7): a `1fr | 360px` grid — the tabbed catalog card on
 * the left, the Basket panel on the right.
 *
 * Filtering is CLIENT-SIDE on purpose: `GET /api/offers` and `GET /api/campaigns`
 * take no query parameters and return plain arrays with no pagination
 * (scope §2B.4). Inventing `?name=` would be inventing a contract, so the
 * screen fetches once and narrows in memory.
 */
@Component({
  selector: 'app-offer-selection-step',
  imports: [
    TranslatePipe,
    ReactiveFormsModule,
    EmptyState,
    Skeleton,
    Button,
    Icon,
    IconButton,
    FormField,
    TextInput,
    Select,
  ],
  templateUrl: './offer-selection-step.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
})
export class OfferSelectionStep {
  private readonly api = inject(CatalogApiService);
  private readonly i18n = inject(I18nService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly basket = inject(SaleBasketStore);

  protected readonly tab = signal<Tab>('offers');
  protected readonly isOffers = computed(() => this.tab() === 'offers');

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  private readonly offers = signal<readonly OfferResponse[]>([]);
  private readonly campaigns = signal<readonly CampaignResponse[]>([]);

  /** Applied filters (only updated on `LBL-SEARCH`, matching the mock's explicit
   *  Search button — typing does not re-filter). */
  private readonly offerFilter = signal({ category: '', id: '', name: '' });
  private readonly campaignFilter = signal({ campaign: '', id: '', name: '' });

  /** Draft filter inputs. Reactive Forms (FE-ADR-007): `TextInput`/`Select` are
   *  ControlValueAccessors, so they bind to controls, never to raw signals. */
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly offerForm = this.fb.group({ category: '', id: '', name: '' });
  protected readonly campaignForm = this.fb.group({ campaign: '', id: '', name: '' });

  /** Rows ticked but not yet added — the mock lets the user select several and
   *  press `LBL-ADD-TO-BASKET` once. */
  private readonly selectedOfferIds = signal<ReadonlySet<number>>(new Set());
  private readonly selectedCampaignId = signal<string | null>(null);

  /** AC-SALE-01-05: a campaign containing an offer already in the basket must
   *  show `MSG-SALE-DUP-OFFER` and must NOT be added. */
  protected readonly dupWarning = signal<string | null>(null);

  /** Set by {@link validate}. Before the first `LBL-NEXT` the composition list
   *  is ADVISORY — the user is still assembling the basket and has not claimed
   *  to be finished; after a refused Next it is the REASON they were refused, so
   *  it changes tone and announces itself. */
  private readonly nextAttempted = signal(false);

  protected readonly compositionRejected = computed(
    () => this.nextAttempted() && this.basket.compositionIssues().length > 0,
  );

  constructor() {
    this.load();
  }

  /**
   * AC-SALE-01-08 — the `LBL-NEXT` gate, called by the wizard footer. Answers
   * `true` when Product Configuration may open.
   *
   * WHY A CLICK-TIME GATE AND NOT A DISABLED BUTTON: the AC says the user
   * presses Next and is then TOLD which rule failed. A disabled button can never
   * say why — it can only refuse silently, leaving "one internet, one resource,
   * one activation" as a rule the user has to guess. `LBL-NEXT` stays disabled
   * for the EMPTY basket only (AC-SALE-01-07), where there is nothing to explain.
   *
   * The messages themselves are already on screen as the basket's composition
   * preview; this only escalates their tone and scrolls them into view, so the
   * refusal and its explanation are the same words in the same place rather than
   * a second, competing sentence.
   *
   * "Every offer in the basket must be ACTIVE" is the one clause of the AC with
   * nothing to check here: `GET /api/offers` and `GET /api/campaigns` return
   * ACTIVE rows only, so an inactive offer cannot be picked in the first place.
   * It stays a real server rule — product-service re-reads every offer at submit
   * and answers `MSG-SALE-OFFER-INACTIVE` if one was passivated meanwhile, which
   * step 3 renders (FE-ADR-007 §3). Inventing a client check for it would only
   * be able to lie.
   */
  validate(): boolean {
    this.nextAttempted.set(true);
    if (this.basket.compositionIssues().length === 0) return true;
    // The basket panel can sit below the fold on a short window; a rule the user
    // cannot see is a rule they cannot act on.
    this.host.nativeElement
      .querySelector<HTMLElement>('[data-testid="sale-basket-rules"]')
      ?.scrollIntoView({ block: 'center' });
    return false;
  }

  private load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.listOffers().subscribe({
      next: (rows) => {
        this.offers.set(rows);
        this.api.listCampaigns().subscribe({
          next: (c) => {
            this.campaigns.set(c);
            this.loading.set(false);
          },
          error: (err: unknown) => this.fail(err),
        });
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  private fail(err: unknown): void {
    this.loadError.set(isApiError(err) ? err.messageKey : 'UI-ERROR-GENERIC');
    this.loading.set(false);
  }

  protected retry(): void {
    this.load();
  }

  // --- Filter options -------------------------------------------------------
  /** The option VALUE stays the raw contract enum (it is what the filter
   *  compares against); only the label is resolved (scope §4.32). */
  protected readonly categoryOptions = computed<readonly SelectOption[]>(() => [
    { value: '', label: this.i18n.translate('UI-SALE-FILTER-CATEGORY-ALL') },
    ...[...new Set(this.offers().map((o) => o.serviceType))].map((type) => ({
      value: type,
      label: this.serviceTypeLabel(type),
    })),
  ]);

  /** Readable name of a contract `serviceType`. Never the enum itself. */
  protected serviceTypeLabel(type: ServiceType): string {
    return this.i18n.translate(SERVICE_TYPE_KEYS[type]);
  }

  protected readonly campaignOptions = computed<readonly SelectOption[]>(() => [
    { value: '', label: this.i18n.translate('UI-SALE-FILTER-CAMPAIGN-ALL') },
    ...this.campaigns().map((c) => ({ value: c.campaignId, label: c.campaignName })),
  ]);

  // --- Rows -----------------------------------------------------------------
  protected readonly offerRows = computed(() => {
    const f = this.offerFilter();
    return this.offers().filter(
      (o) =>
        (f.category === '' || o.serviceType === f.category) &&
        (f.id === '' || String(o.offerId).includes(f.id.trim())) &&
        (f.name === '' ||
          o.offerName.toLocaleLowerCase().includes(f.name.trim().toLocaleLowerCase())),
    );
  });

  protected readonly campaignRows = computed(() => {
    const f = this.campaignFilter();
    return this.campaigns().filter(
      (c) =>
        (f.campaign === '' || c.campaignId === f.campaign) &&
        (f.id === '' ||
          c.campaignId.toLocaleLowerCase().includes(f.id.trim().toLocaleLowerCase())) &&
        (f.name === '' ||
          c.campaignName.toLocaleLowerCase().includes(f.name.trim().toLocaleLowerCase())),
    );
  });

  protected readonly hasRows = computed(() =>
    this.isOffers() ? this.offerRows().length > 0 : this.campaignRows().length > 0,
  );

  // --- Selection ------------------------------------------------------------
  protected isSelected(offerId: number): boolean {
    return this.selectedOfferIds().has(offerId);
  }

  protected isCampaignSelected(campaignId: string): boolean {
    return this.selectedCampaignId() === campaignId;
  }

  protected inBasket(offerId: number): boolean {
    return this.basket.has(offerId);
  }

  /**
   * A campaign counts as ALREADY ADDED when every one of its member offers is
   * in the basket — the campaign-level equivalent of {@link inBasket}, since
   * `addCampaign` is all-or-nothing (AC-SALE-01-04). Its row then gets the same
   * filled check and the same inert `LBL-ADD-TO-BASKET` an added offer gets.
   *
   * PARTIAL overlap is deliberately NOT this state: the campaign has not been
   * added, the user may still select it, and pressing Add answers with
   * `MSG-SALE-DUP-OFFER` (AC-SALE-01-05) — which names the real problem instead
   * of silently greying the row out.
   */
  protected campaignInBasket(campaign: CampaignResponse): boolean {
    return campaign.offers.length > 0 && campaign.offers.every((o) => this.basket.has(o.offerId));
  }

  /** AC-SALE-01-05: a row already in the basket cannot be re-selected — the
   *  mock shows a filled check on it and `LBL-ADD-TO-BASKET` stays inert. */
  protected toggleOffer(offerId: number): void {
    if (this.basket.has(offerId)) return;
    this.dupWarning.set(null);
    this.selectedOfferIds.update((set) => {
      const next = new Set(set);
      if (next.has(offerId)) next.delete(offerId);
      else next.add(offerId);
      return next;
    });
  }

  /** Mirrors {@link toggleOffer}: a campaign already in the basket cannot be
   *  re-selected (AC-SALE-01-05). */
  protected toggleCampaign(campaignId: string): void {
    const campaign = this.campaigns().find((c) => c.campaignId === campaignId);
    if (campaign && this.campaignInBasket(campaign)) return;
    this.dupWarning.set(null);
    this.selectedCampaignId.update((id) => (id === campaignId ? null : campaignId));
  }

  protected readonly selectedCount = computed(() =>
    this.isOffers() ? this.selectedOfferIds().size : this.selectedCampaignId() ? 1 : 0,
  );

  /**
   * Card footer counter, mock-verbatim (`selCountLabel`): with a selection it
   * reads "{n} selected"; with NONE it falls back to the size of the list the
   * user is looking at — "{n} offers" on Catalog, "{n} campaigns" on Campaign.
   * Showing "0 selected" on an untouched screen was the defect (D6).
   *
   * No plural engine, per FE-ADR-012 §h.3 — the mock does the same plain
   * interpolation and Turkish does not inflect after a number.
   */
  protected readonly selectedCountLabel = computed(() => {
    const selected = this.selectedCount();
    if (selected > 0) {
      return this.i18n.translate('UI-SALE-SELECTED-COUNT', { count: selected });
    }
    return this.isOffers()
      ? this.i18n.translate('UI-SALE-ROW-COUNT-OFFERS', { count: this.offerRows().length })
      : this.i18n.translate('UI-SALE-ROW-COUNT-CAMPAIGNS', { count: this.campaignRows().length });
  });

  /** Nothing ticked, or the ticked campaign is already in the basket. The
   *  second arm is a belt-and-braces guard: `toggleCampaign` already refuses
   *  such a selection, so this can only matter if the basket changes while a
   *  selection stands. */
  protected readonly addDisabled = computed(() => {
    if (this.selectedCount() === 0) return true;
    if (this.isOffers()) return false;
    const campaign = this.campaigns().find((c) => c.campaignId === this.selectedCampaignId());
    return campaign === undefined || this.campaignInBasket(campaign);
  });

  // --- Actions --------------------------------------------------------------
  protected switchTab(tab: Tab): void {
    this.tab.set(tab);
    this.dupWarning.set(null);
    this.selectedOfferIds.set(new Set());
    this.selectedCampaignId.set(null);
  }

  protected searchOffers(): void {
    this.offerFilter.set(this.offerForm.getRawValue());
  }

  protected searchCampaigns(): void {
    this.campaignFilter.set(this.campaignForm.getRawValue());
  }

  /** AC-SALE-01-03 (offers) / AC-SALE-01-04 (campaign adds all member offers). */
  protected addToBasket(): void {
    this.dupWarning.set(null);
    if (this.isOffers()) {
      for (const id of this.selectedOfferIds()) {
        const offer = this.offers().find((o) => o.offerId === id);
        if (offer) this.basket.addOffer(offer);
      }
      this.selectedOfferIds.set(new Set());
      return;
    }
    const campaignId = this.selectedCampaignId();
    const campaign = this.campaigns().find((c) => c.campaignId === campaignId);
    if (!campaign) return;
    // AC-SALE-01-05: all-or-nothing — a campaign whose member is already in the
    // basket is refused with the analyst's own key, and nothing is added.
    if (!this.basket.addCampaign(campaign)) {
      this.dupWarning.set('MSG-SALE-DUP-OFFER');
      return;
    }
    this.selectedCampaignId.set(null);
  }

  /**
   * The basket panel's rows, mock-verbatim (scope §4.33). A campaign is ONE row
   * carrying its member offers in the indented block; a loose offer is a plain
   * row. Structure comes from `SaleBasketStore.groups` (a projection of the flat
   * item list); the strings are resolved here, because catalogue access belongs
   * to the screen, not to the store.
   *
   * The mock's optional third line (`it.meta`) has no counterpart in
   * `OfferResponse` — it is a mock fixture field — so it is simply never
   * rendered rather than invented.
   */
  protected readonly basketRows = computed<readonly BasketRowView[]>(() =>
    this.basket.groups().map((group) =>
      group.kind === 'campaign'
        ? {
            key: group.key,
            campaignId: group.campaignId,
            offerId: null,
            testId: `sale-basket-item-${group.campaignId}`,
            title: group.campaignName,
            subtitle: this.i18n.translate('UI-SALE-BASKET-CAMPAIGN-SUBTITLE', {
              campaignId: group.campaignId,
            }),
            priceLabel: this.price(group.price),
            members: group.items.map((item: BasketItem) => ({
              offerId: item.offer.offerId,
              label: `${item.offer.offerId} · ${item.offer.offerName}`,
              typeLabel: this.serviceTypeLabel(item.offer.serviceType),
              priceLabel: this.price(item.offer.price),
            })),
          }
        : {
            key: group.key,
            campaignId: null,
            offerId: group.item.offer.offerId,
            testId: `sale-basket-item-${group.item.offer.offerId}`,
            title: String(group.item.offer.offerId),
            subtitle: group.item.offer.offerName,
            priceLabel: this.price(group.price),
            members: [],
          },
    ),
  );

  /** One remove control per ROW: an offer row drops its offer, a campaign row
   *  drops the whole bundle — the mirror of the all-or-nothing add, and the
   *  only removal the mock's grouped panel offers (scope §4.33). */
  protected removeRow(row: BasketRowView): void {
    if (row.campaignId !== null) {
      this.basket.removeCampaign(row.campaignId);
    } else if (row.offerId !== null) {
      this.basket.remove(row.offerId);
    }
  }

  protected clearBasket(): void {
    this.basket.clear();
  }

  /** The mock counts ENTRIES, so an added campaign reads "1 item", not "3".
   *  Singular and plural are two keys, not a plural engine (FE-ADR-012 §h.3). */
  protected readonly basketCountLabel = computed(() => {
    const count = this.basket.entryCount();
    return count === 1
      ? this.i18n.translate('UI-SALE-BASKET-COUNT-ONE')
      : this.i18n.translate('UI-SALE-BASKET-COUNT', { count });
  });

  protected price(value: number): string {
    return formatPrice(value);
  }

  /**
   * The campaign "Includes" cell — ONE LINE PER MEMBER OFFER, as the mock draws
   * it: `{offerId} · {offerName}` in primary text, then the offer's service-type
   * label in smaller tertiary text (D5).
   *
   * Two things were wrong before: every member was joined into a single comma
   * separated line, and the second half printed the raw `serviceType` enum where
   * the mock prints the offer's NAME. Both fields come straight from
   * `GET /api/campaigns` (`offers[].offerId` / `.offerName` / `.serviceType`) —
   * the mock's own ids and titles are fixtures and are not reproduced.
   */
  protected includeLines(campaign: CampaignResponse): readonly CampaignIncludeLine[] {
    return campaign.offers.map((offer: CampaignOffer) => ({
      offerId: offer.offerId,
      label: `${offer.offerId} · ${offer.offerName}`,
      typeLabel: this.serviceTypeLabel(offer.serviceType),
    }));
  }

  /**
   * Row paint, in the mock's own precedence order (D3/D4):
   *   in basket → selected → zebra.
   * A row in the basket stays highlighted PERMANENTLY and stops being
   * clickable; `isSelected` already excludes it (`!inBasket && …`), so adding
   * to the basket hands the row over from the "selected" state to the
   * "in basket" one rather than clearing it.
   *
   * The mock spells the two highlights with the SAME token name
   * (`--eds-color-bg-selected`) but two different inline fallbacks; our theme
   * defines that token once, so both states use it (scope §4.32).
   */
  protected rowClasses(inBasket: boolean, selected: boolean, odd: boolean): string {
    const base = 'border-b border-line transition-colors duration-100 last:border-b-0';
    if (inBasket) return `${base} cursor-default bg-selected`;
    if (selected) return `${base} cursor-pointer bg-selected`;
    // Zebra: the mock shades ODD indices (0-based) with bg-page.
    return `${base} cursor-pointer ${odd ? 'bg-page' : 'bg-transparent'}`;
  }
}
