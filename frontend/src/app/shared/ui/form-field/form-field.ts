import { Component, computed, input } from '@angular/core';
import { Icon } from '../icon/icon';

/**
 * EDS `FormField` (shared-ui-design-notes §4, mock-ui-analysis §3.8) — the
 * layout contract around a control. It wraps the control, it never knows the
 * control: any of TextInput / Select / DatePicker is projected in.
 *
 * 🔑 The error row is ALWAYS rendered with `min-height` = caption line (16px),
 * so the field's height never jumps when an error appears or clears — the
 * mock's documented behaviour, preserved verbatim.
 *
 * All texts (`label`, `optionalText`, `helperText`, `errorText`) are RESOLVED
 * strings — the caller binds `'KEY' | t` (§4.14). For backend field errors the
 * caller resolves `ApiFieldError.messageKey` through the catalogue first; raw
 * backend text never reaches this input (FE-ADR-008 §2).
 *
 * A11y wiring: `<label for>` binds via the mandatory `controlId`; the error
 * row's id is `{controlId}-error`, exactly what the projected control should
 * reference via `aria-describedby` (TextInput/DatePicker do this through
 * their `describedBy` input). The row is `aria-live="polite"` — deliberately
 * NOT `role="alert"`, so five errors appearing on submit do not drown a
 * screen reader (design note §4).
 *
 * The `hint` tooltip of the mock (§3.8) is deferred until the Tooltip pattern
 * exists in `shared/patterns/` — no in-scope screen uses it.
 */
@Component({
  selector: 'app-form-field',
  imports: [Icon],
  template: `
    <div class="grid gap-2" [attr.data-testid]="testId()">
      <label [for]="controlId()" class="flex items-center gap-1 text-body font-medium text-ink">
        <span>{{ label() }}</span>
        @if (required()) {
          <!-- Star is visual; requiredness reaches AT via the control itself. -->
          <span aria-hidden="true" class="text-danger">*</span>
        }
        @if (optionalText(); as optional) {
          <span class="font-regular text-ink-muted">{{ optional }}</span>
        }
      </label>
      <ng-content />
      <div
        class="flex min-h-4 items-center gap-1 text-caption transition-colors duration-150 ease-standard"
        [class]="rowColor()"
        aria-live="polite"
        [id]="controlId() + '-error'"
        [attr.data-testid]="testId() + '-error'"
      >
        @if (errorText()) {
          <app-icon name="alert-circle" [size]="16" class="text-danger" />
        }
        <span>{{ errorText() || helperText() || '' }}</span>
      </div>
    </div>
  `,
})
export class FormField {
  /** Resolved label text (`'LBL-…' | t`), never a raw key (§4.14). */
  readonly label = input.required<string>();
  /** Mandatory so the label→control link can never silently break (design note §4). */
  readonly controlId = input.required<string>();
  readonly required = input<boolean>(false);
  /** Resolved "(optional)" marker text; rendered only when provided. */
  readonly optionalText = input<string | undefined>(undefined);
  /** Resolved helper text — shown when there is no error. */
  readonly helperText = input<string | undefined>(undefined);
  /** Resolved error text; when set it wins over `helperText` (§3.8). */
  readonly errorText = input<string | undefined>(undefined);
  readonly testId = input.required<string>();

  /** §3.8: error text `--eds-color-text-danger`, helper `text-tertiary`. */
  protected readonly rowColor = computed(() =>
    this.errorText() ? 'text-danger-fg' : 'text-ink-muted',
  );
}
