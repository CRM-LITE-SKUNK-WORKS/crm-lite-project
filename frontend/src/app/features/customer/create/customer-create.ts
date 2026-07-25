import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { type ApiError, isApiError, matchFieldErrors } from '../../../core/http';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { LookupCacheService } from '../../../core/lookup';
import { ConfirmDialog, Stepper, type StepItem } from '../../../shared/patterns';
import {
  Button,
  DatePicker,
  FormField,
  Select,
  TextInput,
  type SelectOption,
} from '../../../shared/ui';
import { AddressCards } from '../address/address-cards';
import { AddressFormDialog } from '../address/address-form-dialog';
import { CustomerApiService } from '../data/customer-api.service';
import { CustomerFlashService } from '../state/customer-flash.service';
import {
  type AddressRequest,
  type AddressResponse,
  type CreateCustomerRequest,
  type Gender,
} from '../model';

/** Mock §6.3 name rule (VR-NAME as UX): letters incl. Turkish, space, '.-  */
const NAME_RE = /^[A-Za-zÀ-ſĞğİıŞşÖöÜüÇç\s'.-]+$/;
/** Mock §6.3 email rule (VR-EMAIL as UX). */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const DEMOGRAPHIC_FIELDS = [
  'firstName',
  'middleName',
  'lastName',
  'fatherName',
  'motherName',
  'birthDate',
  'gender',
  'nationalityId',
] as const;
const CONTACT_FIELDS = ['email', 'mobilePhone', 'homePhone', 'fax'] as const;
type DemographicField = (typeof DEMOGRAPHIC_FIELDS)[number];
type ContactField = (typeof CONTACT_FIELDS)[number];

/**
 * A locally collected address (Create step 2). The atomic create forbids any
 * address API call before the customer exists, so drafts live HERE, with the
 * city/district NAMES resolved from the lookup cache purely for card display.
 * `draftId` is a local counter used only for testids/tracking — it never
 * reaches the wire.
 */
interface AddressDraft extends AddressRequest {
  readonly draftId: number;
  readonly cityName: string;
  readonly districtName: string;
  readonly primary: boolean;
}

/**
 * Create Customer — the 3-step wizard (mock-ui-analysis §6.3; FR-CUST-03,
 * KR-10, VR-*). The LAST screen of the buildable scope.
 *
 * The one non-negotiable: creation is ONE `POST /api/customers` carrying
 * `{demographic, addresses[], contactMedium}` — a single backend transaction
 * (customer-service.md §Atomic create). No partial requests, ever; addresses
 * are local drafts until submit (see {@link AddressDraft}), collected through
 * the SAME AddressFormDialog the Info tab uses, in `draft` mode.
 *
 * MERNIS (KR-10): verification happens INSIDE the POST on the backend —
 * mernis-stub is not gateway-routed (ADR-009), so this screen never calls it;
 * `MSG-CUST-NATID-VERIFICATION-FAILED` (400) and `MSG-MERNIS-UNAVAILABLE`
 * (503) arrive as the POST's answer and resolve through i18n like every other
 * messageKey (FE-ADR-008).
 *
 * Client validation (VR-NAME / VR-NATID / VR-EMAIL / VR-PHONE / VR-MOBILE) is
 * UX only, runs on Next/Create and clears on typing (FE-ADR-007); the mock's
 * hardcoded duplicate-ID list is NOT reproduced — the real answers are the
 * backend's 409 `MSG-CUST-DUP-NATID` (globally unique incl. soft-deleted,
 * ADR-003) and the MERNIS pipeline.
 *
 * No account fields: the atomic create is customer-service's scope only;
 * billing accounts are the separate F6 flow (and the mock's create steps
 * carry no account field either — verified, no conflict to record).
 *
 * No store: this screen holds only request-scoped state (FE-ADR-006 §4), so
 * it calls {@link CustomerApiService} directly and keeps its state in local
 * signals — nothing outlives the screen except the success flash.
 */
@Component({
  selector: 'app-customer-create',
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    Button,
    DatePicker,
    FormField,
    Select,
    TextInput,
    Stepper,
    ConfirmDialog,
    AddressCards,
    AddressFormDialog,
  ],
  templateUrl: './customer-create.html',
})
export class CustomerCreate {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly i18n = inject(I18nService);
  private readonly api = inject(CustomerApiService);
  private readonly lookup = inject(LookupCacheService);
  private readonly flash = inject(CustomerFlashService);
  private readonly router = inject(Router);

