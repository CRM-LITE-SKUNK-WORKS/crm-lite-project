import { Component, computed, effect, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { ConfirmDialog, Stepper, type StepItem } from '../../../shared/patterns';
import { Button } from '../../../shared/ui';
import { OrderSubmitStore, SaleBasketStore } from '../../order';
import { CustomerDetailStore } from '../state/customer-detail.store';
import { CustomerFlashService } from '../state/customer-flash.service';
import { formatPrice } from './money';
import { OfferSelectionStep } from './offer-selection-step';
import { ProductConfigurationStep } from './product-configuration-step';
import { SubmitOrderStep } from './submit-order-step';

const STEP_IDS = ['offer', 'config', 'summary'] as const;
type StepId = (typeof STEP_IDS)[number];

/**
 * The §2.7 product-sale wizard (FR-SALE-01/02) — ONE route, three steps.
 *
 * WHY ONE ROUTE: all three mock screens render the same step indicator and
 * differ only by the active entry, and AC-SALE-01-13/14 require `LBL-PREVIOUS`
 * to return with the basket and the entered configuration INTACT. Separate
 * routes would rebuild that from a shared store anyway, and a deep link to
 * step 2 would land on an empty basket (nothing is persisted). Same shape as
 * the Create Customer wizard.
 *
 * WHY IT LIVES UNDER features/customer/: AC-SALE-01-11 needs the customer's
 * ADDRESSES and the FR-ADDR-02 dialog. Hosting the wizard in `features/order/`
 * would open a NEW order → customer sibling direction; FE-ADR-003
 * §Consequences only sanctions `customer → sibling` (account §4.24, product
 * §4.28/3). Here customer consumes `features/order/` exactly as it consumes
 * those two.
 *
 * 🔴 NOTHING IS PERSISTED. Both stores are provided HERE, not in root, so
 * leaving the flow destroys the basket with the component (AC-SALE-01-16). A
 * refresh restarts at step 1 — required behaviour, not a defect.
 */
@Component({
  selector: 'app-sale-wizard',
  imports: [
    TranslatePipe,
    Stepper,
    ConfirmDialog,
    Button,
    OfferSelectionStep,
    ProductConfigurationStep,
    SubmitOrderStep,
  ],
  // All three are screen-scoped, never root: leaving the flow destroys the
  // basket, the submit outcome AND the address state with the component
  // (AC-SALE-01-16). `CustomerDetailStore` is here because step 2 reuses the
  // FR-ADDR-02 dialog, which writes addresses through that store.
  providers: [SaleBasketStore, OrderSubmitStore, CustomerDetailStore],
  templateUrl: './sale-wizard.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
})
export class SaleWizard {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly flash = inject(CustomerFlashService);
  protected readonly basket = inject(SaleBasketStore);
  protected readonly submitStore = inject(OrderSubmitStore);

  /** `customerNumber` is a path param; `accountNumber` is a QUERY param because
   *  it selects which billing account of that customer the sale is for
   *  (AC-SALE-01-01) — a qualifier, not an identity. */
  protected readonly customerNumber = toSignal(
    this.route.paramMap.pipe(map((p) => Number(p.get('customerNumber')))),
    { initialValue: 0 },
  );
  protected readonly accountNumber = toSignal(
    this.route.queryParamMap.pipe(map((p) => p.get('accountNumber') ?? '')),
    { initialValue: '' },
  );

  private readonly stepIndex = signal(0);
  protected readonly activeIndex = this.stepIndex.asReadonly();
  private readonly stepId = computed<StepId>(() => STEP_IDS[this.stepIndex()]);

  protected readonly isOffer = computed(() => this.stepId() === 'offer');
  protected readonly isConfig = computed(() => this.stepId() === 'config');
  protected readonly isSummary = computed(() => this.stepId() === 'summary');

  protected readonly confirmOpen = signal(false);

  /** Each step's own `LBL-NEXT` gate. AC-SALE-01-08 (basket composition) and
   *  AC-SALE-01-12/18/19 (characteristics) both make the CLICK the moment their
   *  rules run, so the footer ASKS the active step rather than mirroring its
   *  rules here — only the step knows its basket panel / loaded schema. */
  private readonly offerStep = viewChild(OfferSelectionStep);
  private readonly configStep = viewChild(ProductConfigurationStep);

  /** Mock step labels. Step 3 reads "Order summary" here while the page heading
   *  reads "Submit order" — the mock deliberately differs; both preserved
   *  (scope §2B.7). */
  protected readonly steps = computed<readonly StepItem[]>(() => [
    { id: 'offer', label: this.i18n.translate('UI-SALE-STEP-OFFER') },
    { id: 'config', label: this.i18n.translate('UI-SALE-STEP-CONFIG') },
    { id: 'summary', label: this.i18n.translate('UI-SALE-STEP-SUMMARY') },
  ]);

  protected readonly titleKey = computed(() =>
    this.isOffer()
      ? 'UI-SALE-OFFER-TITLE'
      : this.isConfig()
        ? 'UI-SALE-CONFIG-TITLE'
        : 'UI-SALE-SUBMIT-TITLE',
  );

  protected readonly accountContext = computed(() =>
    this.accountNumber()
      ? this.i18n.translate('UI-SALE-ACCOUNT-CONTEXT', { accountNumber: this.accountNumber() })
      : '',
  );

  /**
   * The confirm dialog's body (AC-SALE-01-15). The mock states the two facts
   * that make the confirmation meaningful — WHICH billing account and HOW MUCH
   * — instead of a bare "are you sure" (scope §4.33). Both come from the sale's
   * own state: the account from the wizard's context, the amount from the
   * basket. The total shown here is the client's preview; the authoritative
   * figure is the one the 201 snapshots.
   */
  protected readonly confirmMessage = computed(() => {
    const totalAmount = formatPrice(this.basket.totalAmount());
    const accountNumber = this.accountNumber();
    return accountNumber
      ? this.i18n.translate('UI-SALE-CONFIRM-BODY', { accountNumber, totalAmount })
      : this.i18n.translate('UI-SALE-CONFIRM-BODY-NO-ACCOUNT', { totalAmount });
  });

  /** AC-SALE-01-07: Next is disabled while the basket is empty — the only
   *  DISABLED gate in the flow. The AC-SALE-01-08 composition rules and the
   *  AC-SALE-01-12/18/19 characteristic rules are NOT gated here: those ACs say
   *  the user presses Next and is then TOLD which rule failed, which a disabled
   *  button can never do. They are enforced on the click instead, in
   *  {@link goNext}. */
  protected readonly nextDisabled = computed(() => this.isOffer() && this.basket.isEmpty());

  /** Submit is blocked while a request is open or once the order exists — the
   *  UI half of the duplicate-submit guard (`OrderSubmitStore` is the other). */
  protected readonly submitDisabled = computed(
    () => this.submitStore.locked() || this.basket.isEmpty(),
  );

  constructor() {
    effect(() => {
      const customerNumber = this.customerNumber();
      const accountNumber = this.accountNumber();
      if (customerNumber > 0 && accountNumber !== '') {
        this.basket.setContext(customerNumber, accountNumber);
      }
    });

    // AC-SALE-01-15 success: LEAVE. The mock navigates back to Customer Info the
    // moment the order exists, rather than parking the user on a dead screen
    // behind a "Back to customer" button (scope §4.33, superseding §2B.8's
    // in-place success state). The order number is not lost by leaving — it
    // travels in the flash message and is announced by the toast.
    //
    // The products the order created are NOT carried across and NOT injected
    // client-side (the mock writes them into localStorage; we deliberately do
    // not — FE-ADR-013 §e). They exist in the backend, so Customer Info simply
    // re-reads them: GET /api/products?accountNumber= runs when the row expands.
    effect(() => {
      const order = this.submitStore.order();
      if (!order) return;
      const count = order.items.length;
      // Choosing between two KEYS is not translating — the sender still never
      // resolves text, and singular/plural stays a catalogue concern
      // (FE-ADR-012 §h.3: no plural engine).
      this.flash.set(
        count === 1 ? 'UI-SALE-TOAST-ORDER-SUBMITTED-ONE' : 'UI-SALE-TOAST-ORDER-SUBMITTED',
        { orderNumber: order.orderNumber, count, accountNumber: order.accountNumber },
      );
      void this.router.navigate(['/customers', this.customerNumber()], {
        queryParams: { tab: 'account', account: order.accountNumber },
      });
    });
  }

  /**
   * Every forward move is gated by the step the user is standing on, and a
   * failure LEAVES THEM THERE with the reason on screen:
   *
   * - step 1, AC-SALE-01-08: the basket must hold exactly one INTERNET, one
   *   RESOURCE and one ACTIVATION offer. Product Configuration must not open
   *   over a basket the order can never be built from — otherwise the user
   *   configures characteristics for two screens and only learns the basket was
   *   wrong when the submit is rejected.
   * - step 2, AC-SALE-01-12/18/19: the characteristic and service-address rules,
   *   painted under the offending fields.
   */
  protected goNext(): void {
    if (this.isOffer() && this.offerStep()?.validate() !== true) return;
    if (this.isConfig() && this.configStep()?.validate() !== true) return;
    if (this.stepIndex() < STEP_IDS.length - 1) this.stepIndex.update((i) => i + 1);
  }

  /** AC-SALE-01-13/14 — automatic: basket and configuration live in the store,
   *  which outlives the step components. */
  protected goPrevious(): void {
    if (this.stepIndex() > 0) this.stepIndex.update((i) => i - 1);
  }

  /** AC-SALE-01-16 `LBL-CANCEL`: leaving ends the sale. The store dies with this
   *  component, so nothing survives to be processed later. */
  protected cancel(): void {
    void this.router.navigate(['/customers', this.customerNumber()]);
  }

  /** AC-SALE-01-15: `LBL-SUBMIT` opens the confirm modal; it does NOT submit. */
  protected openConfirm(): void {
    if (this.submitDisabled()) return;
    this.confirmOpen.set(true);
  }

  /** AC-SALE-01-15: declining closes the modal, sends nothing, and leaves the
   *  user on the Submit screen. */
  protected closeConfirm(): void {
    this.confirmOpen.set(false);
  }

  protected confirmSubmit(): void {
    const request = this.basket.toRequest();
    if (!request) return;
    this.confirmOpen.set(false);
    this.submitStore.submit(request);
  }
}
