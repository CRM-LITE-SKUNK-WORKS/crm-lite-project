import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { type ApiError, isApiError, matchFieldErrors } from '../../../core/http';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { Modal, ModalFooter } from '../../../shared/patterns';
import { Button, FormField, TextInput } from '../../../shared/ui';
import { CustomerDetailStore } from '../state/customer-detail.store';
import { type ContactMediumRequest, type ContactMediumResponse } from '../model';

const FIELDS = ['email', 'homePhone', 'mobilePhone', 'fax'] as const;
type FieldName = (typeof FIELDS)[number];

/** Mock §6.3 UX rules (VR-EMAIL / VR-MOBILE / VR-PHONE as client checks). */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * "Contact medium update" dialog (mock §6.4: 560px modal, 4 fields). FR-CNTC-02.
 *
 * Field order is Email → Home → Mobile → Fax in BOTH read and edit (the §6.4
 * recorded rule). Email + mobile are required (create pipeline contract);
 * home phone and fax are optional and travel as `null` when blank
 * (`ContactMediumRequest`). Client checks run on save, clear on typing, and
 * remain UX only — the backend's 400 `MSG-VAL-*` field errors land back on
 * the same controls via `matchFieldErrors` (FE-ADR-007/008).
 */
@Component({
  selector: 'app-contact-form-dialog',
  imports: [ReactiveFormsModule, TranslatePipe, Button, FormField, TextInput, Modal, ModalFooter],
  templateUrl: './contact-form-dialog.html',
})
export class ContactFormDialog {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly i18n = inject(I18nService);
  private readonly store = inject(CustomerDetailStore);

  readonly open = input.required<boolean>();
  readonly contact = input.required<ContactMediumResponse | null>();

  readonly closed = output<void>();
  readonly saved = output<void>();

  protected readonly form = this.fb.group({
    email: this.fb.control(''),
    homePhone: this.fb.control(''),
    mobilePhone: this.fb.control(''),
    fax: this.fb.control(''),
  });

  protected readonly busy = signal(false);
  private readonly serverError = signal<ApiError | null>(null);
  private readonly clientErrors = signal<Partial<Record<FieldName, string>>>({});

  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  /** Email + mobile required (contract) — save stays disabled without them. */
  protected readonly canSave = computed(() => {
    const value = this.formValue();
    return (value.email ?? '').trim() !== '' && (value.mobilePhone ?? '').trim() !== '';
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
    effect(() => {
      if (!this.open()) return;
      const contact = this.contact();
      this.serverError.set(null);
      this.clientErrors.set({});
      this.busy.set(false);
      this.form.reset({
        email: contact?.email ?? '',
        homePhone: contact?.homePhone ?? '',
        mobilePhone: contact?.mobilePhone ?? '',
        fax: contact?.fax ?? '',
      });
    });

    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (Object.keys(this.clientErrors()).length > 0) this.clientErrors.set({});
      if (this.serverError() !== null) this.serverError.set(null);
    });
  }

  protected onSave(): void {
    if (!this.canSave() || this.busy()) return;
    const raw = this.form.getRawValue();
    const errors: Partial<Record<FieldName, string>> = {};

    const email = raw.email.trim();
    if (!EMAIL_RE.test(email)) errors.email = 'MSG-VAL-EMAIL';
    const mobile = raw.mobilePhone.trim();
    if (mobile.length !== 11 || !mobile.startsWith('05')) errors.mobilePhone = 'MSG-VAL-PHONE';
    const home = raw.homePhone.trim();
    if (home && (home.length !== 11 || !home.startsWith('0'))) errors.homePhone = 'MSG-VAL-PHONE';
    const fax = raw.fax.trim();
    if (fax && (fax.length !== 11 || !fax.startsWith('0'))) errors.fax = 'MSG-VAL-PHONE';

    this.clientErrors.set(errors);
    if (Object.keys(errors).length > 0) return; // UX gate only (FE-ADR-007)

    const body: ContactMediumRequest = {
      email,
      mobilePhone: mobile,
      homePhone: home || null,
      fax: fax || null,
    };
    this.busy.set(true);
    this.store.updateContact(body).subscribe({
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