  // --- wizard position ------------------------------------------------------
  protected readonly step = signal<0 | 1 | 2>(0);
  protected readonly steps = computed<readonly StepItem[]>(() => [
    { id: 'demographic', label: this.i18n.translate('UI-CREATE-STEP-DEMOGRAPHIC') },
    { id: 'address', label: this.i18n.translate('UI-CREATE-STEP-ADDRESS') },
    { id: 'contact', label: this.i18n.translate('UI-CREATE-STEP-CONTACT') },
  ]);

  // --- step 1: demographic --------------------------------------------------
  protected readonly demographicForm = this.fb.group({
    firstName: this.fb.control(''),
    middleName: this.fb.control(''),
    lastName: this.fb.control(''),
    birthDate: this.fb.control(''),
    gender: this.fb.control<Gender | ''>(''),
    fatherName: this.fb.control(''),
    motherName: this.fb.control(''),
    nationalityId: this.fb.control(''),
  });

  protected readonly lang = computed(() => this.i18n.lang());
  protected readonly todayIso = new Date().toISOString().slice(0, 10);
  protected readonly genderOptions = computed<readonly SelectOption[]>(() => [
    { value: 'Male', label: this.i18n.translate('UI-GENDER-MALE') },
    { value: 'Female', label: this.i18n.translate('UI-GENDER-FEMALE') },
  ]);

  private readonly demographicValue = toSignal(this.demographicForm.valueChanges, {
    initialValue: this.demographicForm.getRawValue(),
  });

  /** Mock §6.3: Next disabled while a REQUIRED demographic field is empty. */
  protected readonly demographicIncomplete = computed(() => {
    const value = this.demographicValue();
    return (
      (value.firstName ?? '').trim() === '' ||
      (value.lastName ?? '').trim() === '' ||
      (value.birthDate ?? '') === '' ||
      (value.gender ?? '') === '' ||
      (value.nationalityId ?? '').trim() === ''
    );
  });

  // --- step 2: address drafts ----------------------------------------------
  protected readonly drafts = signal<readonly AddressDraft[]>([]);
  private draftSeq = 0;
  protected readonly addressDialogOpen = signal(false);
  protected readonly addressDialogTarget = signal<AddressDraft | null>(null);
  protected readonly deleteDraftTarget = signal<AddressDraft | null>(null);

  /** Drafts rendered through the shared AddressCards, which expects the
   *  server shape — the local id/names stand in (client draft state, not
   *  fake server data: nothing here claims to be persisted). */
  protected readonly draftCards = computed<readonly AddressResponse[]>(() =>
    this.drafts().map((draft) => ({
      addressId: draft.draftId,
      cityId: draft.cityId,
      cityName: draft.cityName,
      districtId: draft.districtId,
      districtName: draft.districtName,
      street: draft.street,
      houseFlatNumber: draft.houseFlatNumber,
      addressDescription: draft.addressDescription,
      primary: draft.primary,
    })),
  );

  // --- step 3: contact ------------------------------------------------------
  protected readonly contactForm = this.fb.group({
    email: this.fb.control(''),
    mobilePhone: this.fb.control(''),
    homePhone: this.fb.control(''),
    fax: this.fb.control(''),
  });
  private readonly contactValue = toSignal(this.contactForm.valueChanges, {
    initialValue: this.contactForm.getRawValue(),
  });
  /** Mock §6.3: Create disabled while email or mobile is empty. */
  protected readonly contactIncomplete = computed(() => {
    const value = this.contactValue();
    return (value.email ?? '').trim() === '' || (value.mobilePhone ?? '').trim() === '';
  });

  // --- submit / errors ------------------------------------------------------
  protected readonly busy = signal(false);
  private readonly serverError = signal<ApiError | null>(null);
  private readonly clientErrors = signal<Partial<Record<DemographicField | ContactField, string>>>(
    {},
  );

  private readonly fieldErrorMatch = computed(() =>
    matchFieldErrors(this.serverError()?.fieldErrors ?? [], [
      ...DEMOGRAPHIC_FIELDS,
      ...CONTACT_FIELDS,
    ]),
  );
  /** Section-level failures + address-row errors — surfaced in the banner,
   *  never dropped (core/http contract, scope §4.11). */
  protected readonly unmatchedFieldErrors = computed(() => this.fieldErrorMatch().unmatched);
  protected readonly bannerText = computed(() => {
    const error = this.serverError();
    return error ? this.i18n.translate(error.messageKey) : null;
  });

