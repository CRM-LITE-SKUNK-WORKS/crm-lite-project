import { Component, computed, input, output } from '@angular/core';
import { Icon } from '../icon/icon';
import { type IconName } from '../icon/icons';

export type ButtonVariant = 'primary' | 'secondary' | 'danger';
export type ButtonSize = 'md' | 'sm';

/** Mock-verbatim state styling (mock-ui-analysis §3.4, Button.jsx of the EDS
 *  bundle). `enabled:` scopes hover/active away from the disabled state, which
 *  the mock achieves with `:not(:disabled)` + `!important`. */
const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-brand text-on-brand enabled:hover:bg-brand-hover enabled:active:bg-brand-active',
  secondary:
    'border-line-strong bg-surface text-secondary-action enabled:hover:border-line-hover ' +
    'enabled:hover:bg-page enabled:active:border-brand enabled:active:bg-selected',
  danger: 'bg-danger text-white enabled:hover:bg-danger-hover enabled:active:bg-danger-hover',
};

/** §3.4 size table: height / padding-x / font-size (lg exists in the DS but no
 *  screen uses it — deliberately not written, design note §2). */
const SIZE_CLASSES: Record<ButtonSize, string> = {
  md: 'h-10 px-4 text-body',
  sm: 'h-8 px-3 text-body-sm',
};

/**
 * EDS `Button` (shared-ui-design-notes §2). `ghost` and `lg` are deliberately
 * absent (no in-scope consumer).
 *
 * Text arrives through `<ng-content>` — the caller writes `{{ 'KEY' | t }}`;
 * the component never owns copy (FE-ADR-012 §b, §4.14).
 *
 * `loading` (design note §2, a deliberate a11y refinement over the mock's
 * plain `disabled`): the button stays FOCUSABLE, announces `aria-busy` +
 * `aria-disabled`, shows the EDS spinner, and swallows activation — so a
 * screen reader user keeps focus context instead of losing it mid-submit.
 */
@Component({
  selector: 'app-button',
  imports: [Icon],
  template: `
    <button
      [type]="type()"
      [class]="classes()"
      [disabled]="disabled()"
      [attr.aria-busy]="loading() ? 'true' : null"
      [attr.aria-disabled]="inertButNotDisabled() ? 'true' : null"
      [attr.data-variant]="variant()"
      [attr.data-loading]="loading() ? 'true' : null"
      [attr.data-testid]="testId()"
      (click)="guardClick($event)"
    >
      @if (loading()) {
        <!-- EDS Spinner (Spinner.jsx): 16px in buttons; keeps spinning under
             prefers-reduced-motion via data-eds-motion-exempt (§2.14). -->
        <svg
          class="animate-eds-spin shrink-0"
          data-eds-motion-exempt="true"
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
          focusable="false"
        >
          <circle cx="12" cy="12" r="9.5" stroke="currentColor" stroke-width="2" opacity="0.2" />
          <path
            d="M12 2.5a9.5 9.5 0 0 1 9.5 9.5"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
          />
        </svg>
      } @else {
        @if (iconLeading(); as icon) {
          <app-icon [name]="icon" [size]="iconSize()" />
        }
      }
      <span class="leading-none"><ng-content /></span>
      @if (!loading()) {
        @if (iconTrailing(); as icon) {
          <app-icon [name]="icon" [size]="iconSize()" />
        }
      }
    </button>
  `,
})
export class Button {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly iconLeading = input<IconName | undefined>(undefined);
  readonly iconTrailing = input<IconName | undefined>(undefined);
  readonly fullWidth = input<boolean>(false);
  readonly loading = input<boolean>(false);
  readonly disabled = input<boolean>(false);
  /** Always explicit so a form never gets a surprise implicit submit (design note §2). */
  readonly type = input<'button' | 'submit'>('button');
  readonly testId = input.required<string>();

  /** Guarded activation: fires on click UNLESS disabled (native) or `loading`
   *  (guard). Prefer this over listening for `(click)` on the host — the host
   *  element cannot carry the `data-testid` the conventions check requires, and
   *  a host click would also fire during `loading`. */
  readonly action = output<void>();

  protected readonly iconSize = computed(() => (this.size() === 'sm' ? 16 : 20) as 16 | 20);
  protected readonly inertButNotDisabled = computed(() => this.loading() && !this.disabled());

  protected readonly classes = computed(() =>
    [
      // Base (§3.4): inline-flex, gap space-2, weight medium, radius-md,
      // transparent 1px border, nowrap, 100ms standard color transition.
      'inline-flex items-center justify-center gap-2 rounded-md border border-transparent',
      'font-medium whitespace-nowrap transition-colors duration-100 ease-standard',
      // Buttons use the offset focus ring, not the global outline (§2.6).
      'focus-visible:outline-none focus-visible:shadow-focus-ring-offset',
      'disabled:cursor-not-allowed disabled:border-transparent disabled:bg-sunken disabled:text-ink-faint',
      SIZE_CLASSES[this.size()],
      VARIANT_CLASSES[this.variant()],
      this.fullWidth() ? 'w-full' : '',
    ]
      .filter(Boolean)
      .join(' '),
  );

  /** While loading the button is present and focusable but never activates. */
  protected guardClick(event: Event): void {
    if (this.loading()) {
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }
    this.action.emit();
  }
}
