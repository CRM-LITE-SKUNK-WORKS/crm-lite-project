import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { isApiError } from '../../../core/http';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import {
  ConfirmDialog,
  EmptyState,
  Skeleton,
  StatusBadge,
  Table,
  TableCellDef,
  Toast,
  type TableColumn,
} from '../../../shared/patterns';
import { Button, IconButton } from '../../../shared/ui';
import { AccountListStore } from '../state/account-list.store';
import { type AccountResponse } from '../model';
import { AccountFormDialog } from './account-form-dialog';
import { type BillingAddressOption } from './billing-address-option';

/**
 * Billing-account section of Customer Info (FR-ACCT-01..04, KR-11) — the
 * component the account feature EXPOSES for the Customer Info account tab,
 * exactly the composition FE-ADR-003 §Consequences predicted. The customer
 * feature imports this; this feature imports nothing of the customer's
 * (addresses arrive as caller-built {@link BillingAddressOption}s).
 *
 * Domain rules enforced here (ADR-013/014, FE-ADR-013 §Amendment A1):
 * - **K-8:** only 224 Billing Accounts ever arrive or render. No 223 surface
 *   of any kind — the automatic Customer Account is invisible backend
 *   behaviour the UI neither knows nor explains.
 * - **KR-11:** the account number is display-only (tabular text; the edit
 *   dialog shows it as text, never a control).
 * - **AC-ACCT-01-03/04:** Passive rows stay listed; the SERVER's order
 *   (Active-first, then number ascending) renders as received — no client
 *   re-sort (the store guarantees this).
 * - **AC-ACCT-04-02/03:** delete = passivation behind a ConfirmDialog; the
 *   active-product guard is backend-enforced — a 409 (`MSG-ACCT-HAS-PRODUCTS`
 *   / `MSG-ACCT-NOT-ACTIVE`) flips the dialog into its error state, never
 *   pre-computed. Passive-row actions stay ENABLED for the same reason: the
 *   backend's answer is the truth, the client does not imitate the check.
 */
@Component({
  selector: 'app-account-section',
  imports: [
    TranslatePipe,
    Button,
    IconButton,
    ConfirmDialog,
    EmptyState,
    Skeleton,
    StatusBadge,
    Table,
    TableCellDef,
    Toast,
    AccountFormDialog,
  ],
  providers: [AccountListStore],
  templateUrl: './account-section.html',
})
export class AccountSection {
  private readonly i18n = inject(I18nService);
  protected readonly store = inject(AccountListStore);

  /** The customer's PUBLIC business number (list/create key). */
  readonly customerNumber = input.required<number>();
  /** Active addresses of the customer, resolved to display labels by the caller. */
  readonly addressOptions = input.required<readonly BillingAddressOption[]>();

  // --- dialog / toast state -------------------------------------------------
  protected readonly formDialogOpen = signal(false);
  /** Account being edited; `null` while creating. */
  protected readonly formDialogTarget = signal<AccountResponse | null>(null);
  protected readonly deleteTarget = signal<AccountResponse | null>(null);
  protected readonly deleteError = signal<string | null>(null);
  protected readonly deleteBusy = signal(false);
  protected readonly toastKey = signal<string | null>(null);

  protected readonly columns = computed<readonly TableColumn[]>(() => [
    { id: 'status', header: this.i18n.translate('UI-ACCOUNT-COL-STATUS') },
    {
      id: 'number',
      header: this.i18n.translate('UI-ACCOUNT-COL-NUMBER'),
      cellClass: 'eds-tabular-nums text-body text-ink',
    },
    { id: 'name', header: this.i18n.translate('UI-ACCOUNT-COL-NAME'), cellClass: 'text-body text-ink' },
    {
      id: 'type',
      header: this.i18n.translate('UI-ACCOUNT-COL-TYPE'),
      cellClass: 'text-body text-ink-soft',
    },
    {
      id: 'actions',
      header: this.i18n.translate('UI-ACCOUNT-COL-ACTIONS'),
      headerClass: 'text-right',
    },
  ]);

  protected readonly rowTestIdFor = (account: AccountResponse): string =>
    `customer-detail-account-row-${account.accountNumber}`;

  protected readonly errorText = computed(() => {
    const error = this.store.error();
    if (this.store.status() !== 'error') return null;
    return this.i18n.translate(error?.messageKey ?? 'MSG-INTERNAL-ERROR');
  });

  constructor() {
    effect(() => {
      this.store.load(this.customerNumber());
    });
  }

  /** Localized DISPLAY of the wire status ("Active"/"Passive" stay the data). */
  protected statusLabel(account: AccountResponse): string {
    return this.i18n.translate(
      account.accountStatus === 'Active' ? 'UI-ACCOUNT-STATUS-ACTIVE' : 'UI-ACCOUNT-STATUS-PASSIVE',
    );
  }

  protected openCreate(): void {
    this.formDialogTarget.set(null);
    this.formDialogOpen.set(true);
  }
  protected openEdit(account: AccountResponse): void {
    this.formDialogTarget.set(account);
    this.formDialogOpen.set(true);
  }
  protected onFormSaved(): void {
    this.toastKey.set(
      this.formDialogTarget() ? 'UI-ACCOUNT-TOAST-UPDATED' : 'UI-ACCOUNT-TOAST-CREATED',
    );
    this.formDialogOpen.set(false);
    this.formDialogTarget.set(null);
  }
  protected onFormClosed(): void {
    this.formDialogOpen.set(false);
    this.formDialogTarget.set(null);
  }

  protected askDelete(account: AccountResponse): void {
    this.deleteError.set(null);
    this.deleteTarget.set(account);
  }
  protected confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target || this.deleteBusy()) return;
    this.deleteBusy.set(true);
    this.store.delete(target.accountNumber).subscribe({
      next: () => {
        // 204: passivated — the reload keeps the row visible as Passive
        // (AC-ACCT-04-02); the success message is frontend-only (contract).
        this.deleteBusy.set(false);
        this.deleteTarget.set(null);
        this.toastKey.set('MSG-ACCT-DELETED');
      },
      error: (error: unknown) => {
        // AC-ACCT-04-03 and re-delete: the backend's 409 messageKey, verbatim
        // through i18n — the client never imitated the check.
        this.deleteBusy.set(false);
        this.deleteError.set(
          this.i18n.translate(isApiError(error) ? error.messageKey : 'MSG-INTERNAL-ERROR'),
        );
      },
    });
  }
  protected cancelDelete(): void {
    this.deleteTarget.set(null);
    this.deleteError.set(null);
  }

  protected dismissToast(): void {
    this.toastKey.set(null);
  }
}
