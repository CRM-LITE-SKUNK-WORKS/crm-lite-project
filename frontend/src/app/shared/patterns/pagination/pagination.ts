import { Component, computed, effect, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IconButton, Select, type SelectOption } from '../../ui';
import { isPageSize, type PageSize } from './page-size';

/**
 * Pagination pattern (shared/patterns) — the mock's results-card footer
 * (mock-ui-analysis §6.2): range label | ‹ page numbers › | per-page selector.
 * §7.2 hand-built pattern (FE-ADR-011 §d); extracted from Customer Search.
 *
 * Purely presentational (FE-ADR-003): the current page, page count and size
 * come in; intents go out as `pageChange` (ZERO-based, matching the API and
 * the stores) and `sizeChange`. The pattern never calls anything and never
 * reorders/re-derives data. The per-page selector is always rendered and the
 * chosen size is always emitted explicitly, so the caller can honour KR-04
 * ("size is always sent") — the pattern itself sends nothing.
 *
 * Texts arrive RESOLVED (`'KEY' | t` at the call site, scope §4.14). The
 * page-button aria-label is per-page parameterized, so it comes in as a
 * `pageLabelFor(page)` function — translation still happens at the caller.
 *
 * Truncation is the recorded mock rule (§6.2, applied per scope §2A.4):
 * ≤ 7 pages → all; otherwise `1 … (p-1) p (p+1) … last` with non-interactive
 * gaps. If the analyst ever changes the rule, `pageItems` is the single place.
 *
 * Test ids derive from `testId`: `{testId}-prev`, `{testId}-next`,
 * `{testId}-page-{n}`; the size Select carries its own `sizeSelectTestId`
 * (FE-ADR-009).
 */
@Component({
  selector: 'app-pagination',
  imports: [ReactiveFormsModule, IconButton, Select],
  host: {
    class: 'flex shrink-0 items-center gap-4 border-t border-line px-5 py-3',
    '[attr.data-testid]': 'testId()',
  },
  template: `
    <span class="eds-tabular-nums flex-1 text-body-sm text-ink-muted">{{ rangeText() }}</span>

    <div class="flex items-center gap-1">
      <app-icon-button
        icon="chevron-left"
        [ariaLabel]="prevLabel()"
        [disabled]="isFirst()"
        [testId]="testId() + '-prev'"
        (action)="pageChange.emit(page() - 1)"
      />
      @for (item of pageItems(); track $index) {
        @if (item === 'gap') {
          <span class="px-1 text-body-sm text-ink-muted select-none" aria-hidden="true"> … </span>
        } @else {
          <button
            type="button"
            class="eds-tabular-nums h-8 min-w-8 rounded-md border px-1.5 text-body-sm transition-colors duration-100 ease-standard"
            [class.border-brand]="item === currentDisplayPage()"
            [class.bg-selected]="item === currentDisplayPage()"
            [class.text-brand-active]="item === currentDisplayPage()"
            [class.font-semibold]="item === currentDisplayPage()"
            [class.cursor-default]="item === currentDisplayPage()"
            [class.border-line]="item !== currentDisplayPage()"
            [class.bg-surface]="item !== currentDisplayPage()"
            [class.text-ink-soft]="item !== currentDisplayPage()"
            [class.font-medium]="item !== currentDisplayPage()"
            [attr.aria-current]="item === currentDisplayPage() ? 'page' : null"
            [attr.aria-label]="pageLabelFor()(item)"
            [attr.data-testid]="testId() + '-page-' + item"
            (click)="onPageButton(item)"
          >
            {{ item }}
          </button>
        }
      }
      <app-icon-button
        icon="chevron-right"
        [ariaLabel]="nextLabel()"
        [disabled]="isLast()"
        [testId]="testId() + '-next'"
        (action)="pageChange.emit(page() + 1)"
      />
    </div>

    <div class="flex flex-1 items-center justify-end gap-2">
      <span class="text-body-sm whitespace-nowrap text-ink-soft">{{ perPageLabel() }}</span>
      <div class="w-24">
        <app-select
          [formControl]="sizeControl"
          [options]="sizeSelectOptions()"
          [controlId]="sizeSelectTestId()"
          size="sm"
          [dropUp]="true"
          [testId]="sizeSelectTestId()"
        />
      </div>
    </div>
  `,
})
export class Pagination {
  /** Current page, ZERO-based (as the API and the stores count). */
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  /** Server flags, passed through — the pattern never re-derives them. */
  readonly isFirst = input.required<boolean>();
  readonly isLast = input.required<boolean>();
  /** Resolved range text ("1–20 of 137") — parameterized i18n stays with the caller. */
  readonly rangeText = input.required<string>();
  /** Resolved aria-labels for the arrow buttons. */
  readonly prevLabel = input.required<string>();
  readonly nextLabel = input.required<string>();
  /** Resolved, parameterized aria-label per page button ("Page 3") — a function
   *  because shared/ may not translate (FE-ADR-003; scope §4.14). */
  readonly pageLabelFor = input.required<(page: number) => string>();
  /** Resolved "Per page" label. */
  readonly perPageLabel = input.required<string>();
  /** Typed to the server whitelist (`page-size.ts`), NOT to `number`: a screen
   *  that hard-codes an unsupported size no longer compiles, instead of failing
   *  at runtime with a 400. */
  readonly size = input.required<PageSize>();
  readonly sizeOptions = input.required<readonly PageSize[]>();
  readonly testId = input.required<string>();
  readonly sizeSelectTestId = input.required<string>();

  /** Requested page, ZERO-based. Never fires for the already-current page. */
  readonly pageChange = output<number>();
  /** Always a whitelisted size — the control's raw value is narrowed first, so
   *  callers never re-implement the check. */
  readonly sizeChange = output<PageSize>();

  /** Internal control feeding the shared Select (a CVA); kept in sync with the
   *  `size` input and re-emitted as the `sizeChange` intent. */
  protected readonly sizeControl = new FormControl<number | null>(null);

  protected readonly currentDisplayPage = computed(() => this.page() + 1);

  /** Mock §6.2 truncation rule (see class doc). Items are 1-based for display. */
  protected readonly pageItems = computed<readonly (number | 'gap')[]>(() => {
    const total = this.totalPages();
    const current = this.currentDisplayPage();
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

  protected readonly sizeSelectOptions = computed<readonly SelectOption[]>(() =>
    // Numbers are locale-neutral; String() is display, not translation (§4.14).
    this.sizeOptions().map((option) => ({ value: option, label: String(option) })),
  );

  constructor() {
    effect(() => {
      this.sizeControl.setValue(this.size(), { emitEvent: false });
    });
    this.sizeControl.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => {
      // `isPageSize` is the guard, not a formality: the Select is a CVA and its
      // value is typed `number | null`, so this is where an off-whitelist value
      // would otherwise leak out to a caller and become a 400.
      if (typeof value === 'number' && isPageSize(value) && value !== this.size()) {
        this.sizeChange.emit(value);
      }
    });
  }

  protected onPageButton(displayPage: number): void {
    if (displayPage === this.currentDisplayPage()) return; // active page is inert
    this.pageChange.emit(displayPage - 1);
  }
}
