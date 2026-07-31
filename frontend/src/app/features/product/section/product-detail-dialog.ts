import { Component, computed, effect, inject, input, output } from '@angular/core';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { Button, Icon } from '../../../shared/ui';
import { Modal, ModalFooter, Skeleton } from '../../../shared/patterns';
import { ProductDetailStore } from '../state/product-detail.store';
import { type ProductRow } from '../model';

/** Empty/absent values render as an em dash, matching Customer Info's read
 *  layout (mock-ui-analysis §6.4 tab 1: "boşsa —"). */
const EM_DASH = '—';

/**
 * FR-PROD-02 product detail (AC-PROD-02-01).
 *
 * WHY A MODAL, NOT A ROUTED SCREEN — the decision and its evidence:
 * 1. AC-PROD-02-01 itself is recorded as a "detail modal" in
 *    `docs/requirements/traceability-matrix.md`, and `docs/api/product-service.md`
 *    describes `GET /api/products/{id}` as the "Product detail modal (FR-PROD-02)".
 * 2. The mock reaches it from an `eye` action inside an expanded account row
 *    (mock-ui-analysis §6.4 tab 2, aria-label `View product`) — a row-level
 *    action, and every other row-level detail/edit interaction in Customer Info
 *    is a modal (customer info update, contact medium update, address form).
 * 3. A routed screen would require inventing a `/products/:id` route and a
 *    back-navigation story that no FR, no AC and no mock screen defines.
 * The mock's own product-detail markup is NOT recoverable — the bundled mock
 * (`docs/source/mock-ui/…Full_App.html`) contains no product markup at all
 * (verified 2026-07-31). Its layout therefore follows Customer Info's documented
 * read grid rather than being invented; recorded in scope §4.28.
 *
 * READ-ONLY: no form, no control, no save. The five contract fields are text.
 * A 404 `MSG-PROD-NOT-FOUND` renders the documented not-found state — the
 * distinction comes from the backend's `messageKey`, never from a guess.
 * `serviceAddress: null` (no address on the product or its ancestors, or a
 * soft-deleted address) blanks that block; the modal still renders.
 */
@Component({
  selector: 'app-product-detail-dialog',
  imports: [TranslatePipe, Button, Icon, Modal, ModalFooter, Skeleton],
  templateUrl: './product-detail-dialog.html',
})
export class ProductDetailDialog {
  private readonly i18n = inject(I18nService);
  protected readonly store = inject(ProductDetailStore);

  /** The row the user clicked; `null` ⇒ closed. The row supplies the identity
   *  (name + id) the detail response deliberately does not carry. */
  readonly product = input<ProductRow | null>(null);
  readonly closed = output<void>();

  protected readonly open = computed(() => this.product() !== null);

  /** Any non-404 failure: shown as a banner with the backend's messageKey. */
  protected readonly errorText = computed(() => {
    if (this.store.status() !== 'error' || this.store.notFound()) return null;
    return this.i18n.translate(this.store.error()?.messageKey ?? 'MSG-INTERNAL-ERROR');
  });

  /** Read rows of the AC-PROD-02-01 grid, in contract order. */
  protected readonly fields = computed(() => {
    const detail = this.store.detail();
    if (!detail) return [];
    return [
      {
        id: 'offer-name',
        label: this.i18n.translate('UI-PRODUCT-FIELD-OFFER-NAME'),
        value: detail.productOfferName,
        tabular: false,
      },
      {
        id: 'offer-id',
        label: this.i18n.translate('UI-PRODUCT-FIELD-OFFER-ID'),
        value: String(detail.productOfferId),
        tabular: true,
      },
      {
        id: 'spec-id',
        label: this.i18n.translate('UI-PRODUCT-FIELD-SPEC-ID'),
        value: String(detail.productSpecId),
        tabular: true,
      },
      {
        id: 'campaign',
        label: this.i18n.translate('UI-PRODUCT-FIELD-CAMPAIGN'),
        // The detail contract carries the campaign NAME only — this endpoint
        // exposes no campaign code, so none is shown here (the row's
        // `campaignId` is the code the table already displays).
        value: detail.campaign ?? EM_DASH,
        tabular: false,
      },
    ];
  });

  /** One display line per address part, blanks skipped — the same composition
   *  the address cards use, applied to the server-resolved service address. */
  protected readonly serviceAddressLines = computed(() => {
    const address = this.store.detail()?.serviceAddress;
    if (!address) return [];
    const street = [address.street, address.houseFlatNumber].filter(Boolean).join(' ');
    const region = [address.districtName, address.cityName].filter(Boolean).join(', ');
    return [street, region].filter((line) => line.length > 0);
  });

  constructor() {
    effect(() => {
      const product = this.product();
      if (product) {
        this.store.load(product.productId);
      } else {
        this.store.reset();
      }
    });
  }

  protected onClose(): void {
    this.closed.emit();
  }
}
