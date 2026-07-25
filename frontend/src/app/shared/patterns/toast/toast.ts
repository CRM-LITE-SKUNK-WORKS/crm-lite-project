import { Component, computed, effect, input, output } from '@angular/core';
import { Icon, IconButton, type IconName } from '../../ui';

export type ToastKind = 'success' | 'error' | 'info';

/** Kind → EDS feedback icon + icon colour (mock §4.3 shows the success case;
 *  error/info reuse the feedback families of §2.8). */
const KIND_ICON: Record<ToastKind, { icon: IconName; iconClass: string }> = {
  success: { icon: 'check-circle-2', iconClass: 'text-success' },
  error: { icon: 'alert-circle', iconClass: 'text-danger' },
  info: { icon: 'info', iconClass: 'text-info' },
};

/**
 * Toast pattern (shared/patterns) — the mock's notification card
 * (mock-ui-analysis §4.3): fixed below the header on the right, elevation-4,
 * icon + message + dismiss, auto-dismiss after 6 s. §7.2 hand-built pattern
 * (FE-ADR-011 §d). Built ahead of its consumers (Create / Info / Account
 * screens); Customer Search deliberately keeps its in-context error BAND for
 * load failures (recorded in scope §4.20 item 1).
 *
 * Purely presentational: the caller owns WHEN a toast exists (`@if`) and WHAT
 * it says (resolved text, `'KEY' | t` / translated `messageKey` — raw backend
 * `message` never reaches here, FE-ADR-008 §2). The component only renders and
 * reports dismissal — via the ✕ button or the §4.3 auto-dismiss timer — through
 * the single `dismissed` output; the caller removes it from the DOM.
 *
 * A11y (FE-ADR-011 §g): the host is `role="status"` — an implicit polite ARIA
 * live region, so the message is announced without stealing focus. Position
 * uses tokens only: 64px header (`top-16`) + 12px (`mt-3`) = the mock's 76px;
 * `z-toast` (1500) keeps it above modals (§2.15 decision).
 *
 * Deviation note: the mock's dismiss button is 24×24; the shared IconButton
 * standard is 32×32 and is reused instead of copied — the same rounding
 * precedent as the pagination arrows (scope §4.20 item 6).
 */
@Component({
  selector: 'app-toast',
  imports: [Icon, IconButton],
  host: {
    class:
      'animate-toast-in fixed top-16 right-6 mt-3 z-toast flex max-w-100 items-center gap-3 ' +
      'rounded-lg border border-line bg-surface px-4 py-3 shadow-e4',
    role: 'status',
    '[attr.data-testid]': 'testId()',
  },
  template: `
    <app-icon [name]="kindIcon().icon" [size]="20" [class]="kindIcon().iconClass" />
    <span class="min-w-0 flex-1 text-body text-ink">{{ message() }}</span>
    <app-icon-button
      icon="x"
      [ariaLabel]="dismissLabel()"
      [testId]="testId() + '-dismiss-button'"
      (action)="dismissed.emit()"
    />
  `,
})
export class Toast {
  readonly kind = input<ToastKind>('success');
  /** Resolved message text (`'KEY' | t` / translated messageKey), never raw
   *  backend text and never a catalogue key (FE-ADR-008 §2, scope §4.14). */
  readonly message = input.required<string>();
  /** Resolved aria-label for the dismiss button (mock: "Dismiss notification"). */
  readonly dismissLabel = input.required<string>();
  /** Auto-dismiss delay; §4.3 documents 6 s. `0` disables the timer. */
  readonly autoDismissMs = input<number>(6000);
  readonly testId = input.required<string>();

  /** Fired on ✕ or when the auto-dismiss timer elapses; the CALLER removes the
   *  toast (the pattern holds no visibility state of its own). */
  readonly dismissed = output<void>();

  protected readonly kindIcon = computed(() => KIND_ICON[this.kind()]);

  constructor() {
    // Auto-dismiss (§4.3). Restarts when the message changes — a replaced text
    // deserves its full reading time.
    effect((onCleanup) => {
      this.message();
      const delay = this.autoDismissMs();
      if (delay <= 0) return;
      const timer = setTimeout(() => this.dismissed.emit(), delay);
      onCleanup(() => clearTimeout(timer));
    });
  }
}
