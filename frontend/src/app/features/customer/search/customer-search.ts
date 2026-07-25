import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { matchFieldErrors } from '../../../core/http';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { Button, FormField, Icon, IconButton, Select, TextInput } from '../../../shared/ui';
import { type SelectOption } from '../../../shared/ui';
import { CustomerListStore } from '../state/customer-list.store';
import {
  DEFAULT_PAGE_SIZE,
  PAGE_SIZE_OPTIONS,
  type CustomerSearchCriteria,
  type PageSize,
} from '../model';

/** The five filter controls that can carry a server field error (bare-name
 *  paths from `@RequestParam` validation — core/http parses them). */
const FILTERABLE_CONTROLS = [
  'nationalityId',
  'customerId',
  'gsmNumber',
  'firstName',
  'lastName',
] as const;
type FilterControlName = (typeof FILTERABLE_CONTROLS)[number];

/**
 * Customer Search — the golden-path screen (customer-search-analysis §1).
 *
 * Behaviour contract (backend wins over the mock, FE-ADR-013 §e):
 * - Opens in BROWSE mode: an automatic, criterion-less `GET /api/customers`
 *   (AC-CUST-01-00); the Search button only re-runs a search and stays disabled
 *   while every filter is empty (AC-CUST-01-02) — the recorded resolution of
 *   the two ACs (mock-ui-analysis §6.2 decision).
 * - KR-01 semantics live SERVER-side: this screen only maps inputs to the
 *   documented query parameters and never re-implements matching, folding or
 *   sorting (no `sort` param — KR-04 fixed A-Z).
 * - `accountNumber` / `orderNumber` are rendered DISABLED and are
 *   unrepresentable in the request (FE-ADR-013 §b; a 501 would be a frontend
 *   bug, FE-ADR-008 §6).
 * - Client-side checks (ID length, GSM prefix) are UX only (FE-ADR-007); the
 *   backend remains the authority and its 400 `validationErrors` are bound back
 *   onto the same fields via `matchFieldErrors` (unmatched → banner).
 * - Errors surface through the `messageKey → i18n` chain exclusively
 *   (FE-ADR-008); 401/403/CSRF are fully handled by the core interceptors —
 *   this screen does nothing for them.
 */
@Component({
  selector: 'app-customer-search',
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    Button,
    FormField,
    Icon,
    IconButton,
    Select,
    TextInput,
  ],
  providers: [CustomerListStore],
  templateUrl: './customer-search.html',
})
export class CustomerSearch {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly i18n = inject(I18nService);
  protected readonly store = inject(CustomerListStore);

  /** 7 filter fields in mock order (§6.2). The two 501 filters are disabled at
   *  the CONTROL level — our TextInput takes disabled state only from the form,
   *  so they can never carry a value into a request. */
  protected readonly form = this.fb.group({
    nationalityId: this.fb.control(''),
    customerId: this.fb.control(''),
    accountNumber: this.fb.control({ value: '', disabled: true }),
    gsmNumber: this.fb.control(''),
    firstName: this.fb.control(''),
    lastName: this.fb.control(''),
    orderNumber: this.fb.control({ value: '', disabled: true }),
  });

  /** Per-page selector (KR-04: size is always explicit; options per scope §2.4). */
  protected readonly sizeControl = new FormControl<PageSize>(DEFAULT_PAGE_SIZE, {
    nonNullable: true,
  });
  protected readonly pageSizeOptions: readonly SelectOption[] = PAGE_SIZE_OPTIONS.map((size) => ({
    value: size,
    label: String(size),
  }));

  /** Client-side UX validation keys (mock §6.2); cleared as the user types. */
  private readonly clientErrors = signal<Partial<Record<FilterControlName, string>>>({});

  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  /** AC-CUST-01-02: Search stays disabled while every (enabled) filter is empty. */
  protected readonly allEmpty = computed(() => {
    const value = this.formValue();
    return FILTERABLE_CONTROLS.every((name) => !(value[name] ?? '').trim());
  });

  /** Server 400 field errors bound to the controls; leftovers go to the banner
   *  (matchFieldErrors contract — unmatched are never dropped). */
  private readonly fieldErrorMatch = computed(() =>
    matchFieldErrors(this.store.error()?.fieldErrors ?? [], [...FILTERABLE_CONTROLS]),
  );
  protected readonly unmatchedFieldErrors = computed(() => this.fieldErrorMatch().unmatched);

  /** Resolved error text per control: client UX error wins, then server error. */
  protected errorTextFor(control: FilterControlName): string | undefined {
    const clientKey = this.clientErrors()[control];
    const serverKey = this.fieldErrorMatch().matched.get(control)?.messageKey;
    const key = clientKey ?? serverKey;
    return key ? this.i18n.translate(key) : undefined;
  }

  /** Banner text for the failure states (11/12 & unmatched 400s). */
  protected readonly bannerText = computed(() => {
    const error = this.store.error();
    if (this.store.status() !== 'error') return null;
    return this.i18n.translate(error?.messageKey ?? 'MSG-INTERNAL-ERROR');
  });

