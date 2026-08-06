import { Component, computed, inject } from '@angular/core';
import { TranslatePipe } from '../../../core/i18n';
import { Icon } from '../../../shared/ui';
import { OrderSubmitStore, SaleBasketStore } from '../../order';
import { CustomerDetailStore } from '../state/customer-detail.store';
import { formatPrice } from './money';

/**
 * Step 3 — Submit order (AC-SALE-01-12/15/18/19).
 *
 * Mock layout (Submit Order.dc.html, scope §2B.7): one "Order details" card
 * whose body is a `160px | minmax(0,1fr)` label/value grid — Order Number,
 * Order items (a bordered table), Service address — with a "Total amount"
 * strip across its footer. The Previous/Cancel/Submit footer belongs to the
 * WIZARD, not to this step, exactly as the mock draws it.
 *
 * ORDER NUMBER (approved option (a), scope §2B.7): the row is always rendered,
 * but until the server answers 201 its value is an em dash. The mock fabricates
 * a number here; KR-12 order numbers are minted by order-service and the client
 * has no way to know one in advance, so inventing one would be fabricating data
 * (FE-ADR-013 §b). It fills in from `OrderResponse.orderNumber` on success.
 *
 * SUCCESS (scope §4.33, superseding §2B.8): this screen renders NO success
 * state. The wizard navigates to Customer Info the moment the 201 lands, as the
 * mock does. §2B.8 had kept an in-place banner because leaving seemed to lose
 * the KR-12 order number — it does not: the number travels in the flash message
 * and the destination's toast announces it. The only outcome rendered here is a
 * FAILURE, which keeps the user on the summary with their basket intact.
 */
@Component({
  selector: 'app-submit-order-step',
  imports: [TranslatePipe, Icon],
  templateUrl: './submit-order-step.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
})
export class SubmitOrderStep {
  protected readonly basket = inject(SaleBasketStore);
  protected readonly submitStore = inject(OrderSubmitStore);
  private readonly addressStore = inject(CustomerDetailStore);

  /** The 201 body once it exists — the server's own snapshot of the order. */
  protected readonly order = this.submitStore.order;

  /** AC-SALE-01-18: the store resolves which key is renderable (backend key
   *  first, HTTP-status fallback second); the screen only paints it. The
   *  backend's `message` field is never bound (FE-ADR-008 §2). */
  protected readonly errorKey = this.submitStore.errorKey;

  /** Field-level rejections (e.g. `MSG-VAL-CHAR-REQUIRED` on one
   *  characteristic) arrive as `validationErrors`; step 2 owns no banner, so
   *  they are listed here rather than dropped (AC-SALE-01-19). */
  protected readonly fieldErrorKeys = this.submitStore.fieldErrorKeys;

  /** Option (a): `—` until the server mints the KR-12 number. */
  protected readonly orderNumber = computed(() => this.order()?.orderNumber ?? '—');

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
   * The table rows.
   *
   * NAMES always come from the basket: `OrderItemResponse` carries only
   * `offerId`/`productId`/`amount` — offer names, the campaign and the service
   * address are deliberately not echoed back (ADR-016 §3), because the client
   * assembled the basket and already holds them.
   *
   * PRICES switch to the server's `amount` once the order exists: ADR-016 §2.4
   * snapshots the amounts at creation, so those are the authoritative numbers
   * from that moment on. Before then the basket's catalog price is a clearly
   * labelled preview.
   */
  protected readonly rows = computed(() => {
    const amountByOffer = new Map(
      (this.order()?.items ?? []).map((item) => [item.offerId, item.amount]),
    );
    return this.basket.items().map((item) => ({
      key: String(item.offer.offerId),
      offerId: item.offer.offerId,
      offerName: item.offer.offerName,
      campaignLabel: item.campaignName ? `${item.campaignName} · ${item.campaignId}` : '',
      price: amountByOffer.get(item.offer.offerId) ?? item.offer.price,
    }));
  });

  protected readonly total = computed(() => this.order()?.totalAmount ?? this.basket.totalAmount());

  protected price(value: number): string {
    return formatPrice(value);
  }
}
