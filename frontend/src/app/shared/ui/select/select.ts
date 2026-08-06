import { Component, computed, forwardRef, input, signal } from '@angular/core';
import { NG_VALUE_ACCESSOR, type ControlValueAccessor } from '@angular/forms';
import { Icon } from '../icon/icon';

/** Option values are business identifiers (gender string, cityId number…). */
export type SelectValue = string | number;

export interface SelectOption {
  readonly value: SelectValue;
  /** RESOLVED display text: fixed options are translated by the caller
   *  (`i18n` at the call site); backend reference data (cities/districts)
   *  arrives already resolved and is never translated (§4.14). */
  readonly label: string;
}

/**
 * EDS `Select` (shared-ui-design-notes §6, mock-ui-analysis §3.7) — a custom
 * panel, not a native `<select>`: the mock's look (panel animation, option
 * states) cannot be styled onto the native control.
 *
 * A11y (FE-ADR-011 §g — ours to own, no library):
 * - trigger: `role="combobox"`, `aria-expanded`, `aria-controls`,
 *   `aria-activedescendant` (focus STAYS on the trigger; the active option is
 *   announced, the listbox itself is never focused),
 * - panel: `role="listbox"`, options `role="option"` + `aria-selected`,
 * - keyboard: ↑/↓ navigate, Enter/Space select, Esc close, Home/End jump,
 *   printable character jumps to the next matching option.
 *
 * `searchable`/`clearable`/`loading` of the DS signature have no in-scope
 * consumer yet and are deliberately not written (the FE-ADR-011 §c budget
 * rule; added when a screen needs them).
 */
@Component({
  selector: 'app-select',
  imports: [Icon],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => Select), multi: true }],
  host: { class: 'relative block' },
  template: `
    <button
      type="button"
      role="combobox"
      [id]="controlId()"
      [class]="triggerClasses()"
      [disabled]="isDisabled()"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="listboxId()"
      [attr.aria-activedescendant]="activeDescendant()"
      [attr.aria-invalid]="error() ? 'true' : null"
      [attr.data-testid]="testId()"
      (click)="toggle()"
      (keydown)="onKeydown($event)"
      (blur)="close()"
    >
      <span class="truncate" [class.text-ink-faint]="!selectedOption()">
        {{ selectedOption()?.label ?? placeholder() ?? '' }}
      </span>
      <app-icon
        name="chevron-down"
        [size]="16"
        class="shrink-0 text-ink-muted transition-transform duration-200 ease-standard"
        [class.rotate-180]="open()"
      />
    </button>
    @if (open()) {
      <div
        role="listbox"
        [id]="listboxId()"
        [class]="panelClasses()"
        [class.top-full]="!dropUp()"
        [class.mt-1]="!dropUp()"
        [class.bottom-full]="dropUp()"
        [class.mb-1]="dropUp()"
        [attr.data-testid]="testId() + '-panel'"
        (mousedown)="$event.preventDefault()"
      >
        @for (option of options(); track option.value; let i = $index) {
          <!-- A real button element (natively focusable/activatable) kept out
               of the tab order: the combobox keeps DOM focus on the trigger and
               announces the active option via aria-activedescendant. -->
          <button
            type="button"
            role="option"
            tabindex="-1"
            [id]="optionDomId(option)"
            class="block w-full cursor-pointer border-none px-3 py-2 text-left text-body text-ink transition-colors duration-100 ease-standard"
            [class.bg-selected]="isSelected(option)"
            [class.bg-page]="i === activeIndex() && !isSelected(option)"
            [class.bg-transparent]="i !== activeIndex() && !isSelected(option)"
            [attr.aria-selected]="isSelected(option)"
            [attr.data-testid]="testId() + '-option-' + option.value"
            (click)="commit(option)"
          >
            {{ option.label }}
          </button>
        } @empty {
          <div
            class="p-4 text-center text-body-sm text-ink-muted"
            [attr.data-testid]="testId() + '-empty'"
          >
            {{ emptyText() ?? '' }}
          </div>
        }
      </div>
    }
  `,
})
export class Select implements ControlValueAccessor {
  readonly options = input.required<readonly SelectOption[]>();
  /** The FormField's `controlId`; also seeds the ARIA ids below. */
  readonly controlId = input.required<string>();
  readonly placeholder = input<string | undefined>(undefined);
  /** Resolved "no results" text (the mock's literal is not carried — §4.14). */
  readonly emptyText = input<string | undefined>(undefined);
  readonly size = input<'md' | 'sm'>('md');
  readonly error = input<boolean>(false);
  /** Open the panel ABOVE the trigger — the mock's per-page selector does this
   *  so the panel stays inside the results card (mock-ui-analysis §6.2,
   *  `.eds-pagesize-up`). */
  readonly dropUp = input<boolean>(false);
  readonly testId = input.required<string>();

