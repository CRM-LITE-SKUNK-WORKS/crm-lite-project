import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import {
  Button,
  IconButton,
} from '../../../shared/ui';
import {
  Skeleton,
  StatusBadge,
  Table,
  TableCellDef,
  type TableColumn,
} from '../../../shared/patterns';
import { ProductListStore } from '../state/product-list.store';
import { ProductDetailStore } from '../state/product-detail.store';
import { type ProductRow } from '../model';
import { ProductDetailDialog } from './product-detail-dialog';

/** Campaign-less products render `"-"` — explicitly the frontend's job
 *  (docs/api/product-service.md §Representations); the wire value is `null`. */
const NO_VALUE = '-';

/**
 * Product sub-table of ONE billing account (FR-PROD-01) — the component the
 * product feature EXPOSES for Customer Info, mirroring
 * `features/account/section/AccountSection` (FE-ADR-003 §Consequences).
 *
 * DEPENDENCY DIRECTION — one way, and narrower than the account section's.
 * The only input is `accountNumber: string`, which is this feature's OWN
 * contract parameter (`GET /api/products?accountNumber=`), not a borrowed
 * account type: no `AccountResponse`, no customer type, and no shared model of
 * any kind crosses into this feature. Nothing here imports a sibling feature —
 * the composition happens one level up, in Customer Info.
 *
 * Domain rules enforced here (FR-PROD-01..02, docs/api/product-service.md):
 * - **NO PAGINATION.** FR-PROD-01 defines none and the endpoint returns a plain
 *   array, so `shared/patterns/Pagination` is deliberately NOT used and no
 *   page/size parameter is ever sent. The mock's 4/8/12 selector is not
 *   reproduced (same call as scope §4.24/5 for accounts).
 * - **AC-PROD-01-03 — Passive products stay listed.** The client never filters
 *   by status; whatever the backend returns is rendered, in server order. Same
 *   philosophy as the account section's Active-then-Passive handling.
 * - **Campaign identity is `campaignId` = the PUBLIC `campaign_code`.** Internal
 *   campaign ids never leave the service, so this is the only campaign
 *   identifier the UI shows or uses.
 * - **Derived values are the server's.** Product status, service type and
 *   campaign totals are computed backend-side; the client displays what arrives
 *   and recomputes nothing.
 * - **AC-PROD-01-02 — `MSG-PROD-NONE` is frontend-only.** An account with no
 *   products is a `200 []`; the backend never sends this key, so the empty
 *   state produces it.
 * - **AC-PROD-01-04 — the Action column is VIEW-ONLY.** "Deactivate product" is
 *   a write and no write endpoint exists, so it renders inert under the
 *   FE-ADR-013 §Amendment A3 "Coming soon" rule — never wired, never faked.
 */
@Component({
  selector: 'app-product-section',
  imports: [
    TranslatePipe,
    Button,
    IconButton,
    Skeleton,
    StatusBadge,
    Table,
    TableCellDef,
    ProductDetailDialog,
  ],
  providers: [ProductListStore, ProductDetailStore],
  templateUrl: './product-section.html',
})
export class ProductSection {
  private readonly i18n = inject(I18nService);
  protected readonly store = inject(ProductListStore);

  /** The PUBLIC 10-digit KR-11 billing-account number — this feature's own
   *  query parameter, supplied by the caller as a plain string. */
  readonly accountNumber = input.required<string>();

  /** Product whose detail modal is open; `null` while closed. */
  protected readonly detailTarget = signal<ProductRow | null>(null);

  protected readonly columns = computed<readonly TableColumn[]>(() => [
    {
      id: 'productId',
      header: this.i18n.translate('UI-PRODUCT-COL-ID'),
      cellClass: 'eds-tabular-nums text-body text-ink',
    },
    { id: 'productName', header: this.i18n.translate('UI-PRODUCT-COL-NAME'), cellClass: 'text-body text-ink' },
    {
      id: 'campaignName',
      header: this.i18n.translate('UI-PRODUCT-COL-CAMPAIGN-NAME'),
      cellClass: 'text-body text-ink-soft',
    },
    {
      id: 'campaignId',
      header: this.i18n.translate('UI-PRODUCT-COL-CAMPAIGN-ID'),
      cellClass: 'text-body text-ink-soft',
    },
    { id: 'status', header: this.i18n.translate('UI-PRODUCT-COL-STATUS') },
    {
      id: 'actions',
      header: this.i18n.translate('UI-PRODUCT-COL-ACTION'),
      headerClass: 'text-right',
    },
  ]);

  protected readonly rowTestIdFor = (product: ProductRow): string =>
    `customer-detail-product-row-${product.productId}`;

  protected readonly errorText = computed(() => {
    const error = this.store.error();
    if (this.store.status() !== 'error') return null;
    return this.i18n.translate(error?.messageKey ?? 'MSG-INTERNAL-ERROR');
  });

  constructor() {
    effect(() => {
      this.store.load(this.accountNumber());
    });
  }

  /** `null` campaign fields render as `"-"` (AC-PROD-01-03 rendering rule). */
  protected display(value: string | null): string {
    return value ?? NO_VALUE;
  }

  /** Localized DISPLAY of the wire status ("Active"/"Passive" stay the data). */
  protected statusLabel(product: ProductRow): string {
    return this.i18n.translate(
      product.productStatus === 'Active' ? 'UI-PRODUCT-STATUS-ACTIVE' : 'UI-PRODUCT-STATUS-PASSIVE',
    );
  }

  protected openDetail(product: ProductRow): void {
    this.detailTarget.set(product);
  }

  protected closeDetail(): void {
    this.detailTarget.set(null);
  }
}
