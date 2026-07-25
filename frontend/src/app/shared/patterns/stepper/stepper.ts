import { Component, input } from '@angular/core';
import { Icon } from '../../ui';

/** One step of the {@link Stepper}. `label` arrives RESOLVED (`'KEY' | t` at
 *  the call site, scope §4.14); `id` is a stable, untranslated slug. */
export interface StepItem {
  readonly id: string;
  readonly label: string;
}

/**
 * Stepper pattern (shared/patterns) — the mock's wizard progress strip
 * (mock-ui-analysis §6.3: 28px circles, `01/02/03` numbers via padStart,
 * done = brand circle with a check, active = brand circle with the number,
 * upcoming = sunken circle; 2px connectors that turn brand once a step is
 * done, hidden after the last step). §7.2 `EdsStepper` (FE-ADR-011 §d).
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
    <ol class="flex items-center gap-3" [attr.data-testid]="testId()">
      @for (step of steps(); track step.id; let i = $index; let last = $last) {
        <li
          class="flex items-center gap-3"
          [class.flex-1]="!last"
          [attr.aria-current]="i === activeIndex() ? 'step' : null"
          [attr.data-testid]="testId() + '-step-' + step.id"
        >
          <span
            class="flex size-7 shrink-0 items-center justify-center rounded-full text-body-sm"
            [class.bg-brand]="stateOf(i) !== 'upcoming'"
            [class.text-on-brand]="stateOf(i) !== 'upcoming'"
            [class.bg-sunken]="stateOf(i) === 'upcoming'"
            [class.text-ink-muted]="stateOf(i) === 'upcoming'"
            [class.font-semibold]="stateOf(i) === 'active'"
            [class.font-medium]="stateOf(i) !== 'active'"
          >
            @if (stateOf(i) === 'done') {
              <app-icon name="check" [size]="16" />
            } @else {
              {{ numberOf(i) }}
            }
          </span>
          <span
            class="text-body-sm whitespace-nowrap"
            [class.font-semibold]="stateOf(i) === 'active'"
            [class.font-medium]="stateOf(i) !== 'active'"
            [class.text-ink]="stateOf(i) !== 'upcoming'"
            [class.text-ink-muted]="stateOf(i) === 'upcoming'"
          >
            {{ step.label }}
          </span>
          @if (!last) {
            <span
              class="h-0.5 min-w-6 flex-1 rounded-full"
              [class.bg-brand]="stateOf(i) === 'done'"
              [class.bg-line]="stateOf(i) !== 'done'"
              aria-hidden="true"
            ></span>
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
  readonly testId = input.required<string>();

  /** Mock §6.3 visual state table: done / active / upcoming. (Also keeps
   *  `<`/`>` comparisons out of the template, which the conventions checker's
   *  textual scanner would misread as tag boundaries.) */
  protected stateOf(index: number): 'done' | 'active' | 'upcoming' {
    if (index < this.activeIndex()) return 'done';
    return index === this.activeIndex() ? 'active' : 'upcoming';
  }

  /** Mock §6.3: numbers render as `01`, `02`, `03` (padStart(2,'0')). */
  protected numberOf(index: number): string {
    return String(index + 1).padStart(2, '0');
  }
}
