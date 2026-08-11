import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '../../../core/i18n';
import { Button, Icon } from '../../../shared/ui';
import { OrderSubmitStore, SaleBasketStore } from '../../order';
import { CustomerDetailStore } from '../state/customer-detail.store';
import { formatPrice } from './money';

/**
 * Step 3 — Submit order (AC-SALE-01-12/15/18/19, ADR-018 §6).
 *
 * Mock layout (Submit Order.dc.html, scope §2B.7): one "Order details" card
 * whose body is a `160px | minmax(0,1fr)` label/value grid — Order Number,
 * Order items (a bordered table), Service address — with a "Total amount"
 * strip across its footer. The Previous/Cancel/Submit footer belongs to the
 * WIZARD, not to this step, exactly as the mock draws it.
 *
 * ORDER NUMBER (analyst decision of 2026-08-10, ADR-018 §1.1): the row is
 * always rendered, and shows the REAL KR-12 number the WAIT draft was given
 * at Start New Sale — not an em dash, and not a client-invented placeholder
 * (FE-ADR-013 §b). While the draft is still being created it shows a loading
 * label instead; a failed draft leaves it blank and the error banner explains
 * why.
 *
 * SUCCESS (scope §4.33): this screen renders NO success state. The wizard
 * navigates to Customer Info the moment `processingStatus` reaches
 * `COMPLETED`. The number travels in the flash message and the destination's
 * toast announces it.
 *
 * PROCESSING / FAILURE (ADR-018 §6, a PROJECT INTERPRETATION pending the
 * analyst's final UX confirmation — see FRONTEND_BRAIN.md): once Submit is
 * accepted this screen shows the analyst's own "Sipariş Alındı, İşleniyor…"
 * wording in place, with the Order Number, until the saga resolves. A
 * terminal FAILED sale replaces that with the backend's failure key and a way
 * back to Customer Info; nothing here ever retries or creates a second order.
 */
@Component({
  selector: 'app-submit-order-step',
  imports: [TranslatePipe, Icon, Button],
  templateUrl: './submit-order-step.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
})
export class SubmitOrderStep {
  protected readonly basket = inject(SaleBasketStore);
  protected readonly submitStore = inject(OrderSubmitStore);
  private readonly addressStore = inject(CustomerDetailStore);
  private readonly router = inject(Router);

  /** `null` until the WAIT draft exists; the real KR-12 number from there on. */
  protected readonly orderNumber = this.submitStore.orderNumber;
  protected readonly draftPending = this.submitStore.draftPending;

  /** The banner shows WHICHEVER HTTP rejection is current: a failed draft
   *  creation, or (once a draft exists) a failed submit attempt — the two
   *  can never both be current, since a failed draft leaves no order number
   *  to submit against. */
  protected readonly errorKey = computed(
    () => this.submitStore.draftErrorKey() ?? this.submitStore.submitErrorKey(),
  );

  /** Field-level rejections (e.g. `MSG-VAL-CHAR-REQUIRED` on one
   *  characteristic) arrive as `validationErrors`; step 2 owns no banner, so
   *  they are listed here rather than dropped (AC-SALE-01-19). */
  protected readonly fieldErrorKeys = this.submitStore.fieldErrorKeys;

  /** AC-SALE-01-15's in-place state, once the 202 has been accepted and while
   *  the saga is still running. */
  protected readonly processing = computed(
    () => this.submitStore.processingStatus() === 'PROCESSING',
  );
  protected readonly failed = this.submitStore.failed;
  protected readonly failureMessageKey = this.submitStore.failureMessageKey;

  protected returnToCustomer(): void {
    const customerNumber = this.basket.customerNumber();
    if (customerNumber === null) return;
    void this.router.navigate(['/customers', customerNumber]);
  }

  private readonly serviceAddress = computed(() => {
    const id = this.basket.serviceAddressId();
    if (id === null) return null;
    return this.addressStore.addresses().find((a) => a.addressId === id) ?? null;
  });

  protected readonly addressLine = computed(() => {
    const address = this.serviceAddress();
    if (!address) return '—';
    return `${address.street} ${address.houseFlatNumber}, ${address.districtName}, ${address.cityName}`;
  });

  protected readonly addressDescription = computed(
    () => this.serviceAddress()?.addressDescription ?? '',
  );

  /**
   * The table rows. Names, the campaign and prices all come from the basket:
   * the asynchronous status resource (`OrderStatusResponse`, ADR-018 §6)
   * carries no items and no amounts at all — unlike the legacy 201, there is
   * no server-side snapshot to switch to. Catalog prices are the analyst-
   * confirmed non-normative demo fixtures (ADR-018 §1.3): this screen
   * displays exactly what the catalog/basket already holds, and never
   * recalculates or invents a price list of its own.
   */
  protected readonly rows = computed(() =>
    this.basket.items().map((item) => ({
      key: String(item.offer.offerId),
      offerId: item.offer.offerId,
      offerName: item.offer.offerName,
      campaignLabel: item.campaignName ? `${item.campaignName} · ${item.campaignId}` : '',
      price: item.offer.price,
    })),
  );

  protected readonly total = this.basket.totalAmount;

  protected price(value: number): string {
    return formatPrice(value);
  }
}
