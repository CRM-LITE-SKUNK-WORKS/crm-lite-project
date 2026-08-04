import { Component, ElementRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup, NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import {
  CatalogApiService,
  characteristicErrorKey,
  characteristicValidators,
  todayIso,
  type CharacteristicResponse,
} from '../../../core/catalog';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { Skeleton } from '../../../shared/patterns';
import {
  Button,
  DatePicker,
  FormField,
  Icon,
  Select,
  TextInput,
  type SelectOption,
} from '../../../shared/ui';
import { SaleBasketStore } from '../../order';
import { AddressFormDialog } from '../address/address-form-dialog';
import { CustomerDetailStore } from '../state/customer-detail.store';
import type { AddressResponse } from '../model';

/** One offer's block on the screen (AC-SALE-01-20: one block per basket item). */
interface ProductBlock {
  readonly offerId: number;
  readonly offerName: string;
  readonly fields: readonly CharacteristicResponse[];
}

/** Form control name for one characteristic. The offer id is part of the key
 *  because the same characteristic can appear under two offers. */
function controlKey(offerId: number, characteristicId: number): string {
  return `${offerId}:${characteristicId}`;
}

/**
 * Step 2 — Product configuration (AC-SALE-01-10/11/12/17/18/19/20/21).
 *
 * Mock layout (scope §2B.7): one card per basket offer with a 2-column field
 * grid, then the "Address info" card with selectable address tiles plus an
 * "Add new address" tile.
 *
 * ── CONTROL PER DATA TYPE (AC-SALE-01-17) ────────────────────────────────────
 * The catalog declares each characteristic's `dataType`, and the screen renders
 * the control that can only produce that type:
 *
 *   NUMBER   TextInput, `numericOnly` — a letter cannot be typed or pasted
 *   BOOLEAN  Select of `true` / `false` — a format error is unreachable
 *   DATE     DatePicker — masked `dd.mm.yyyy` in, ISO `yyyy-MM-dd` out
 *   TEXT     TextInput
 *
 * ⚠ The mock renders an XDSL *password* field. `password` is NOT a contract data
 * type, and `shared/ui` has no PasswordInput by design (FE-ADR-005 §P5); such a
 * characteristic is declared TEXT and renders as one (FE-ADR-013 §e).
 *
 * ── VALIDATION ON LBL-NEXT (AC-SALE-01-12/18/19) ─────────────────────────────
 * {@link validate} is what the wizard footer calls; it marks every control
 * touched, paints `MSG-VAL-CHAR-REQUIRED` / `MSG-VAL-CHAR-FORMAT` UNDER the
 * offending field, scrolls the first one into view and answers `false`, so the
 * Submit screen never opens over an unfilled mandatory field. The rules
 * themselves live in `core/catalog/characteristic-validation` — a deliberate
 * mirror of product-service's `CharacteristicValidationRules`, which stays the
 * authority: `POST /api/orders` re-validates and its rejections still render on
 * step 3 (FE-ADR-007 §3).
 *
 * ADDRESSES come from {@link CustomerDetailStore} (provided by the wizard, fed
 * by its narrow `loadAddresses` intent) rather than from the API service
 * directly, because `AddressFormDialog` in API mode writes through that store —
 * reusing the FR-ADDR-02 dialog means reusing its store contract too.
 */
@Component({
  selector: 'app-product-configuration-step',
  imports: [
    TranslatePipe,
    ReactiveFormsModule,
    Skeleton,
    Button,
    Icon,
    FormField,
    TextInput,
    Select,
    DatePicker,
    AddressFormDialog,
  ],
  templateUrl: './product-configuration-step.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
})
export class ProductConfigurationStep {
  private readonly catalog = inject(CatalogApiService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly i18n = inject(I18nService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly addressStore = inject(CustomerDetailStore);
  protected readonly basket = inject(SaleBasketStore);

  protected readonly schemaLoading = signal(true);
  protected readonly schemaError = signal<string | null>(null);
  protected readonly blocks = signal<readonly ProductBlock[]>([]);
  protected readonly addDialogOpen = signal(false);
  /** Shown only after the user has interacted and left the address unset. */
  protected readonly addressTouched = signal(false);
  /** Set by {@link validate}; drives the "correct the highlighted fields" banner. */
  private readonly nextAttempted = signal(false);

  /** Reactive Forms (FE-ADR-007): one control per characteristic, seeded from
   *  the store so returning from step 3 restores what was typed
   *  (AC-SALE-01-14), and validated against the characteristic's declared
   *  `dataType` / `mandatory` (AC-SALE-01-18/19). */
  protected readonly form = new FormGroup({});

  /**
   * Bumped whenever anything that can change a control's ERROR PRESENTATION
   * happens: a value edit, a status recompute, a blur (which flips `touched`
   * without emitting anything), and `validate()`'s `markAllAsTouched`.
   *
   * Reactive Forms are not signals, so under zoneless change detection
   * (FE-ADR-006 §7) nothing else would tell the error computeds to re-run.
   */
  private readonly formRevision = signal(0);

  protected readonly selectedAddressId = this.basket.serviceAddressId;
  protected readonly addresses = this.addressStore.addresses;
  protected readonly lang = this.i18n.lang;

  /** Greys out past days in every DATE characteristic's calendar. It is only the
   *  CALENDAR half of the rule — the picker treats `min` as a UX affordance and
   *  raises no error for a past date TYPED into the masked input, which is why
   *  `charNotPastValidator` exists alongside it. */
  protected readonly minDate = todayIso();

  private readonly addressesLoading = computed(() => {
    const status = this.addressStore.addressesStatus();
    return status === 'idle' || status === 'loading';
  });
  protected readonly loading = computed(() => this.schemaLoading() || this.addressesLoading());

  /** Either read failing blocks the step; both surface the same retry. */
  protected readonly loadError = computed(
    () => this.schemaError() ?? this.addressStore.addressesError()?.messageKey ?? null,
  );

  /** Ids the screen has already seen, so the address created through the
   *  dialog can be spotted in the reloaded list and auto-selected. */
  private readonly knownAddressIds = signal<ReadonlySet<number>>(new Set());
  private readonly selectNewest = signal(false);

  protected readonly showAddressError = computed(
    () => this.addressTouched() && this.selectedAddressId() === null,
  );

  /**
   * Resolved error text per control name — TOUCHED controls only, so a field
   * nobody has visited yet is not painted red on arrival (it is painted the
   * moment `LBL-NEXT` touches everything).
   */
  protected readonly errorTexts = computed<ReadonlyMap<string, string>>(() => {
    this.formRevision();
    const texts = new Map<string, string>();
    for (const block of this.blocks()) {
      for (const field of block.fields) {
        const name = controlKey(block.offerId, field.characteristicId);
        const control = this.form.get(name);
        if (!control?.touched) continue;
        const key = characteristicErrorKey(control.errors);
        if (key) texts.set(name, this.i18n.translate(key));
      }
    }
    return texts;
  });

  /** AC-SALE-01-12: after a rejected `LBL-NEXT`, say so at the top too — the
   *  offending field can easily be several product blocks down the page. */
  protected readonly showInvalidBanner = computed(() => {
    this.formRevision();
    return this.nextAttempted() && (this.form.invalid || this.selectedAddressId() === null);
  });

  constructor() {
    this.loadSchemas();
    const customerNumber = this.basket.customerNumber();
    if (customerNumber !== null) this.addressStore.loadAddresses(customerNumber);

    // Preselect the PRIMARY address, and after an add, the newly created one.
    // ONLY the address list is tracked; everything this effect reads AND writes
    // is untracked, or it would retrigger itself forever.
    effect(() => {
      const rows = this.addresses();
      if (rows.length === 0) return;
      untracked(() => {
        const known = this.knownAddressIds();
        const fresh = rows.find((a) => !known.has(a.addressId));

        if (this.selectNewest() && fresh) {
          this.basket.setServiceAddressId(fresh.addressId);
          this.selectNewest.set(false);
        } else if (this.selectedAddressId() === null) {
          // Only when nothing is chosen yet, so returning from step 3 keeps the
          // user's own choice (AC-SALE-01-14).
          const primary = rows.find((a) => a.primary) ?? rows[0];
          if (primary) this.basket.setServiceAddressId(primary.addressId);
        }
        this.knownAddressIds.set(new Set(rows.map((a) => a.addressId)));
      });
    });

    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.pushValuesToStore();
      this.bumpRevision();
    });
    this.form.statusChanges.pipe(takeUntilDestroyed()).subscribe(() => this.bumpRevision());
  }

  /**
   * AC-SALE-01-12/18/19 — the `LBL-NEXT` gate, called by the wizard footer.
   * Returns `true` when the Submit screen may open.
   */
  validate(): boolean {
    this.nextAttempted.set(true);
    this.addressTouched.set(true);
    this.form.markAllAsTouched();
    this.bumpRevision();

    const valid = this.form.valid && this.selectedAddressId() !== null;
    if (!valid) this.revealFirstProblem();
    return valid;
  }

  /** A field the user cannot see is a field they cannot fix — so the first
   *  offending control (or the address card) is brought into view and focused. */
  private revealFirstProblem(): void {
    for (const block of this.blocks()) {
      for (const field of block.fields) {
        const name = controlKey(block.offerId, field.characteristicId);
        if (this.form.get(name)?.valid !== false) continue;
        const testId = `sale-char-${block.offerId}-${field.characteristicId}`;
        const element = this.host.nativeElement.querySelector<HTMLElement>(
          `[data-testid="${testId}"]`,
        );
        element?.scrollIntoView({ block: 'center' });
        element?.focus();
        return;
      }
    }
    if (this.selectedAddressId() === null) {
      this.host.nativeElement
        .querySelector<HTMLElement>('[data-testid="sale-address-card"]')
        ?.scrollIntoView({ block: 'center' });
    }
  }

  private bumpRevision(): void {
    this.formRevision.update((n) => n + 1);
  }

  private loadSchemas(): void {
    this.schemaLoading.set(true);
    this.schemaError.set(null);

    const items = this.basket.items();
    if (items.length === 0) {
      this.blocks.set([]);
      this.basket.setMandatoryIds(new Set());
      this.schemaLoading.set(false);
      return;
    }

    forkJoin(items.map((i) => this.catalog.getOfferCharacteristics(i.offer.offerId))).subscribe({
      next: (schemas) => {
        this.blocks.set(
          items.map((item, index) => ({
            offerId: item.offer.offerId,
            offerName: item.offer.offerName,
            fields: schemas[index] ?? [],
          })),
        );

        // The wizard footer owns the Submit button, so the mandatory ids must
        // live in the shared store rather than in this component.
        const mandatory = new Set<number>();
        for (const schema of schemas) {
          for (const field of schema) if (field.mandatory) mandatory.add(field.characteristicId);
        }
        this.basket.setMandatoryIds(mandatory);

        this.rebuildForm();
        this.schemaLoading.set(false);
      },
      error: () => {
        this.schemaError.set('UI-ERROR-GENERIC');
        this.schemaLoading.set(false);
      },
    });
  }

  /** Rebuilds the control set for the current schemas, seeded from the store
   *  and validated against each characteristic's declared type. */
  private rebuildForm(): void {
    for (const name of Object.keys(this.form.controls)) {
      this.form.removeControl(name, { emitEvent: false });
    }
    for (const block of this.blocks()) {
      for (const field of block.fields) {
        this.form.addControl(
          controlKey(block.offerId, field.characteristicId),
          this.fb.control(this.basket.valueOf(block.offerId, field.characteristicId), {
            validators: characteristicValidators(field),
          }),
          { emitEvent: false },
        );
      }
    }
    // The controls were added with `emitEvent: false`, so seed the store once
    // by hand. Without this an untouched MANDATORY field would be absent from
    // the request rather than present-and-blank, and the store's rule — blank
    // mandatory values travel so the SERVER also answers MSG-VAL-CHAR-REQUIRED
    // — would silently not apply to fields nobody ever focused.
    this.pushValuesToStore();
    this.bumpRevision();
  }

  private pushValuesToStore(): void {
    const raw = this.form.getRawValue() as Record<string, string | null>;
    for (const [key, value] of Object.entries(raw)) {
      const [offerId, characteristicId] = key.split(':').map(Number);
      if (offerId === undefined || characteristicId === undefined) continue;
      this.basket.setValue(offerId, characteristicId, value ?? '');
    }
  }

  protected controlName(offerId: number, characteristicId: number): string {
    return controlKey(offerId, characteristicId);
  }

  protected errorTextFor(offerId: number, characteristicId: number): string | undefined {
    return this.errorTexts().get(controlKey(offerId, characteristicId));
  }

  /** AC-SALE-01-17 for BOOLEAN: the option VALUES are the backend's wire values
   *  (`true`/`false`), only the labels are translated. */
  protected readonly booleanOptions = computed<readonly SelectOption[]>(() => [
    { value: 'true', label: this.i18n.translate('UI-SALE-CHAR-BOOL-TRUE') },
    { value: 'false', label: this.i18n.translate('UI-SALE-CHAR-BOOL-FALSE') },
  ]);

  /** `touched` flips on blur without emitting on any observable, so the error
   *  computeds are told by hand as focus leaves a field. */
  protected onFieldBlur(): void {
    this.bumpRevision();
  }

  protected retry(): void {
    this.loadSchemas();
    this.addressStore.reloadAddresses();
  }

  protected selectAddress(addressId: number): void {
    this.basket.setServiceAddressId(addressId);
    this.addressTouched.set(true);
  }

  protected openAddDialog(): void {
    this.addDialogOpen.set(true);
  }

  protected closeAddDialog(): void {
    this.addDialogOpen.set(false);
    this.addressTouched.set(true);
  }

  /** FR-ADDR-02 via the shared dialog in API mode: the customer already exists,
   *  so the address is created server-side and the STORE refreshes the list. */
  protected onAddressSaved(): void {
    this.addDialogOpen.set(false);
    this.selectNewest.set(true);
  }

  /** Mock tile line 1: street + house/flat number. */
  protected streetLine(address: AddressResponse): string {
    return `${address.street} ${address.houseFlatNumber}`;
  }

  /** Mock tile line 2: "District, City". */
  protected regionLine(address: AddressResponse): string {
    return `${address.districtName}, ${address.cityName}`;
  }
}
