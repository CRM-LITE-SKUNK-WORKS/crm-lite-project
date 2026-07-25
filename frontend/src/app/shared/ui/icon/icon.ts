import { Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, type SafeHtml } from '@angular/platform-browser';
import { ICON_PATHS, type IconName, type IconSize } from './icons';

/**
 * EDS `Icon` (shared-ui-design-notes §1, mock-ui-analysis §3.3/§3.9).
 *
 * - Colour is ALWAYS the parent's `currentColor`; there is deliberately no
 *   colour input — variation belongs to the consuming context, never to the
 *   icon (design note §1).
 * - Without `ariaLabel` the icon is decorative: `aria-hidden` and skipped by
 *   the accessibility tree. With `ariaLabel` (a RESOLVED string — the caller
 *   binds `[ariaLabel]="'UI-…' | t"`, scope-and-conflicts §4.14) it becomes
 *   `role="img"`. A meaning-bearing icon must never ship without one.
 * - No `data-testid`: decorative by contract; tests target the enclosing
 *   control (design note §1).
 *
 * The inner markup comes from the typed {@link ICON_PATHS} transcript of the
 * mock's Lucide shim; `bypassSecurityTrustHtml` is safe because the content is
 * a compile-time constant, never user input.
 */
@Component({
  selector: 'app-icon',
  host: { class: 'inline-flex shrink-0 items-center justify-center leading-none' },
  template: `
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.75"
      stroke-linecap="round"
      stroke-linejoin="round"
      [attr.width]="size()"
      [attr.height]="size()"
      [attr.role]="ariaLabel() ? 'img' : null"
      [attr.aria-label]="ariaLabel() || null"
      [attr.aria-hidden]="ariaLabel() ? null : 'true'"
      [attr.focusable]="'false'"
      [innerHTML]="content()"
    ></svg>
  `,
})
export class Icon {
  private readonly sanitizer = inject(DomSanitizer);

  readonly name = input.required<IconName>();
  readonly size = input<IconSize>(20);
  /** Resolved text (`'UI-…' | t`), not a catalogue key (§4.14). */
  readonly ariaLabel = input<string | undefined>(undefined);

  protected readonly content = computed<SafeHtml>(() =>
    this.sanitizer.bypassSecurityTrustHtml(ICON_PATHS[this.name()]),
  );
}
