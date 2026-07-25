import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { map } from 'rxjs';
import { type ApiError, isApiError, matchFieldErrors } from '../../../core/http';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { Modal, ModalFooter } from '../../../shared/patterns';
import { Button, FormField, Select, TextInput, type SelectOption } from '../../../shared/ui';
import { AccountListStore } from '../state/account-list.store';
import { type AccountResponse } from '../model';
import { type BillingAddressOption } from './billing-address-option';

/** Server field-error paths this form can bind (bare names from bean
 *  validation; `customerId` errors have no control → banner via unmatched). */
const FIELDS = ['accountName', 'addressId'] as const;
type FieldName = (typeof FIELDS)[number];

/**
 * Create / Edit billing-account dialog (FR-ACCT-02/03).
 *
 * KR-11 BY CONSTRUCTION: in edit mode the account number and type are rendered
 * as read-only TEXT — never bound to any form control (scope §1A.3) — and the
 * form only ever produces `{accountName, addressId}` (+ `customerId` added by
 * the store on create). The immutable fields are not "locked inputs", they are
 * not inputs at all, so no request can carry them; the backend's 400
 * `MSG-ACCT-IMMUTABLE-FIELD` remains a contract backstop, not a UI path.
 *
 * The billing address is chosen from the customer's ACTIVE addresses, which
 * arrive as caller-built {@link BillingAddressOption}s — the account feature
 * never reaches into the customer feature (FE-ADR-003; the one sanctioned
 * cross-feature import runs the other way).
 *
 * Errors: client gate is just "both fields present" (no format rule exists in
 * the contract); server 400s land per-field via `matchFieldErrors`, and
 * whole-request rejections (409 `MSG-ACCT-NOT-ACTIVE` on a Passive account —
 * NEVER pre-checked, 400 `MSG-ACCT-IMMUTABLE-FIELD`, 503) show in the dialog
 * banner through their `messageKey` (FE-ADR-008).
 */
@Component({
  selector: 'app-account-form-dialog',
  imports: [ReactiveFormsModule, TranslatePipe, Button, FormField, Select, TextInput, Modal, ModalFooter],
  templateUrl: './account-form-dialog.html',
})
export class AccountFormDialog {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly i18n = inject(I18nService);
  private readonly store = inject(AccountListStore);

  readonly open = input.required<boolean>();
  /** `null` ⇒ create mode; an account ⇒ edit mode. */
  readonly account = input<AccountResponse | null>(null);
  /** The customer's active addresses, resolved by the CALLER (see class doc). */
  readonly addressOptions = input.required<readonly BillingAddressOption[]>();

  readonly closed = output<void>();
  readonly saved = output<void>();

  protected readonly form = this.fb.group({
    accountName: this.fb.control(''),
    addressId: new FormControl<number | null>(null),
  });

  protected readonly busy = signal(false);
  private readonly serverError = signal<ApiError | null>(null);

  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );

  protected readonly isEdit = computed(() => this.account() !== null);

  protected readonly selectOptions = computed<readonly SelectOption[]>(() =>
    this.addressOptions().map((option) => ({ value: option.addressId, label: option.label })),
  );

  /** Both fields required (contract) — the save button gates on this. */
  protected readonly canSave = computed(() => {
    const value = this.formValue();
    return value.accountName.trim() !== '' && value.addressId !== null;
  });

  private readonly fieldErrorMatch = computed(() =>
    matchFieldErrors(this.serverError()?.fieldErrors ?? [], [...FIELDS]),
  );
  protected readonly unmatchedFieldErrors = computed(() => this.fieldErrorMatch().unmatched);
  protected readonly bannerText = computed(() => {
    const error = this.serverError();
    return error ? this.i18n.translate(error.messageKey) : null;
  });

  protected errorTextFor(field: FieldName): string | undefined {
    const key = this.fieldErrorMatch().matched.get(field)?.messageKey;
    return key ? this.i18n.translate(key) : undefined;
  }

  constructor() {
    // (Re)initialize on every open: blank for create, pre-filled for edit.
    effect(() => {
      if (!this.open()) return;
      const account = this.account();
      this.serverError.set(null);
      this.busy.set(false);
      this.form.reset({
        accountName: account?.accountName ?? '',
        addressId: account?.billingAddressId ?? null,
      });
    });

    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.serverError() !== null) this.serverError.set(null);
    });
  }

  protected onSave(): void {
    if (!this.canSave() || this.busy()) return;
    const raw = this.form.getRawValue();
    const body = {
      accountName: raw.accountName.trim(),
      addressId: raw.addressId as number,
    };
    this.busy.set(true);
    const account = this.account();
    const request = account ? this.store.update(account.accountNumber, body) : this.store.create(body);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.saved.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.serverError.set(isApiError(error) ? error : null);
      },
    });
  }

  protected onCancel(): void {
    this.closed.emit();
  }
}
