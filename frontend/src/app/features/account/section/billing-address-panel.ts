import { Component, computed, inject, input, output } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { map } from 'rxjs';
import { TranslatePipe } from '../../../core/i18n';
import { LookupCacheService } from '../../../core/lookup';
import { Button, FormField, IconButton, Select, TextInput, type SelectOption } from '../../../shared/ui';
import { type NewBillingAddress } from './billing-address-option';

/**
 * The "New address" panel that expands INSIDE the Create/Edit billing account
 * modal (BUG-1; scope §4.30).
 *
 * Deliberately an inline panel, not a nested dialog: the bug report asked for a
 * "address creation popup", the mock's markup expands a bordered panel in the
 * modal body, and the mock is binding on layout (FE-ADR-011). Recorded in
 * scope §4.30 — a modal stacked on a modal would also have to solve focus-trap
 * nesting for no gain the mock asks for.
 *
 * Presentational + cascade only. It NEVER calls an API: saving emits
 * {@link save} with the validated {@link NewBillingAddress} and the host chain
 * (dialog → section → Customer Info) performs FR-ADDR-02 through the customer
 * feature's existing store. `busy`/`errorText` are pushed back down so the
 * panel can render the outcome without knowing who produced it.
 *
 * The city→district cascade is the documented one (scope §2.8/§2.9,
 * `address-form-dialog` behaves identically): options come from `core/lookup`
 * (`GET /api/cities`, `/api/cities/{id}/districts` — ADR-002, never hardcoded),
 * the Selects carry ids not names, and District stays DISABLED until a city is
 * chosen; changing the city resets the district.
 *
 * Lifetime = one editing session: the host renders it under an `@if`, so a
 * cancel/save destroys it and the next open starts blank. No reset effect.
 */
@Component({
  selector: 'app-billing-address-panel',
  imports: [ReactiveFormsModule, TranslatePipe, Button, FormField, IconButton, Select, TextInput],
  template: `
    <!-- mock: 1px border, radius-lg, bg-page, padding 16, gap 16 -->
    <div
      class="flex flex-col gap-4 rounded-lg border border-line bg-page p-4"
      [attr.data-testid]="testIdBase()"
    >
      <div class="flex items-center gap-3">
        <span class="flex-1 text-body font-semibold text-ink">
          {{ 'UI-ACCOUNT-NEW-ADDRESS-TITLE' | t }}
        </span>
        <app-icon-button
          icon="x"
          [ariaLabel]="'UI-ACCOUNT-NEW-ADDRESS-CANCEL' | t"
          [testId]="testIdBase() + '-cancel-button'"
          (action)="cancelled.emit()"
        />
      </div>

      @if (errorText(); as text) {
        <div
          class="rounded-md border border-danger-border bg-danger-surface p-3 text-body-sm text-danger-fg"
          role="alert"
          [attr.data-testid]="testIdBase() + '-error'"
        >
          {{ text }}
        </div>
      }

      <form
        [formGroup]="form"
        class="flex flex-col gap-4"
        (ngSubmit)="onSave()"
        [attr.data-testid]="testIdBase() + '-form'"
      >
        <!-- mock: City | District, then Street | House no, then Description -->
        <div class="grid grid-cols-2 gap-4">
          <app-form-field
            [label]="'UI-CREATE-FIELD-CITY' | t"
            [controlId]="testIdBase() + '-city'"
            [required]="true"
            [testId]="testIdBase() + '-city'"
          >
            <app-select
              formControlName="cityId"
              [options]="cityOptions()"
              [controlId]="testIdBase() + '-city'"
              [placeholder]="'UI-ADDRESS-CITY-PLACEHOLDER' | t"
              [testId]="testIdBase() + '-city-select'"
            />
          </app-form-field>

          <app-form-field
            [label]="'UI-CREATE-FIELD-DISTRICT' | t"
            [controlId]="testIdBase() + '-district'"
            [required]="true"
            [testId]="testIdBase() + '-district'"
          >
            <app-select
              formControlName="districtId"
              [options]="districtOptions()"
              [controlId]="testIdBase() + '-district'"
              [placeholder]="'UI-ADDRESS-DISTRICT-PLACEHOLDER' | t"
              [testId]="testIdBase() + '-district-select'"
            />
          </app-form-field>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <app-form-field
            [label]="'UI-CREATE-FIELD-STREET' | t"
            [controlId]="testIdBase() + '-street'"
            [required]="true"
            [testId]="testIdBase() + '-street'"
          >
            <app-text-input
              formControlName="street"
              [controlId]="testIdBase() + '-street'"
              [testId]="testIdBase() + '-street-input'"
            />
          </app-form-field>

          <app-form-field
            [label]="'UI-CREATE-FIELD-HOUSE-NO' | t"
            [controlId]="testIdBase() + '-house-no'"
            [required]="true"
            [testId]="testIdBase() + '-house-no'"
          >
            <app-text-input
              formControlName="houseFlatNumber"
              [controlId]="testIdBase() + '-house-no'"
              [testId]="testIdBase() + '-house-no-input'"
            />
          </app-form-field>
        </div>

        <app-form-field
          [label]="'UI-CREATE-FIELD-DESCRIPTION' | t"
          [controlId]="testIdBase() + '-description'"
          [required]="true"
          [testId]="testIdBase() + '-description'"
        >
          <app-text-input
            formControlName="addressDescription"
            [controlId]="testIdBase() + '-description'"
            [testId]="testIdBase() + '-description-input'"
          />
        </app-form-field>

        <div class="flex justify-end">
          <app-button
            variant="secondary"
            size="sm"
            iconLeading="check"
            [disabled]="!canSave()"
            [loading]="busy()"
            [testId]="testIdBase() + '-save-button'"
            (action)="onSave()"
          >
            {{ 'UI-ACCOUNT-NEW-ADDRESS-SAVE' | t }}
          </app-button>
        </div>
      </form>
    </div>
  `,
})
export class BillingAddressPanel {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly lookup = inject(LookupCacheService);

