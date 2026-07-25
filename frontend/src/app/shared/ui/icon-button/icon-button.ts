import { Component, computed, input, output } from '@angular/core';
import { Icon } from '../icon/icon';
import { type IconName } from '../icon/icons';

export type IconButtonVariant = 'ghost' | 'danger-ghost';

/** Mock-verbatim variants (IconButton.jsx; §3.5). `subtle` and size 40 have no
 *  in-scope consumer and are deliberately not written (design note §3). */
const VARIANT_CLASSES: Record<IconButtonVariant, string> = {
  ghost: 'bg-sunken enabled:hover:bg-line enabled:hover:text-ink enabled:active:bg-line-hover',
  'danger-ghost':
    'bg-danger-surface text-danger enabled:hover:bg-danger-border enabled:hover:text-danger-hover',
};

/**
 * EDS `IconButton` (shared-ui-design-notes §3) — the a11y-critical control:
 * it has no visible text, so `ariaLabel` is REQUIRED at compile time. The
 * caller binds a resolved string (`[ariaLabel]="'UI-…' | t"`, §4.14); the
 * mock's established labels (`Edit customer info`, `Delete address`, …) are
 * carried through the catalogue.
 *
 * Fixed 32×32 box, radius-md, base colour text-secondary (§3.5).
 */
@Component({
  selector: 'app-icon-button',
  imports: [Icon],
  template: `
    <button
      type="button"
      [class]="classes()"
      [disabled]="disabled()"
      [attr.aria-label]="ariaLabel()"
      [attr.aria-busy]="loading() ? 'true' : null"
      [attr.aria-disabled]="inertButNotDisabled() ? 'true' : null"
      [attr.data-variant]="variant()"
      [attr.data-testid]="testId()"
      (click)="guardClick($event)"
    >
      @if (loading()) {
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
        <app-icon [name]="icon()" [size]="20" />
      }
    </button>
  `,
})
export class IconButton {
  readonly icon = input.required<IconName>();
  /** REQUIRED — an icon-only control without an accessible name is a defect
   *  (FE-ADR-011 §g). Resolved string, never a raw key (§4.14). */
  readonly ariaLabel = input.required<string>();
  readonly variant = input<IconButtonVariant>('ghost');
  readonly disabled = input<boolean>(false);
  readonly loading = input<boolean>(false);
  readonly testId = input.required<string>();

  /** Guarded activation — see Button.action for the rationale. */
  readonly action = output<void>();

  protected readonly inertButNotDisabled = computed(() => this.loading() && !this.disabled());

  protected readonly classes = computed(() =>
    [
      'inline-flex h-8 w-8 items-center justify-center rounded-md border border-transparent',
      'text-ink-soft transition-colors duration-100 ease-standard',
      'focus-visible:outline-none focus-visible:shadow-focus-ring-offset',
      'disabled:cursor-not-allowed disabled:bg-sunken disabled:text-ink-faint',
      VARIANT_CLASSES[this.variant()],
    ].join(' '),
  );

  protected guardClick(event: Event): void {
    if (this.loading()) {
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }
    this.action.emit();
  }
}