  // --- view-state helpers ----------------------------------------------------
  protected readonly hasAppliedCriteria = computed(
    () => Object.keys(this.store.criteria()).length > 0,
  );
  /** State 1: first load — no rows yet, skeleton replaces the table. */
  protected readonly initialLoading = computed(
    () => this.store.isLoading() && this.store.customers().length === 0,
  );
  /** State 5: reloading with rows on screen — table stays, thin bar on top. */
  protected readonly reloading = computed(
    () => this.store.isLoading() && this.store.customers().length > 0,
  );
  /** State 4: a search returned nothing → MSG-CUST-NOT-FOUND + create call. */
  protected readonly searchEmpty = computed(
    () => this.store.isEmpty() && this.hasAppliedCriteria(),
  );
  /** State 3: browse mode with zero customers — different copy (no search ran). */
  protected readonly browseEmpty = computed(
    () => this.store.isEmpty() && !this.hasAppliedCriteria(),
  );

  /** Pagination range: "{from}–{to} of {total}" (parameterized, FE-ADR-012 §h). */
  protected readonly rangeText = computed(() => {
    const total = this.store.totalElements();
    if (total === 0) return '';
    const from = this.store.page() * this.store.size() + 1;
    const to = Math.min((this.store.page() + 1) * this.store.size(), total);
    return this.i18n.translate('UI-PAGINATION-RANGE', { from, to, total });
  });

  /** Mock §6.2 truncation: ≤7 pages → all; else 1 … (p-1) p (p+1) … last. */
  protected readonly pageItems = computed<readonly (number | 'gap')[]>(() => {
    const total = this.store.totalPages();
    const current = this.store.page() + 1; // 1-based for display
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i + 1);
    }
    const wanted = [...new Set([1, current - 1, current, current + 1, total])]
      .filter((n) => n >= 1 && n <= total)
      .sort((a, b) => a - b);
    const items: (number | 'gap')[] = [];
    let previous = 0;
    for (const n of wanted) {
      if (n - previous > 1) items.push('gap');
      items.push(n);
      previous = n;
    }
    return items;
  });

  constructor() {
    // AC-CUST-01-00: the post-login list — an automatic criterion-less browse.
    this.store.browse();

    this.sizeControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((size) => this.store.setPageSize(size));

    // Mock §6.2: typing hides validation errors; fully clearing a field removes
    // that APPLIED filter immediately (no second Search needed).
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => {
      if (Object.keys(this.clientErrors()).length > 0) {
        this.clientErrors.set({});
      }
      this.dropEmptiedAppliedFilters(value);
    });
  }

  protected onSearch(): void {
    if (this.allEmpty()) return; // button is disabled; guards a raw form submit
    const raw = this.form.getRawValue();
    const errors: Partial<Record<FilterControlName, string>> = {};
    const nationalityId = raw.nationalityId.trim();
    if (nationalityId && nationalityId.length !== 11) {
      errors.nationalityId = 'UI-SEARCH-VAL-ID-LENGTH';
    }
    const gsmNumber = raw.gsmNumber.trim();
    if (gsmNumber && !gsmNumber.startsWith('05')) {
      errors.gsmNumber = 'UI-SEARCH-VAL-GSM-PREFIX';
    }
    this.clientErrors.set(errors);
    if (Object.keys(errors).length > 0) return; // UX gate only (FE-ADR-007)
    this.store.applyFilters(this.criteriaFromForm());
  }

  /** Clear resets the filters AND returns to browse mode (mock §6.2 decision). */
  protected onClear(): void {
    this.form.reset();
    this.clientErrors.set({});
    this.store.browse();
  }

  protected retry(): void {
    this.store.reload();
  }

  protected goToPage(oneBased: number): void {
    this.store.goToPage(oneBased - 1);
  }

  private criteriaFromForm(): CustomerSearchCriteria {
    const raw = this.form.getRawValue();
    const criteria: Record<string, string> = {};
    for (const name of FILTERABLE_CONTROLS) {
      const value = raw[name].trim();
      if (value) criteria[name] = value; // empty string ≠ a filter (analysis §3)
    }
    return criteria;
  }

  private dropEmptiedAppliedFilters(value: Partial<Record<string, string>>): void {
    const applied = this.store.criteria() as Record<string, string>;
    const appliedKeys = Object.keys(applied) as FilterControlName[];
    if (appliedKeys.length === 0) return;
    const remaining: Record<string, string> = {};
    let removed = false;
    for (const key of appliedKeys) {
      if ((value[key] ?? '').trim() === '') {
        removed = true;
      } else {
        remaining[key] = applied[key];
      }
    }
    if (!removed) return;
    if (Object.keys(remaining).length === 0) {
      this.store.browse();
    } else {
      this.store.applyFilters(remaining);
    }
  }
}
