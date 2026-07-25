import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { type ApiError, isApiError, matchFieldErrors } from '../../../core/http';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { Modal, ModalFooter } from '../../../shared/patterns';
import {
  Button,
  DatePicker,
  FormField,
  Select,
  TextInput,
  type SelectOption,
} from '../../../shared/ui';
import { CustomerDetailStore } from '../state/customer-detail.store';
import {
  type CustomerDetailResponse,
  type Gender,
  type UpdateCustomerRequest,
} from '../model';

const FIELDS = [
  'firstName',
  'middleName',
  'lastName',
  'fatherName',
  'motherName',
  'birthDate',
  'gender',
  'nationalityId',
] as const;
type FieldName = (typeof FIELDS)[number];

/** Mock §6.3 name rule (VR-NAME as UX): letters incl. Turkish, space, '.-  */
const NAME_RE = /^[A-Za-zÀ-ſĞğİıŞşÖöÜüÇç\s'.-]+$/;

/**
 * "Customer info update" dialog (mock §6.4: 680px modal whose body is Create
 * step 1 verbatim — 8 fields, 1fr 1fr grid). FR-CUST-04.
 *
 * - `PUT /api/customers/{n}` sends the FLAT demographic body (doc §J — not
 *   wrapped in `demographic`); dates travel ISO, displayed `dd.MM.yyyy` by the
 *   DatePicker (scope §2.6); gender wire values stay `"Male"/"Female"` while
 *   the option labels localize (scope §2.7).
 * - Client checks are the mock's, run ON SAVE and cleared on typing — UX only,
 *   the backend stays the authority (FE-ADR-007). The mock's hardcoded
 *   duplicate-ID check is NOT reproduced (edit skips it anyway, §6.3; the real
 *   answer is the backend's 409/MERNIS pipeline).
 * - Server 400s land per-field via `matchFieldErrors`; everything unmatched
 *   goes to the dialog banner (never dropped).
 */
@Component({
  selector: 'app-demographic-form-dialog',
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    Button,
    DatePicker,
    FormField,
    Select,
    TextInput,
    Modal,
    ModalFooter,
  ],
  templateUrl: './demographic-form-dialog.html',
})
export class DemographicFormDialog {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly i18n = inject(I18nService);
  private readonly store = inject(CustomerDetailStore);

  readonly open = input.required<boolean>();
  readonly customer = input.required<CustomerDetailResponse | null>();

  readonly closed = output<void>();
  readonly saved = output<void>();

  protected readonly form = this.fb.group({
    firstName: this.fb.control(''),
    middleName: this.fb.control(''),
    lastName: this.fb.control(''),
    birthDate: this.fb.control(''),
    gender: this.fb.control<Gender | ''>(''),
    fatherName: this.fb.control(''),
    motherName: this.fb.control(''),
    nationalityId: this.fb.control(''),
  });

  protected readonly busy = signal(false);
  private readonly serverError = signal<ApiError | null>(null);
  private readonly clientErrors = signal<Partial<Record<FieldName, string>>>({});

  protected readonly lang = computed(() => this.i18n.lang());
  /** DatePicker upper bound (mock §6.3: `max = today`). */
  protected readonly todayIso = new Date().toISOString().slice(0, 10);

  protected readonly genderOptions = computed<readonly SelectOption[]>(() => [
    { value: 'Male', label: this.i18n.translate('UI-GENDER-MALE') },
    { value: 'Female', label: this.i18n.translate('UI-GENDER-FEMALE') },
  ]);

  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  /** Mock §6.3: save disabled while any REQUIRED field is empty. */
  protected readonly canSave = computed(() => {
    const value = this.formValue();
    return (
      (value.firstName ?? '').trim() !== '' &&
      (value.lastName ?? '').trim() !== '' &&
      (value.birthDate ?? '') !== '' &&
      (value.gender ?? '') !== '' &&
      (value.nationalityId ?? '').trim() !== ''
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
    const clientKey = this.clientErrors()[field];
    const serverKey = this.fieldErrorMatch().matched.get(field)?.messageKey;
    const key = clientKey ?? serverKey;
    return key ? this.i18n.translate(key) : undefined;
  }

  constructor() {
    // Pre-fill from the live record every time the dialog opens.
    effect(() => {
      if (!this.open()) return;
      const customer = this.customer();
      this.serverError.set(null);
      this.clientErrors.set({});
      this.busy.set(false);
      this.form.reset({
        firstName: customer?.firstName ?? '',
        middleName: customer?.middleName ?? '',
        lastName: customer?.lastName ?? '',
        birthDate: customer?.birthDate ?? '',
        gender: customer?.gender ?? '',
        fatherName: customer?.fatherName ?? '',
        motherName: customer?.motherName ?? '',
        nationalityId: customer?.nationalityId ?? '',
      });
    });

    // Typing hides both client and server errors (mock §6.2/§6.3 behaviour).
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (Object.keys(this.clientErrors()).length > 0) this.clientErrors.set({});
      if (this.serverError() !== null) this.serverError.set(null);
    });
  }

  protected onSave(): void {
    if (!this.canSave() || this.busy()) return;
    const raw = this.form.getRawValue();
    const errors: Partial<Record<FieldName, string>> = {};

    if (!NAME_RE.test(raw.firstName.trim())) errors.firstName = 'MSG-VAL-NAME';
    if (!NAME_RE.test(raw.lastName.trim())) errors.lastName = 'MSG-VAL-NAME';
    if (raw.birthDate > this.todayIso) {
      errors.birthDate = 'MSG-VAL-BIRTHDATE';
    } else if (raw.birthDate > this.isoYearsAgo(18)) {
      errors.birthDate = 'MSG-VAL-AGE-MIN';
    }
    if (raw.nationalityId.trim().length !== 11) errors.nationalityId = 'MSG-VAL-NATID';

    this.clientErrors.set(errors);
    if (Object.keys(errors).length > 0) return; // UX gate only (FE-ADR-007)

    const body: UpdateCustomerRequest = {
      firstName: raw.firstName.trim(),
      middleName: raw.middleName.trim() || null,
      lastName: raw.lastName.trim(),
      fatherName: raw.fatherName.trim() || null,
      motherName: raw.motherName.trim() || null,
      birthDate: raw.birthDate,
      gender: raw.gender as Gender,
      nationalityId: raw.nationalityId.trim(),
    };
    this.busy.set(true);
    this.store.updateDemographic(body).subscribe({
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

  private isoYearsAgo(years: number): string {
    const date = new Date();
    date.setFullYear(date.getFullYear() - years);
    return date.toISOString().slice(0, 10);
  }
}
