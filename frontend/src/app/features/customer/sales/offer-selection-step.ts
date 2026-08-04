import { Component, ElementRef, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import {
  CatalogApiService,
  type CampaignResponse,
  type OfferResponse,
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
import { SaleBasketStore } from '../../order';

type Tab = 'offers' | 'campaigns';

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
  protected readonly categoryOptions = computed<readonly SelectOption[]>(() => [
    { value: '', label: this.i18n.translate('UI-SALE-FILTER-CATEGORY-ALL') },
    ...[...new Set(this.offers().map((o) => o.serviceType))].map((t) => ({ value: t, label: t })),
  ]);

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

  protected readonly selectedCountLabel = computed(() =>
    this.i18n.translate('UI-SALE-SELECTED-COUNT', { count: this.selectedCount() }),
  );

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

  protected removeFromBasket(offerId: number): void {
    this.basket.remove(offerId);
  }

  protected clearBasket(): void {
    this.basket.clear();
  }

  protected readonly basketCountLabel = computed(() =>
    this.i18n.translate('UI-SALE-BASKET-COUNT', { count: this.basket.count() }),
  );

  protected price(value: number): string {
    return `${value.toFixed(2)} TL`;
  }

  protected includesLabel(campaign: CampaignResponse): string {
    return campaign.offers.map((o) => `${o.offerId} · ${o.serviceType}`).join(', ');
  }
}
