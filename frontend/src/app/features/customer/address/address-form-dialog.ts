import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { map } from 'rxjs';
import { type ApiError, isApiError, matchFieldErrors } from '../../../core/http';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { LookupCacheService } from '../../../core/lookup';
import { Modal, ModalFooter } from '../../../shared/patterns';
import { Button, FormField, Select, TextInput, type SelectOption } from '../../../shared/ui';
import { CustomerDetailStore } from '../state/customer-detail.store';
import { type AddressRequest, type AddressResponse } from '../model';

const FIELDS = ['cityId', 'districtId', 'street', 'houseFlatNumber', 'addressDescription'] as const;
type FieldName = (typeof FIELDS)[number];

/**
 * What the dialog needs to pre-fill — satisfied structurally by BOTH the
 * server's `AddressResponse` (Info tab) and the wizard's local draft (Create
 * step 2), so one dialog serves both hosts (FE-ADR-003 §3's "single shared
 * component" decision).
 */
export interface AddressFormValue {
  readonly cityId: number;
  readonly districtId: number;
  readonly street: string;
  readonly houseFlatNumber: string;
  readonly addressDescription: string;
}

/**
 * Add/Edit address dialog (mock-ui-analysis §6.3 modal — identical in the §6.4
 * Address tab; `address/` sub-module per FE-ADR-003 §3, shared with the Create
 * wizard later).
 *
 * Contract-driven behaviour (backend wins, FE-ADR-013 §e):
 * - Selects carry `cityId`/`districtId` (numbers), never names (scope §2.8);
 *   the cascade loads from `core/lookup` (`GET /api/cities`,
 *   `/api/cities/{id}/districts` — ADR-002, never hardcoded).
 * - District is DISABLED until a city is chosen; changing the city resets the
 *   district (the mock's cascade rule read narrowly: only the dependent field
 *   resets — recorded in scope §4.22).
 * - `primary` is NOT sent: the standalone POST auto-primaries the first
 *   address and primacy otherwise changes via the PATCH (data-layer note).
 * - All five fields are required (mock §6.3) — the submit button stays
 *   disabled until complete; the backend remains the authority (FE-ADR-007)
 *   and its 400 `validationErrors` land back on the fields via
 *   `matchFieldErrors` (unmatched → dialog banner, never dropped).
 *
 * The dialog owns its submit UX; the store owns the state (it refreshes the
 * address list on success). `saved` fires after the server confirms — the
 * parent closes the dialog by flipping `open`.
 *
 * DRAFT MODE (`draft` input — the Create wizard's step 2): the atomic create
 * sends addresses INSIDE the single `POST /api/customers`, so no address API
 * may be called before the customer exists. With `draft` set, saving emits
 * `draftSaved` with the validated {@link AddressRequest} instead of calling
 * the store (which is then not even required in the injector); the wizard
 * collects drafts locally. Backend errors for drafts arrive later, on the
 * atomic POST, as `addresses[i].*` validationErrors handled by the wizard.
 */
@Component({
  selector: 'app-address-form-dialog',
  imports: [ReactiveFormsModule, TranslatePipe, Button, FormField, Select, TextInput, Modal, ModalFooter],
  templateUrl: './address-form-dialog.html',
})
export class AddressFormDialog {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly i18n = inject(I18nService);
  private readonly lookup = inject(LookupCacheService);
  /** Optional: absent in the wizard's injector, required (and asserted) when
   *  the dialog runs in API mode on the Info tab. */
  private readonly store = inject(CustomerDetailStore, { optional: true });

  readonly open = input.required<boolean>();
  /** `null` ⇒ add mode; a value ⇒ edit mode (form pre-filled). */
  readonly address = input<AddressFormValue | null>(null);
  /** Draft mode (Create wizard): save emits `draftSaved`, no API call. */
  readonly draft = input<boolean>(false);
  /** Testid stem; wizard passes `customer-create-address-dialog` (FE-ADR-009). */
  readonly testIdBase = input<string>('customer-detail-address-dialog');