  protected errorTextFor(field: DemographicField | ContactField): string | undefined {
    const clientKey = this.clientErrors()[field];
    const serverKey = this.fieldErrorMatch().matched.get(field)?.messageKey;
    // The natid-specific whole-request rejections belong ON the field too:
    const natIdKey =
      field === 'nationalityId' &&
      (this.serverError()?.messageKey === 'MSG-CUST-DUP-NATID' ||
        this.serverError()?.messageKey === 'MSG-CUST-NATID-VERIFICATION-FAILED')
        ? this.serverError()?.messageKey
        : undefined;
    const key = clientKey ?? serverKey ?? natIdKey;
    return key ? this.i18n.translate(key) : undefined;
  }

  constructor() {
    // Typing clears both client checks and the previous server rejection.
    this.demographicForm.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.clearErrors());
    this.contactForm.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.clearErrors());
  }

  private clearErrors(): void {
    if (Object.keys(this.clientErrors()).length > 0) this.clientErrors.set({});
    if (this.serverError() !== null) this.serverError.set(null);
  }

  // --- navigation -----------------------------------------------------------
  protected cancel(): void {
    void this.router.navigate(['/customers']);
  }
  protected previous(): void {
    this.step.update((current) => (current === 2 ? 1 : 0));
  }

  /** Step 1 → 2: the mock's submit-time checks (UX only, FE-ADR-007). */
  protected nextFromDemographic(): void {
    if (this.demographicIncomplete()) return;
    const raw = this.demographicForm.getRawValue();
    const errors: Partial<Record<DemographicField, string>> = {};
    if (!NAME_RE.test(raw.firstName.trim())) errors.firstName = 'MSG-VAL-NAME';
    if (!NAME_RE.test(raw.lastName.trim())) errors.lastName = 'MSG-VAL-NAME';
    if (raw.birthDate > this.todayIso) {
      errors.birthDate = 'MSG-VAL-BIRTHDATE';
    } else if (raw.birthDate > this.isoYearsAgo(18)) {
      errors.birthDate = 'MSG-VAL-AGE-MIN';
    }
    if (raw.nationalityId.trim().length !== 11) errors.nationalityId = 'MSG-VAL-NATID';
    this.clientErrors.set(errors);
    if (Object.keys(errors).length > 0) return;
    this.step.set(1);
  }

  protected nextFromAddress(): void {
    if (this.drafts().length === 0) return; // ≥1 address (mock §6.3 / contract)
    this.step.set(2);
  }

  // --- address drafts -------------------------------------------------------
  protected openAddAddress(): void {
    this.addressDialogTarget.set(null);
    this.addressDialogOpen.set(true);
  }
  protected openEditAddress(card: AddressResponse): void {
    const target = this.drafts().find((draft) => draft.draftId === card.addressId) ?? null;
    this.addressDialogTarget.set(target);
    this.addressDialogOpen.set(true);
  }
  protected closeAddressDialog(): void {
    this.addressDialogOpen.set(false);
    this.addressDialogTarget.set(null);
  }

  /** The shared dialog validated the fields; names resolve from the lookup
   *  cache the dialog itself just ensured (display only, never sent). */
  protected onDraftSaved(request: AddressRequest): void {
    const cityName = this.lookup.cities().find((c) => c.cityId === request.cityId)?.name ?? '';
    const districtName =
      this.lookup.districtsOf(request.cityId).find((d) => d.districtId === request.districtId)
        ?.name ?? '';
    const editing = this.addressDialogTarget();
    if (editing) {
      this.drafts.update((drafts) =>
        drafts.map((draft) =>
          draft.draftId === editing.draftId
            ? { ...draft, ...request, cityName, districtName }
            : draft,
        ),
      );
    } else {
      const draft: AddressDraft = {
        ...request,
        draftId: ++this.draftSeq,
        cityName,
        districtName,
        primary: this.drafts().length === 0, // first address is primary (contract)
      };
      this.drafts.update((drafts) => [...drafts, draft]);
    }
    this.closeAddressDialog();
  }

  /** Exactly one primary, always — flipping assigns it to one draft only. */
  protected setPrimaryDraft(card: AddressResponse): void {
    this.drafts.update((drafts) =>
      drafts.map((draft) => ({ ...draft, primary: draft.draftId === card.addressId })),
    );
  }

  protected askDeleteDraft(card: AddressResponse): void {
    const target = this.drafts().find((draft) => draft.draftId === card.addressId) ?? null;
    this.deleteDraftTarget.set(target); // primary can't reach here (card disables it)
  }
  protected confirmDeleteDraft(): void {
    const target = this.deleteDraftTarget();
    if (!target) return;
    this.drafts.update((drafts) => drafts.filter((draft) => draft.draftId !== target.draftId));
    this.deleteDraftTarget.set(null);
  }
  protected cancelDeleteDraft(): void {
    this.deleteDraftTarget.set(null);
  }

  // --- submit (FR-CUST-03 — ONE atomic POST) --------------------------------
  protected onCreate(): void {
    if (this.contactIncomplete() || this.busy()) return; // double-submit guard
    const contact = this.contactForm.getRawValue();
    const errors: Partial<Record<ContactField, string>> = {};
    const email = contact.email.trim();
    if (!EMAIL_RE.test(email)) errors.email = 'MSG-VAL-EMAIL';
    const mobile = contact.mobilePhone.trim();
    if (mobile.length !== 11 || !mobile.startsWith('05')) errors.mobilePhone = 'MSG-VAL-PHONE';
    const home = contact.homePhone.trim();
    if (home && (home.length !== 11 || !home.startsWith('0'))) errors.homePhone = 'MSG-VAL-PHONE';
    const fax = contact.fax.trim();
    if (fax && (fax.length !== 11 || !fax.startsWith('0'))) errors.fax = 'MSG-VAL-PHONE';
    this.clientErrors.set(errors);
    if (Object.keys(errors).length > 0) return; // UX gate only (FE-ADR-007)

    const demographic = this.demographicForm.getRawValue();
    const body: CreateCustomerRequest = {
      demographic: {
        firstName: demographic.firstName.trim(),
        middleName: demographic.middleName.trim() || null,
        lastName: demographic.lastName.trim(),
        fatherName: demographic.fatherName.trim() || null,
        motherName: demographic.motherName.trim() || null,
        birthDate: demographic.birthDate,
        gender: demographic.gender as Gender,
        nationalityId: demographic.nationalityId.trim(),
      },
      addresses: this.drafts().map((draft) => ({
        cityId: draft.cityId,
        districtId: draft.districtId,
        street: draft.street,
        houseFlatNumber: draft.houseFlatNumber,
        addressDescription: draft.addressDescription,
        primary: draft.primary,
      })),
      contactMedium: {
        email,
        mobilePhone: mobile,
        homePhone: home || null,
        fax: fax || null,
      },
    };

    this.busy.set(true);
    this.api.create(body).subscribe({
      next: (created) => {
        // 201 → flash (mock's eds_flash) and land on the new customer's Info.
        this.flash.set('UI-CREATE-TOAST-SUCCESS');
        void this.router.navigate(['/customers', created.customerNumber]);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const apiError = isApiError(error) ? error : null;
        this.serverError.set(apiError);
        this.step.set(this.stepForError(apiError));
      },
    });
  }

  /** Jump to the step that owns the failure so the user SEES it (the banner
   *  is visible on every step regardless). */
  private stepForError(error: ApiError | null): 0 | 1 | 2 {
    if (!error) return 2;
    if (
      error.messageKey === 'MSG-CUST-DUP-NATID' ||
      error.messageKey === 'MSG-CUST-NATID-VERIFICATION-FAILED'
    ) {
      return 0; // natid concerns live on step 1
    }
    const fields = error.fieldErrors ?? [];
    if (fields.some((field) => field.scope === 'demographic')) return 0;
    if (fields.some((field) => field.scope === 'addresses')) return 1;
    if (fields.some((field) => field.scope === 'contactMedium')) return 2;
    if (fields.some((field) => DEMOGRAPHIC_FIELDS.some((name) => name === field.leaf))) return 0;
    return 2; // 503s and the rest: stay where the user pressed Create
  }

  private isoYearsAgo(years: number): string {
    const date = new Date();
    date.setFullYear(date.getFullYear() - years);
    return date.toISOString().slice(0, 10);
  }
}