  /** True while the host's FR-ADDR-02 call is in flight. */
  readonly busy = input<boolean>(false);
  /** Resolved failure text from the host's create attempt (scope §4.14). */
  readonly errorText = input<string | null>(null);
  readonly testIdBase = input<string>('customer-detail-account-dialog-new-address');

  readonly save = output<NewBillingAddress>();
  readonly cancelled = output<void>();

  protected readonly form = this.fb.group({
    cityId: new FormControl<number | null>(null),
    districtId: new FormControl<number | null>({ value: null, disabled: true }),
    street: this.fb.control(''),
    houseFlatNumber: this.fb.control(''),
    addressDescription: this.fb.control(''),
  });

  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );

  protected readonly cityOptions = computed<readonly SelectOption[]>(() =>
    this.lookup.cities().map((city) => ({ value: city.cityId, label: city.name })),
  );
  protected readonly districtOptions = computed<readonly SelectOption[]>(() => {
    const cityId = this.formValue().cityId;
    if (cityId === null) return [];
    return this.lookup.districtsOf(cityId).map((d) => ({ value: d.districtId, label: d.name }));
  });

  /** All five fields required — the same client gate as the address dialog. */
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

  constructor() {
    this.lookup.ensureCities();

    // Cascade: a city change resets the district and loads that city's options.
    // The reset EMITS so `formValue` (and therefore `canSave`) sees the cleared
    // district — a silent reset would leave the save button enabled on a
    // district that no longer belongs to the selected city.
    this.form.controls.cityId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((cityId) => {
        const districtControl = this.form.controls.districtId;
        districtControl.setValue(null, { emitEvent: false });
        if (cityId === null) {
          districtControl.disable();
        } else {
          districtControl.enable();
          this.lookup.ensureDistricts(cityId);
        }
      });
  }

  protected onSave(): void {
    if (!this.canSave() || this.busy()) return;
    const raw = this.form.getRawValue();
    this.save.emit({
      cityId: raw.cityId as number,
      districtId: raw.districtId as number,
      street: raw.street.trim(),
      houseFlatNumber: raw.houseFlatNumber.trim(),
      addressDescription: raw.addressDescription.trim(),
    });
  }
}