  readonly closed = output<void>();
  readonly saved = output<void>();
  readonly draftSaved = output<AddressRequest>();

  protected readonly form = this.fb.group({
    cityId: new FormControl<number | null>(null),
    districtId: new FormControl<number | null>({ value: null, disabled: true }),
    street: this.fb.control(''),
    houseFlatNumber: this.fb.control(''),
    addressDescription: this.fb.control(''),
  });

  protected readonly busy = signal(false);
  private readonly serverError = signal<ApiError | null>(null);

  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );

  protected readonly isEdit = computed(() => this.address() !== null);

  protected readonly cityOptions = computed<readonly SelectOption[]>(() =>
    this.lookup.cities().map((city) => ({ value: city.cityId, label: city.name })),
  );
  private readonly selectedCityId = computed(() => this.formValue().cityId);
  protected readonly districtOptions = computed<readonly SelectOption[]>(() => {
    const cityId = this.selectedCityId();
    if (cityId === null) return [];
    return this.lookup.districtsOf(cityId).map((d) => ({ value: d.districtId, label: d.name }));
  });

  /** Mock §6.3: all five fields are required before save. */
  protected readonly canSave = computed(() => {
    const value = this.formValue();
    return (
      value.cityId !== null &&
      value.districtId !== null &&
      value.street.trim() !== '' &&
      value.houseFlatNumber.trim() !== '' &&
      value.addressDescription.trim() !== ''
    );
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
    // (Re)initialize whenever the dialog opens: reset for add, pre-fill for
    // edit, and make sure the cascade data is there (idempotent cache calls).
    effect(() => {
      if (!this.open()) return;
      this.lookup.ensureCities();
      this.serverError.set(null);
      this.busy.set(false);
      const address = this.address();
      this.form.reset(
        {
          cityId: address?.cityId ?? null,
          districtId: address?.districtId ?? null,
          street: address?.street ?? '',
          houseFlatNumber: address?.houseFlatNumber ?? '',
          addressDescription: address?.addressDescription ?? '',
        },
        { emitEvent: false },
      );
      const districtControl = this.form.controls.districtId;
      if (address) {
        this.lookup.ensureDistricts(address.cityId);
        districtControl.enable({ emitEvent: false });
      } else {
        districtControl.disable({ emitEvent: false });
      }
      // reset/enable used emitEvent:false, so nudge the value signal once:
      this.form.updateValueAndValidity();
    });

    // Cascade: a USER city change resets the district and loads its options.
    this.form.controls.cityId.valueChanges.pipe(takeUntilDestroyed()).subscribe((cityId) => {
      const districtControl = this.form.controls.districtId;
      districtControl.setValue(null, { emitEvent: false });
      if (cityId === null) {
        districtControl.disable({ emitEvent: false });
      } else {
        districtControl.enable({ emitEvent: false });
        this.lookup.ensureDistricts(cityId);
      }
    });

    // Typing clears the previous server rejection (golden-path behaviour).
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.serverError() !== null) this.serverError.set(null);
    });
  }

  protected onSave(): void {
    if (!this.canSave() || this.busy()) return;
    const raw = this.form.getRawValue();
    const body: AddressRequest = {
      cityId: raw.cityId as number,
      districtId: raw.districtId as number,
      street: raw.street.trim(),
      houseFlatNumber: raw.houseFlatNumber.trim(),
      addressDescription: raw.addressDescription.trim(),
    };
    if (this.draft()) {
      // Wizard step 2: the address travels with the atomic POST later.
      this.draftSaved.emit(body);
      return;
    }
    const store = this.store;
    if (!store) {
      throw new Error('AddressFormDialog: API mode requires CustomerDetailStore');
    }
    this.busy.set(true);
    const address = this.address();
    const request = address
      ? store.updateAddress((address as AddressResponse).addressId, body)
      : store.addAddress(body);
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
