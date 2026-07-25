import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { matchFieldErrors } from '../../../core/http';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import { Button, FormField, TextInput } from '../../../shared/ui';
import {
  EmptyState,
  Pagination,
  Skeleton,
  Table,
  TableCellDef,
  Toast,
  type TableColumn,
} from '../../../shared/patterns';
import { CustomerFlashService } from '../state/customer-flash.service';
import { CustomerListStore } from '../state/customer-list.store';
import {
  PAGE_SIZE_OPTIONS,
  type CustomerDetailResponse,
  type CustomerSearchCriteria,
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
    RouterLink,
    TranslatePipe,
    Button,
    FormField,
    TextInput,
    EmptyState,
    Pagination,
    Skeleton,
    Table,
    TableCellDef,
    Toast,
  ],
  providers: [CustomerListStore],
  templateUrl: './customer-search.html',
})
export class CustomerSearch {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly i18n = inject(I18nService);
  protected readonly store = inject(CustomerListStore);

  private readonly router = inject(Router);

  /** One-shot flash from another customer screen (e.g. delete success) —
   *  consumed exactly once on entry, shown as a Toast (mock §4.4 pattern). */
  protected readonly flashKey = signal<string | null>(inject(CustomerFlashService).consume());

  protected dismissFlash(): void {
    this.flashKey.set(null);
  }

  /** Activated with the Create wizard (scope §4.20/3). */
  protected goToCreate(): void {
    void this.router.navigate(['/customers', 'new']);
  }

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

  /** Per-page options (KR-04: size is always explicit; values per scope §2.4).
   *  The selector itself lives in the shared Pagination pattern now. */
  protected readonly pageSizeOptions: readonly number[] = PAGE_SIZE_OPTIONS;

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

  /** Table columns for the shared Table pattern (mock §6.2, 6 columns).
   *  Headers are resolved HERE — shared/ never translates (scope §4.14);
   *  `translate` reads the language signal, so this recomputes on switch. */
  protected readonly columns = computed<readonly TableColumn[]>(() => [
    { id: 'customerId', header: this.i18n.translate('UI-SEARCH-COL-CUSTOMER-ID') },
    {
      id: 'firstName',
      header: this.i18n.translate('UI-SEARCH-COL-FIRST-NAME'),
      cellClass: 'text-body text-ink',
    },
    {
      id: 'middleName',
      header: this.i18n.translate('UI-SEARCH-COL-SECOND-NAME'),
      cellClass: 'text-body text-ink',
    },
    {
      id: 'lastName',
      header: this.i18n.translate('UI-SEARCH-COL-LAST-NAME'),
      cellClass: 'text-body text-ink',
    },
    {
      id: 'role',
      header: this.i18n.translate('UI-SEARCH-COL-ROLE'),
      cellClass: 'text-body text-ink-soft',
    },
    {
      id: 'nationalityId',
      header: this.i18n.translate('UI-SEARCH-COL-ID-NUMBER'),
      cellClass: 'eds-tabular-nums text-body text-ink',
    },
  ]);

  /** Business-keyed row testid for the Table pattern (FE-ADR-009 §5). */
  protected readonly rowTestIdFor = (customer: CustomerDetailResponse): string =>
    `customer-search-results-row-${customer.customerNumber}`;

  /** Per-page-button aria-label for the Pagination pattern — a function because
   *  the pattern may not translate (scope §4.14) and the label is parameterized. */
  protected readonly pageLabelFor = (page: number): string =>
    this.i18n.translate('UI-PAGINATION-PAGE', { page });

  constructor() {
    // AC-CUST-01-00: the post-login list — an automatic criterion-less browse.
    this.store.browse();

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

  /** Pagination pattern emits ZERO-based pages — the store's own convention. */
  protected onPageChange(page: number): void {
    this.store.goToPage(page);
  }

  /** Narrow the pattern's `number` back to the typed PageSize union; the value
   *  always originates from PAGE_SIZE_OPTIONS, so the lookup cannot miss. */
  protected onSizeChange(size: number): void {
    const match = PAGE_SIZE_OPTIONS.find((option) => option === size);
    if (match) {
      this.store.setPageSize(match);
    }
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