  protected readonly open = signal(false);
  protected readonly activeIndex = signal(-1);
  protected readonly value = signal<SelectValue | null>(null);
  private readonly cvaDisabled = signal(false);

  private onChange: (value: SelectValue | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  /** Disabled comes only from the form (see TextInput note): `control.disable()`
   *  — exactly how the 501 filter fields will be modelled (FE-ADR-013 §b). */
  protected readonly isDisabled = computed(() => this.cvaDisabled());
  protected readonly listboxId = computed(() => `${this.controlId()}-listbox`);
  protected readonly selectedOption = computed(
    () => this.options().find((option) => option.value === this.value()) ?? null,
  );
  protected readonly activeDescendant = computed(() => {
    const index = this.activeIndex();
    if (!this.open() || index < 0) return null;
    const option = this.options()[index];
    return option ? this.optionDomId(option) : null;
  });

  /** Mock Select.jsx trigger: input border/bg tokens + ring while open. */
  protected readonly triggerClasses = computed(() => {
    const base =
      'flex w-full items-center justify-between gap-2 rounded-md border px-3 text-left ' +
      (this.size() === 'sm' ? 'h-8 ' : 'h-10 ') +
      'text-body transition-[border-color,box-shadow] duration-150 ease-standard';
    if (this.isDisabled()) {
      return `${base} cursor-not-allowed border-line-strong bg-sunken text-ink-faint`;
    }
    const border = this.error()
      ? 'border-danger'
      : this.open()
        ? 'border-brand shadow-focus-ring'
        : 'border-line-strong';
    return `${base} cursor-pointer bg-surface text-ink ${border}`;
  });

  /**
   * The mock's panel: ALWAYS an overlay (absolutely positioned against the
   * host), so opening it never changes the surrounding layout — a dialog that
   * resized itself as its dropdown opened was the 2026-08-05 defect (scope
   * §4.32). Ancestors that would clip it opt out instead: a `Modal` sets
   * `overflowVisible`, exactly as the mock's compact dialogs do.
   */
  protected readonly panelClasses = computed(
    () =>
      'animate-panel-in absolute left-0 z-dropdown max-h-65 w-full overflow-y-auto ' +
      'rounded-lg border border-line bg-surface shadow-e2',
  );

  protected optionDomId(option: SelectOption): string {
    return `${this.controlId()}-option-${option.value}`;
  }

  protected isSelected(option: SelectOption): boolean {
    return option.value === this.value();
  }

  protected toggle(): void {
    if (this.open()) {
      this.close();
    } else {
      this.openPanel();
    }
  }

  protected close(): void {
    if (!this.open()) return;
    this.open.set(false);
    this.activeIndex.set(-1);
    this.onTouched();
  }

  private openPanel(): void {
    if (this.isDisabled()) return;
    this.open.set(true);
    const selected = this.options().findIndex((option) => option.value === this.value());
    this.activeIndex.set(selected >= 0 ? selected : 0);
  }

  protected commit(option: SelectOption): void {
    this.value.set(option.value);
    this.onChange(option.value);
    this.close();
  }

  protected onKeydown(event: KeyboardEvent): void {
    const { key } = event;
    const count = this.options().length;
    if (!this.open()) {
      if (key === 'ArrowDown' || key === 'ArrowUp' || key === 'Enter' || key === ' ') {
        event.preventDefault();
        this.openPanel();
      }
      return;
    }
    switch (key) {
      case 'ArrowDown':
        event.preventDefault();
        this.activeIndex.set(Math.min(this.activeIndex() + 1, count - 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.activeIndex.set(Math.max(this.activeIndex() - 1, 0));
        break;
      case 'Home':
        event.preventDefault();
        this.activeIndex.set(0);
        break;
      case 'End':
        event.preventDefault();
        this.activeIndex.set(count - 1);
        break;
      case 'Enter':
      case ' ': {
        event.preventDefault();
        const active = this.options()[this.activeIndex()];
        if (active) this.commit(active);
        break;
      }
      case 'Escape':
        event.preventDefault();
        this.close();
        break;
      case 'Tab':
        this.close();
        break;
      default:
        if (key.length === 1 && key !== ' ') {
          this.jumpTo(key);
        }
    }
  }

  /** Type-ahead: next option (wrapping) whose label starts with the character. */
  private jumpTo(character: string): void {
    const needle = character.toLocaleLowerCase();
    const options = this.options();
    const start = this.activeIndex();
    for (let step = 1; step <= options.length; step++) {
      const index = (start + step) % options.length;
      if (options[index].label.toLocaleLowerCase().startsWith(needle)) {
        this.activeIndex.set(index);
        return;
      }
    }
  }

  // ControlValueAccessor ------------------------------------------------------
  writeValue(value: SelectValue | null): void {
    this.value.set(value);
  }
  registerOnChange(fn: (value: SelectValue | null) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.cvaDisabled.set(isDisabled);
  }
}
