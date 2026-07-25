import { Component, input, output } from '@angular/core';
import { Button, Icon, type IconName } from '../../ui';
import { Modal, ModalFooter } from '../modal/modal';

/**
 * ConfirmDialog pattern (shared/patterns) — the mock's 420px confirmation
 * dialog (mock-ui-analysis §6.3 address delete, §6.4 customer delete; §7.2
 * `EdsConfirmDialog`): danger circle + title + message, `[No] [Yes(danger)]`.
 *
 * TWO-STATE, exactly as the mock documents (§6.4 delete modal):
 * 1. **Question** — icon circle, title, message, No/Yes.
 * 2. **Error** (`errorText` set by the caller after a backend rejection, e.g.
 *    409 `MSG-CUST-HAS-PRODUCTS` / `MSG-ADDR-PRIMARY-DELETE`): a danger
 *    feedback box replaces the message and a single `[Close]` (secondary)
 *    remains. The pattern never derives this state itself — the CALLER
 *    translates the backend `messageKey` and passes it in (FE-ADR-008 §2;
 *    guards are backend-enforced, never pre-computed — FE-ADR-013 §a).
 *
 * Composes {@link Modal} `headerless` (mock's confirm has no header bar), so
 * focus trap / Escape / restore and the backdrop rules are inherited, not
 * duplicated. All texts arrive RESOLVED (scope §4.14). `busy` renders the
 * confirm button in its loading state while the caller's request runs.
 *
 * Outputs: `confirmed` (Yes) and `cancelled` (No / Close / Escape). The caller
 * owns visibility — nothing here closes the dialog.
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [Button, Icon, Modal, ModalFooter],
  template: `
    <app-modal
      [open]="open()"
      size="sm"
      [headerless]="true"
      [titleText]="title()"
      [testId]="testId()"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-4">
        <span class="flex size-10 items-center justify-center rounded-full bg-danger-surface">
          <app-icon [name]="icon()" [size]="20" class="text-danger" />
        </span>
        <span class="text-body-lg font-semibold text-ink">{{ title() }}</span>
        @if (errorText(); as error) {
          <!-- State 2: backend rejected the action — its translated messageKey. -->
          <div
            class="flex items-start gap-2 rounded-md border border-danger-border bg-danger-surface p-3"
            role="alert"
            [attr.data-testid]="testId() + '-error'"
          >
            <app-icon name="alert-circle" [size]="20" class="shrink-0 text-danger" />
            <span class="text-body text-danger-fg">{{ error }}</span>
          </div>
        } @else {
          <span class="text-body text-ink-soft">{{ message() }}</span>
        }
      </div>
      <div appModalFooter class="flex gap-3">
        @if (errorText()) {
          <app-button
            variant="secondary"
            [testId]="testId() + '-close-button'"
            (action)="cancelled.emit()"
          >
            {{ closeLabel() }}
          </app-button>
        } @else {
          <app-button
            variant="secondary"
            [testId]="testId() + '-cancel-button'"
            (action)="cancelled.emit()"
          >
            {{ cancelLabel() }}
          </app-button>
          <app-button
            variant="danger"
            [loading]="busy()"
            [testId]="testId() + '-confirm-button'"
            (action)="confirmed.emit()"
          >
            {{ confirmLabel() }}
          </app-button>
        }
      </div>
    </app-modal>
  `,
})
export class ConfirmDialog {
  /** Visibility is the caller's state (same contract as Modal). */
  readonly open = input.required<boolean>();
  readonly icon = input<IconName>('trash-2');
  /** Resolved texts (`'KEY' | t` / translated messageKey — scope §4.14). */
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input.required<string>();
  readonly cancelLabel = input.required<string>();
  /** Label of the single button in the error state (mock: "Close"). */
  readonly closeLabel = input.required<string>();
  /** Translated backend messageKey after a rejection — switches to state 2. */
  readonly errorText = input<string | null>(null);
  /** Renders the confirm button loading while the caller's request runs. */
  readonly busy = input<boolean>(false);
  readonly testId = input.required<string>();

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
}
