import {
  Component,
  Directive,
  ElementRef,
  computed,
  contentChild,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';
import { IconButton } from '../../ui';

export type ModalSize = 'sm' | 'md' | 'lg';

/** §7.2 max-width variants (420 / 560 / 680) — theme tokens, not literals. */
const SIZE_CLASSES: Record<ModalSize, string> = {
  sm: 'max-w-modal-sm',
  md: 'max-w-modal-md',
  lg: 'max-w-modal-lg',
};

/** Everything the trap counts as focusable inside the dialog panel. */
const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), ' +
  'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/** Marks the caller's footer content: `<div appModalFooter>…</div>`. The footer
 *  bar (top border, right-aligned actions) renders only when one is projected. */
@Directive({ selector: '[appModalFooter]' })
export class ModalFooter {}

/**
 * Modal pattern (shared/patterns) — overlay + centered dialog panel
 * (mock-ui-analysis §7.2: max-width 420/560/680, `elevation-3`, `radius-lg`,
 * `bg-overlay` backdrop; z-layers per §2.15: overlay 1300 < modal 1400).
 * §7.2 hand-built pattern (FE-ADR-011 §d), built ahead of its consumers
 * (delete confirmation, Account, Create address dialog).
 *
 * Purely presentational: the CALLER owns visibility (`open` input) and reacts
 * to the single `closed` output (✕ button, Escape, optional backdrop click) —
 * the pattern never closes itself, so state has exactly one owner
 * (FE-ADR-006 §2 spirit).
 *
 * A11y — ours to own (FE-ADR-011 §g):
 * - `role="dialog"` + `aria-modal="true"` + `aria-labelledby` → the title
 *   names the dialog and assistive tech treats the background as inert.
 *   (A literal `inert` attribute on the rest of the page would require
 *   reaching outside this component's DOM — off-limits for a shared pattern;
 *   aria-modal plus the focus trap below is the standards-based equivalent.)
 * - FOCUS TRAP: on open, focus moves to the panel; Tab/Shift+Tab cycle within
 *   it; on close, focus RETURNS to the element that had it before opening.
 * - Escape always emits `closed` (FE-ADR-011 §g: "modals close on Escape").
 * - Backdrop click closes only when `closeOnBackdrop` is set — destructive
 *   confirmations should not vanish on a stray click (default false).
 */
@Component({
  selector: 'app-modal',
  imports: [IconButton],
  template: `
    @if (open()) {
      <div
        class="fixed inset-0 z-overlay bg-overlay"
        aria-hidden="true"
        [attr.data-testid]="testId() + '-backdrop'"
        (click)="onBackdrop()"
      ></div>
      <div class="fixed inset-0 z-modal flex items-center justify-center p-6">
        <div
          #panel
          role="dialog"
          aria-modal="true"
          tabindex="-1"
          [attr.aria-labelledby]="headerless() ? null : titleId()"
          [attr.aria-label]="headerless() ? titleText() : null"
          [class]="panelClasses()"
          [attr.data-testid]="testId()"
          (keydown)="onKeydown($event)"
        >
          @if (!headerless()) {
            <div class="flex h-16 shrink-0 items-center justify-between border-b border-line px-5">
              <h2 [id]="titleId()" class="text-h3 font-semibold text-ink">{{ titleText() }}</h2>
              <app-icon-button
                icon="x"
                [ariaLabel]="closeLabel() ?? ''"
                [testId]="testId() + '-close-button'"
                (action)="closed.emit()"
              />
            </div>
          }
          <div class="min-h-0 flex-1 overflow-y-auto p-5">
            <ng-content />
          </div>
          @if (footer()) {
            <div class="flex shrink-0 items-center justify-end gap-3 border-t border-line px-5 py-4">
              <ng-content select="[appModalFooter]" />
            </div>
          }
        </div>
      </div>
    }
  `,
})
export class Modal {
  /** Visibility is the caller's state; the pattern only renders it. */
  readonly open = input.required<boolean>();
  readonly size = input<ModalSize>('md');
  /** Resolved title (`'KEY' | t`) — also the dialog's accessible name (via
   *  `aria-labelledby` normally, `aria-label` when `headerless`). */
  readonly titleText = input.required<string>();
  /** Resolved aria-label for the ✕ button. REQUIRED unless `headerless` —
   *  a headerless dialog renders no ✕, so no label is needed. */
  readonly closeLabel = input<string | undefined>(undefined);
  /** Skip the header bar (title + ✕) — for §7.2 ConfirmDialog-style dialogs
   *  whose title sits inside the body. The title still names the dialog via
   *  `aria-label`; Escape/backdrop behaviour is unchanged. */
  readonly headerless = input<boolean>(false);
  /** Close on backdrop click; deliberately off by default (see class doc). */
  readonly closeOnBackdrop = input<boolean>(false);
  readonly testId = input.required<string>();

  /** The single close intent (✕ / Escape / permitted backdrop click). */
  readonly closed = output<void>();

  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  protected readonly footer = contentChild(ModalFooter);

  /** Where focus was before the dialog opened — restored on close (§g). */
  private previousFocus: HTMLElement | null = null;

  protected readonly titleId = computed(() => `${this.testId()}-title`);
  protected readonly panelClasses = computed(
    () =>
      'animate-panel-in flex max-h-full w-full flex-col rounded-lg bg-surface shadow-e3 ' +
      SIZE_CLASSES[this.size()],
  );

  constructor() {
    // Focus management keyed on the panel's presence in the DOM: capture and
    // enter on open, restore on close (also covers destroy-while-open).
    effect(() => {
      const panel = this.panel()?.nativeElement;
      if (panel) {
        this.previousFocus = document.activeElement as HTMLElement | null;
        panel.focus();
      } else if (this.previousFocus) {
        this.previousFocus.focus();
        this.previousFocus = null;
      }
    });
  }

  protected onBackdrop(): void {
    if (this.closeOnBackdrop()) {
      this.closed.emit();
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closed.emit();
      return;
    }
    if (event.key !== 'Tab') return;
    const panel = this.panel()?.nativeElement;
    if (!panel) return;
    const focusables = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
    if (focusables.length === 0) {
      event.preventDefault(); // nothing tabbable: focus stays on the panel
      panel.focus();
      return;
    }
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && (active === first || active === panel)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }
}
