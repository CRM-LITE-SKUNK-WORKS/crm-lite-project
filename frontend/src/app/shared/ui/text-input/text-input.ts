import { Component, computed, forwardRef, input, output, signal } from '@angular/core';
import { NG_VALUE_ACCESSOR, type ControlValueAccessor } from '@angular/forms';
import { Icon } from '../icon/icon';
import { type IconName } from '../icon/icons';

/**
 * EDS `TextInput` (shared-ui-design-notes §5, mock-ui-analysis §3.6).
 *
 * A `ControlValueAccessor`: screens use it with `formControlName` (FE-ADR-007
 * Reactive Forms — no separate "controlled component" mechanism). The visual
 * state machine is the §3.6 table, verbatim:
 *
 *   default   bg-surface + border-input        hover  border-hover
 *   focus     border-focus + focus ring        error  border-error
 *   disabled  bg-disabled, transparent border  readonly bg-surface-sunken
 *
 * `placeholder` / `clearAriaLabel` are RESOLVED strings (`'KEY' | t`, §4.14).
 * `error` presentation comes from the caller (typically `control.invalid &&
 * control.touched`) — the component renders state, it never decides validity
 * (FE-ADR-007 §3).
 */
@Component({
  selector: 'app-text-input',
  imports: [Icon],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TextInput), multi: true },
  ],
  template: `
    <div [class]="wrapClasses()">
      @if (iconLeading(); as icon) {
        <app-icon [name]="icon" [size]="16" class="text-ink-muted" />
      }
      <input
        class="w-full min-w-0 flex-1 border-none bg-transparent text-body text-ink outline-none placeholder:text-ink-faint disabled:cursor-not-allowed"
        [id]="controlId()"
        [value]="value()"
        [disabled]="isDisabled()"
        [readonly]="readonly()"
        [attr.maxlength]="maxLength() ?? null"
        [attr.placeholder]="placeholder() || null"
        [attr.inputmode]="inputMode() ?? null"
        [attr.aria-invalid]="error() ? 'true' : null"
        [attr.aria-describedby]="describedBy() ?? null"
        [attr.data-testid]="testId()"
        (input)="onInput($event)"
        (focus)="focused.set(true)"
        (blur)="handleBlur()"
      />
      @if (showClear()) {
        <button
          type="button"
          class="inline-flex shrink-0 cursor-pointer border-none bg-transparent p-0 text-ink-muted"
          [attr.aria-label]="clearAriaLabel() || null"
          [attr.data-testid]="testId() + '-clear'"
          (click)="clear()"
        >
          <app-icon name="x" [size]="16" />
        </button>
      }
      @if (showCount() && maxLength(); as max) {
        <span class="eds-tabular-nums shrink-0 font-mono text-caption text-ink-muted">
          {{ value().length }}/{{ max }}
        </span>
      }
      @if (iconTrailing(); as icon) {
        <app-icon [name]="icon" [size]="16" class="text-ink-muted" />
      }
    </div>
  `,
})
export class TextInput implements ControlValueAccessor {
  /** The FormField's `controlId` — keeps the label→input link unbreakable. */
  readonly controlId = input.required<string>();
  readonly placeholder = input<string | undefined>(undefined);
  /** `numeric` for the Nationality-ID / phone fields (digit keypad + intent). */
  readonly inputMode = input<'numeric' | 'tel' | 'email' | 'text' | undefined>(undefined);
  readonly maxLength = input<number | undefined>(undefined);
  readonly showCount = input<boolean>(false);
  readonly clearable = input<boolean>(false);
  /** Resolved label for the clear button — required whenever `clearable` is used. */
  readonly clearAriaLabel = input<string | undefined>(undefined);
  readonly error = input<boolean>(false);
  readonly readonly = input<boolean>(false);
  readonly iconLeading = input<IconName | undefined>(undefined);
  readonly iconTrailing = input<IconName | undefined>(undefined);
  /** Id of the describing element — FormField exposes `{controlId}-error`. */
  readonly describedBy = input<string | undefined>(undefined);
  readonly testId = input.required<string>();

  readonly cleared = output<void>();

  protected readonly value = signal('');
  protected readonly focused = signal(false);
  private readonly cvaDisabled = signal(false);

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  /** Disabled state comes EXCLUSIVELY from the form (`control.disable()` /
   *  `new FormControl({value, disabled: true})`). A separate `disabled` input
   *  would collide with FormControlDirective's own `disabled` input (Angular
   *  warns) and create a second source of truth. */
  protected readonly isDisabled = computed(() => this.cvaDisabled());

  protected readonly showClear = computed(
    () => this.clearable() && this.value().length > 0 && !this.isDisabled() && !this.readonly(),
  );

  /** §3.6 state table — computed so exactly one border/bg state wins. */
  protected readonly wrapClasses = computed(() => {
    const base =
      'flex h-10 items-center gap-2 rounded-md border px-3 ' +
      'transition-[border-color,box-shadow,background-color] duration-150 ease-standard';
    if (this.isDisabled()) {
      return `${base} cursor-not-allowed border-transparent bg-sunken`;
    }
    if (this.readonly()) {
      return `${base} border-transparent bg-sunken`;
    }
    if (this.error()) {
      // Error border wins; the focus ring still shows focus (mock CSS order).
      return `${base} border-danger bg-surface ${this.focused() ? 'shadow-focus-ring' : ''}`;
    }
    if (this.focused()) {
      return `${base} border-brand bg-surface shadow-focus-ring`;
    }
    return `${base} border-line-strong bg-surface hover:border-line-hover`;
  });

  protected onInput(event: Event): void {
    const next = (event.target as HTMLInputElement).value;
    this.value.set(next);
    this.onChange(next);
  }

  protected handleBlur(): void {
    this.focused.set(false);
    this.onTouched();
  }

  protected clear(): void {
    this.value.set('');
    this.onChange('');
    this.cleared.emit();
  }

  // ControlValueAccessor ------------------------------------------------------
  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }
  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.cvaDisabled.set(isDisabled);
  }
}
