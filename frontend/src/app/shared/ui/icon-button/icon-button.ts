import { Component, computed, input, output } from '@angular/core';
import { Icon } from '../icon/icon';
import { type IconName } from '../icon/icons';

export type IconButtonVariant = 'ghost' | 'danger-ghost' | 'bare';

/** 32px is the DS box (§3.5); 24px is the compact one the mock uses for the
 *  in-list remove control of the sale basket (scope §4.33). */
export type IconButtonSize = 32 | 24;

/** Mock-verbatim variants (IconButton.jsx; §3.5), plus `bare`. `subtle` and
 *  size 40 have no in-scope consumer and are deliberately not written
 *  (design note §3). */
const VARIANT_CLASSES: Record<IconButtonVariant, string> = {
  ghost: 'bg-sunken enabled:hover:bg-line enabled:hover:text-ink enabled:active:bg-line-hover',
  'danger-ghost':
    'bg-danger-surface text-danger enabled:hover:bg-danger-border enabled:hover:text-danger-hover',
  // Added 2026-08-05 (scope §4.33): NO resting fill at all — the control only
  // appears on hover. The mock's basket rows draw their remove button this way
  // (`background:transparent`, sunken + primary text on hover) so a list of
  // them reads as content rather than as a column of grey chips.
  bare: 'bg-transparent enabled:hover:bg-sunken enabled:hover:text-ink',
};

const SIZE_CLASSES: Record<IconButtonSize, string> = {
  32: 'h-8 w-8 rounded-md',
  24: 'h-6 w-6 rounded-sm',
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
      [attr.title]="title() || null"
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
        <app-icon [name]="icon()" [size]="iconSize()" />
      }
    </button>
  `,
})
export class IconButton {
  readonly icon = input.required<IconName>();
  /** REQUIRED — an icon-only control without an accessible name is a defect
   *  (FE-ADR-011 §g). Resolved string, never a raw key (§4.14). */
  readonly ariaLabel = input.required<string>();
  /**
   * OPTIONAL native tooltip, RESOLVED (`[title]="'UI-…' | t"`, §4.14). The
   * accessible name already comes from {@link ariaLabel}; this only adds the
   * hover hint sighted users get, which several of the mock's icon controls
   * carry. Pass the SAME text as `ariaLabel` unless the mock says otherwise —
   * a tooltip that disagrees with the accessible name is a defect.
   */
  readonly title = input<string | undefined>(undefined);
  readonly variant = input<IconButtonVariant>('ghost');
  /** 32 (the DS default) or the compact 24 — see {@link IconButtonSize}. */
  readonly size = input<IconButtonSize>(32);
  readonly disabled = input<boolean>(false);
  readonly loading = input<boolean>(false);
  readonly testId = input.required<string>();

  /** Guarded activation — see Button.action for the rationale. */
  readonly action = output<void>();

  protected readonly inertButNotDisabled = computed(() => this.loading() && !this.disabled());

  /** The glyph tracks the box: 20px in the 32 box, 16px in the 24 one. */
  protected readonly iconSize = computed(() => (this.size() === 24 ? 16 : 20) as 16 | 20);

  protected readonly classes = computed(() =>
    [
      'inline-flex items-center justify-center border border-transparent',
      'text-ink-soft transition-colors duration-100 ease-standard',
      'focus-visible:outline-none focus-visible:shadow-focus-ring-offset',
      'disabled:cursor-not-allowed disabled:bg-sunken disabled:text-ink-faint',
      SIZE_CLASSES[this.size()],
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
