import { Component, input } from '@angular/core';
import { Icon } from '../../ui';

/** One step of the {@link Stepper}. `label` arrives RESOLVED (`'KEY' | t` at
 *  the call site, scope §4.14); `id` is a stable, untranslated slug. */
export interface StepItem {
  readonly id: string;
  readonly label: string;
}

/**
 * Which of the mock's TWO step strips to paint. They are genuinely different
 * designs, not one design with a tweak — verified against the decoded markup
 * (scope §4.33):
 *
 * | | `wizard` (Create customer.dc.html) | `sale` (Offer selection.dc.html) |
 * |---|---|---|
 * | number | `01` / `02` / `03` | `1` / `2` / `3` |
 * | circle | 28px, no border, 13px text | 24px, 1px border, 12px text |
 * | done | filled brand + on-brand check | tinted `bg-selected`, brand border, brand check |
 * | upcoming | filled `bg-sunken` | white with a `border-line-strong` outline |
 * | connector | 2px, turns brand once done | 1px, ALWAYS `border-line-strong` |
 * | done label | primary ink | tertiary, like an upcoming one |
 *
 * Anything shared (semantics, `aria-current`, the passive contract, the
 * connector geometry) stays common — this input selects PAINT only.
 */
export type StepperVariant = 'wizard' | 'sale';

/**
 * Stepper pattern (shared/patterns) — the mock's wizard progress strip
 * (mock-ui-analysis §6.3). §7.2 `EdsStepper` (FE-ADR-011 §d).
 *
 * Purely PASSIVE: it renders progress, it never navigates — moving between
 * steps is the wizard's Next/Previous concern (the mock's steps are not
 * clickable either). Hence no interactive elements; the active step is
 * announced via `aria-current="step"` on a semantic ordered list.
 */
@Component({
  selector: 'app-stepper',
  imports: [Icon],
  template: `
    <ol class="flex w-full items-center gap-3" [attr.data-testid]="testId()">
      @for (step of steps(); track step.id; let i = $index; let last = $last) {
        <!--
          CONNECTOR GEOMETRY — common to both variants (scope §4.32).
          The mock lays step items and connectors out as SIBLINGS of one flex
          row. List semantics forbid a bare span sibling of an li, so the
          connector stays INSIDE its item and the geometry is reproduced one
          level down: "grow" (flex-grow:1 with basis AUTO, i.e. each item's
          basis is its own content) hands every non-last item an EQUAL share of
          the free space, and the connector — the only growing child inside the
          item — absorbs all of it. Equal shares in, equal connectors out.

          This is why "flex-1" (flex: 1 1 0%) was wrong: with a zero basis the
          ITEMS came out equal in width instead, so the connector inside each
          got "equal width minus this label", and labels of different lengths
          produced connectors of different lengths.
        -->
        <li
          class="flex items-center gap-3"
          [class.grow]="!last"
          [attr.aria-current]="i === activeIndex() ? 'step' : null"
          [attr.data-testid]="testId() + '-step-' + step.id"
        >
          <!-- circle + label: shrink-0, 8px apart (the row's own gap is 12px) -->
          <span class="flex shrink-0 items-center gap-2">
            <span [class]="circleClasses(i)">
              @if (stateOf(i) === 'done') {
                <app-icon name="check" [size]="16" />
              } @else {
                {{ numberOf(i) }}
              }
            </span>
            <span [class]="labelClasses(i)">{{ step.label }}</span>
          </span>
          @if (!last) {
            <span [class]="connectorClasses(i)" aria-hidden="true"></span>
          }
        </li>
      }
    </ol>
  `,
})
export class Stepper {
  readonly steps = input.required<readonly StepItem[]>();
  /** ZERO-based index of the active step (caller-owned state). */
  readonly activeIndex = input.required<number>();
  /** Which mock strip to paint — see {@link StepperVariant}. Defaults to the
   *  Create Customer one, which was the pattern's only shape until 2026-08-05. */
  readonly variant = input<StepperVariant>('wizard');
  readonly testId = input.required<string>();

  /** Mock §6.3 visual state table: done / active / upcoming. (Also keeps
   *  `<`/`>` comparisons out of the template, which the conventions checker's
   *  textual scanner would misread as tag boundaries.) */
  protected stateOf(index: number): 'done' | 'active' | 'upcoming' {
    if (index < this.activeIndex()) return 'done';
    return index === this.activeIndex() ? 'active' : 'upcoming';
  }

  /** `wizard` pads to `01`; `sale` prints the bare ordinal. */
  protected numberOf(index: number): string {
    const n = String(index + 1);
    return this.variant() === 'sale' ? n : n.padStart(2, '0');
  }

  protected circleClasses(index: number): string {
    const state = this.stateOf(index);
    if (this.variant() === 'sale') {
      // 24px, outlined, caption-sized, tabular so 1/2/3 never jitter.
      const base =
        'eds-tabular-nums flex size-6 shrink-0 items-center justify-center rounded-full ' +
        'border text-caption font-semibold transition-colors duration-200 ease-standard';
      if (state === 'active') return `${base} border-brand bg-brand text-on-brand`;
      if (state === 'done') return `${base} border-brand bg-selected text-brand-active`;
      return `${base} border-line-strong bg-surface text-ink-muted`;
    }
    const base =
      'flex size-7 shrink-0 items-center justify-center rounded-full text-body-sm font-semibold';
    return state === 'upcoming'
      ? `${base} bg-sunken text-ink-muted`
      : `${base} bg-brand text-on-brand`;
  }

  protected labelClasses(index: number): string {
    const state = this.stateOf(index);
    const base = 'text-body-sm whitespace-nowrap';
    if (state === 'active') return `${base} font-semibold text-ink`;
    // `sale` greys a COMPLETED label too — only the active step is primary.
    if (this.variant() === 'sale') return `${base} font-medium text-ink-muted`;
    return state === 'done' ? `${base} font-medium text-ink` : `${base} font-medium text-ink-muted`;
  }

  protected connectorClasses(index: number): string {
    const base = 'w-7 min-w-6 grow rounded-sm';
    // `sale` keeps a hairline in ONE colour throughout: the mock never repaints
    // it as steps complete (its `connStyle` has no done branch).
    if (this.variant() === 'sale') return `${base} h-px bg-line-strong`;
    return this.stateOf(index) === 'done' ? `${base} h-0.5 bg-brand` : `${base} h-0.5 bg-line`;
  }
}
