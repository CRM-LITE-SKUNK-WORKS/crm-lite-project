import { Component, inject, input, output } from '@angular/core';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { Icon, IconButton } from '../../../shared/ui';
import { type AddressResponse } from '../model';

/**
 * Address card grid + "Add new address" tile (mock-ui-analysis §6.3 step 2 —
 * byte-identical to the §6.4 Address tab, which is why this lives in the
 * `address/` SUB-MODULE (FE-ADR-003 §3): Customer Info consumes it now, the
 * Create wizard step 2 consumes it next, and no cross-feature import is ever
 * needed.
 *
 * Presentational within the feature: addresses come in (the server-resolved
 * `cityName`/`districtName` render directly — no lookup round-trip), intents
 * go out; the parent owns every API call. Rules baked into the markup, all
 * from the mock + contract:
 * - primary card: selected border/surface, inert "Primary" indicator;
 * - non-primary: a "set as primary" action (the PATCH is the parent's job);
 * - the primary's delete button is DISABLED with the mock's hint — the
 *   backend guard (409 MSG-ADDR-PRIMARY-DELETE) stays authoritative and is
 *   still surfaced if it ever fires (FE-ADR-013 §a).
 *
 * Metric roundings onto the token scale (FE-ADR-011 §f, §2.18 precedent):
 * card min-height 146→144px (`min-h-36`), radio 18→16px (`size-4`).
 */
@Component({
  selector: 'app-address-cards',
  imports: [Icon, IconButton, TranslatePipe],
  host: { class: 'grid grid-cols-2 items-start gap-4' },
  template: `
    @for (address of addresses(); track address.addressId) {
      <div
        class="flex min-h-36 flex-col rounded-lg border p-5"
        [class.border-brand]="address.primary"
        [class.bg-selected]="address.primary"
        [class.border-line]="!address.primary"
        [class.bg-surface]="!address.primary"
        [attr.data-testid]="testIdBase() + '-' + address.addressId"
      >
        <span class="flex size-10 items-center justify-center rounded-full bg-sunken">
          <app-icon name="map-pin" [size]="20" class="text-ink-muted" />
        </span>
        <span class="mt-3 text-body font-semibold text-ink">
          {{ address.districtName }}, {{ address.cityName }}
        </span>
        <span class="text-body-sm text-ink-soft">{{ summaryOf(address) }}</span>
        <div class="mt-4 flex items-center justify-between border-t border-line pt-3">
          @if (address.primary) {
            <span class="flex items-center gap-2 text-body-sm font-medium text-brand-active">
              <span class="flex size-4 items-center justify-center rounded-full border-2 border-brand">
                <span class="size-2 rounded-full bg-brand"></span>
              </span>
              {{ 'UI-ADDRESS-PRIMARY' | t }}
            </span>
          } @else {
            <button
              type="button"
              class="flex cursor-pointer items-center gap-2 border-none bg-transparent p-0 text-body-sm font-medium text-ink-soft"
              [attr.data-testid]="testIdBase() + '-' + address.addressId + '-set-primary'"
              (click)="makePrimary.emit(address)"
            >
              <span class="size-4 rounded-full border-2 border-line-strong bg-surface"></span>
              {{ 'LBL-SET-PRIMARY-ADDR' | t }}
            </button>
          }
          <div class="flex items-center gap-2">
            <app-icon-button
              icon="pencil"
              [ariaLabel]="'UI-ADDRESS-EDIT-TITLE' | t"
              [testId]="testIdBase() + '-' + address.addressId + '-edit'"
              (action)="edit.emit(address)"
            />
            <!-- Primary delete is disabled with the mock's hint; the backend
                 guard remains the authority if it ever fires. -->
            <app-icon-button
              icon="trash-2"
              variant="danger-ghost"
              [ariaLabel]="'UI-ADDRESS-DELETE' | t"
              [disabled]="address.primary"
              [attr.title]="address.primary ? ('UI-ADDRESS-PRIMARY-DELETE-HINT' | t) : null"
              [testId]="testIdBase() + '-' + address.addressId + '-delete'"
              (action)="remove.emit(address)"
            />
          </div>
        </div>
      </div>
    }
    <button
      type="button"
      class="flex min-h-36 cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-line-hover bg-sunken text-body font-medium text-ink-soft"
      [attr.data-testid]="testIdBase() + '-add-button'"
      (click)="add.emit()"
    >
      <app-icon name="plus" [size]="24" class="text-ink-muted" />
      {{ 'LBL-ADD-NEW-ADDRESS' | t }}
    </button>
  `,
})
export class AddressCards {
  private readonly i18n = inject(I18nService);

  readonly addresses = input.required<readonly AddressResponse[]>();
  /** Testid stem — `customer-detail-address` on the Info tab (default), and
   *  `customer-create-address` when the Create wizard hosts the same grid
   *  (FE-ADR-009 naming stays truthful to the hosting screen). */
  readonly testIdBase = input<string>('customer-detail-address');

  readonly add = output<void>();
  readonly edit = output<AddressResponse>();
  readonly remove = output<AddressResponse>();
  readonly makePrimary = output<AddressResponse>();

  /** Mock §6.3 summary line via the parameterized catalogue key. */
  protected summaryOf(address: AddressResponse): string {
    return this.i18n.translate('UI-ADDRESS-SUMMARY', {
      street: address.street,
      houseNo: address.houseFlatNumber,
      description: address.addressDescription,
    });
  }
}
