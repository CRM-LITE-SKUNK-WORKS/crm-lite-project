import { Component, computed, input } from '@angular/core';

/** The mock skeleton's line-width rhythm, repeated cyclically (extracted from
 *  the Customer Search inline markup it replaces). */
const LINE_WIDTHS = ['w-full', 'w-full', 'w-3/4', 'w-full', 'w-2/3', 'w-full'] as const;

/**
 * Skeleton pattern (shared/patterns) — the loading placeholder for a content
 * area (first consumer: Customer Search state 1). §7.2 hand-built pattern
 * (FE-ADR-011 §d).
 *
 * Deliberately WITHOUT the EDS shimmer animation: `--eds-duration-shimmer`
 * (1800ms) was never moved into the theme — static `bg-sunken` blocks instead
 * (recorded decision, scope §4.20 item 2; add via the §4.16 process if ever
 * wanted).
 *
 * A11y: `role="status"` + `aria-busy` + a resolved `label` announce the
 * loading state; the bars themselves are decorative.
 */
@Component({
  selector: 'app-skeleton',
  host: {
    class: 'flex flex-1 flex-col gap-3 p-5',
    role: 'status',
    'aria-busy': 'true',
    '[attr.aria-label]': 'label()',
    '[attr.data-testid]': 'testId()',
  },
  template: `
    @for (width of lineWidths(); track $index) {
      <div class="h-4 rounded-sm bg-sunken" [class]="width"></div>
    }
  `,
})
export class Skeleton {
  /** Resolved accessible label (`'KEY' | t`), e.g. "Loading customers…". */
  readonly label = input.required<string>();
  /** Number of placeholder lines (default matches the mock's table skeleton). */
  readonly lines = input<number>(6);
  readonly testId = input.required<string>();

  protected readonly lineWidths = computed(() =>
    Array.from({ length: this.lines() }, (_, i) => LINE_WIDTHS[i % LINE_WIDTHS.length]),
  );
}
