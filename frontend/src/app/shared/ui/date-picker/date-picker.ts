import {
  Component,
  ElementRef,
  computed,
  effect,
  forwardRef,
  inject,
  input,
  signal,
} from '@angular/core';
import {
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  type AbstractControl,
  type ControlValueAccessor,
  type ValidationErrors,
  type Validator,
} from '@angular/forms';
import { Icon } from '../icon/icon';
import {
  type DateParts,
  addDays,
  addMonths,
  compareIso,
  daysInMonth,
  displayToIso,
  isoToDisplay,
  isoToParts,
  maskTyped,
  mondayIndex,
  partsToIso,
  todayParts,
} from './date-utils';

interface DayCell {
  readonly iso: string;
  readonly day: number;
  readonly disabled: boolean;
  readonly selected: boolean;
  readonly active: boolean;
}

/**
 * EDS `DatePicker` (shared-ui-design-notes §7; custom panel per the 2026-07-25
 * decision, scope-and-conflicts §4.15). Mock-faithful: masked `DD.MM.YYYY`
 * typing kept in sync with a calendar popover (280px panel, Monday-first grid,
 * 28px day cells, radius-lg + elevation-2, `eds-panel-in` entry).
 *
 * Contract boundaries:
 * - FORM VALUE IS ALWAYS ISO `yyyy-MM-dd` (or null); display is always
 *   `dd.MM.yyyy` in both languages. The conversion lives HERE — screens never
 *   format dates (FE-ADR-012 §e).
 * - Month/weekday names come from `Intl.DateTimeFormat` via the `locale`
 *   input; the caller binds the active language (`[locale]="i18n.lang()"`) —
 *   `shared/` cannot inject the core I18nService (§4.14/§4.15).
 * - Invalid/incomplete text becomes a `{ invalidDate: true }` validator error
 *   ON BLUR, not per keystroke (design note §7); `min`/`max` are a UX
 *   affordance only, the backend stays the authority (FE-ADR-007).
 *
 * A11y (FE-ADR-011 §g): panel `role="dialog"`, grid `role="grid"`, day cells
 * `role="gridcell"` with roving tabindex; ←→↑↓ move by day/week, PageUp/Down
 * by month, Home/End to week start/end, Enter selects, Escape closes and
 * returns focus to the trigger; Tab cycles inside the panel (focus trap).
 */
@Component({
  selector: 'app-date-picker',
  imports: [Icon],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DatePicker), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => DatePicker), multi: true },
  ],
  host: { class: 'relative block' },
  template: `
    <div [class]="wrapClasses()">
      <input
        class="eds-tabular-nums w-full min-w-0 flex-1 border-none bg-transparent text-body text-ink outline-none placeholder:text-ink-faint disabled:cursor-not-allowed"
        [id]="controlId()"
        [value]="text()"
        [disabled]="isDisabled()"
        inputmode="numeric"
        [attr.placeholder]="placeholder() || null"
        [attr.aria-invalid]="showError() ? 'true' : null"
        [attr.aria-describedby]="describedBy() ?? null"
        [attr.data-testid]="testId()"
        (input)="onInput($event)"
        (focus)="focused.set(true)"
        (blur)="onBlur()"
      />
      <button
        type="button"
        class="inline-flex shrink-0 cursor-pointer border-none bg-transparent p-0 text-ink-muted disabled:cursor-not-allowed disabled:text-ink-faint"
        [disabled]="isDisabled()"
        [attr.aria-label]="triggerAriaLabel() || null"
        [attr.aria-expanded]="open()"
        [attr.data-testid]="testId() + '-trigger'"
        (click)="togglePanel()"
      >
        <app-icon name="calendar" [size]="16" />
      </button>
    </div>
    @if (open()) {
      <div
        role="dialog"
        class="animate-panel-in absolute top-full left-0 z-dropdown mt-1 w-70 rounded-lg border border-line bg-surface p-3 shadow-e2"
        [attr.data-testid]="testId() + '-panel'"
        (keydown)="onPanelKeydown($event)"
      >
        <div class="mb-2 flex items-center justify-between">
          <button
            type="button"
            class="inline-flex cursor-pointer rounded-sm border-none bg-transparent p-1 text-ink-soft"
            [attr.aria-label]="prevYearAriaLabel() || null"
            [attr.data-testid]="testId() + '-prev-year'"
            (click)="shiftView(-12)"
          >
            <app-icon name="chevrons-left" [size]="16" />
          </button>
          <button
            type="button"
            class="inline-flex cursor-pointer rounded-sm border-none bg-transparent p-1 text-ink-soft"
            [attr.aria-label]="prevMonthAriaLabel() || null"
            [attr.data-testid]="testId() + '-prev-month'"
            (click)="shiftView(-1)"
          >
            <app-icon name="chevron-left" [size]="16" />
          </button>
          <span class="text-body-sm font-medium text-ink" aria-live="polite">
            {{ monthLabel() }}
          </span>
          <button
            type="button"
            class="inline-flex cursor-pointer rounded-sm border-none bg-transparent p-1 text-ink-soft"
            [attr.aria-label]="nextMonthAriaLabel() || null"
            [attr.data-testid]="testId() + '-next-month'"
            (click)="shiftView(1)"
          >
            <app-icon name="chevron-right" [size]="16" />
          </button>
          <button
            type="button"
            class="inline-flex cursor-pointer rounded-sm border-none bg-transparent p-1 text-ink-soft"
            [attr.aria-label]="nextYearAriaLabel() || null"
            [attr.data-testid]="testId() + '-next-year'"
            (click)="shiftView(12)"
          >
            <app-icon name="chevrons-right" [size]="16" />
          </button>
        </div>
        <div class="mb-1 grid grid-cols-7 gap-0.5">
          @for (weekday of weekdayLabels(); track $index) {
            <div class="text-center text-caption text-ink-muted" aria-hidden="true">
              {{ weekday }}
            </div>
          }
        </div>
        <div class="grid grid-cols-7 gap-0.5" role="grid">
          @for (cell of leadingBlanks(); track $index) {
            <div></div>
          }
          @for (cell of dayCells(); track cell.iso) {
            <button
              type="button"
              role="gridcell"
              [class]="dayClasses(cell)"
              [disabled]="cell.disabled"
              [tabIndex]="cell.active ? 0 : -1"
              [attr.aria-selected]="cell.selected"
              [attr.aria-current]="cell.selected ? 'date' : null"
              [attr.data-testid]="testId() + '-day-' + cell.iso"
              (click)="selectDay(cell)"
            >
              {{ cell.day }}
            </button>
          }
        </div>
      </div>
    }
  `,
})
export class DatePicker implements ControlValueAccessor, Validator {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly controlId = input.required<string>();
  readonly placeholder = input<string | undefined>(undefined);
  readonly error = input<boolean>(false);
  /** ISO bounds — UX affordance only; the backend re-validates (FE-ADR-007). */
  readonly min = input<string | undefined>(undefined);
  readonly max = input<string | undefined>(undefined);
  /** Active UI language for Intl month/weekday names (§4.15). */
  readonly locale = input<'en' | 'tr'>('en');
  /** Resolved aria labels ('KEY' | t) for the icon-only controls (§4.14). */
  readonly triggerAriaLabel = input<string | undefined>(undefined);
  readonly prevMonthAriaLabel = input<string | undefined>(undefined);
  readonly nextMonthAriaLabel = input<string | undefined>(undefined);
  readonly prevYearAriaLabel = input<string | undefined>(undefined);
  readonly nextYearAriaLabel = input<string | undefined>(undefined);
  readonly describedBy = input<string | undefined>(undefined);
  readonly testId = input.required<string>();

  /** ISO form value (or null). */
  private readonly value = signal<string | null>(null);
  protected readonly text = signal('');
  protected readonly focused = signal(false);
  protected readonly open = signal(false);
  private readonly invalid = signal(false);
  private readonly cvaDisabled = signal(false);
  /** The keyboard cursor inside the calendar; also defines the visible month. */
  private readonly activeParts = signal<DateParts>(todayParts());

  private onChange: (value: string | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;
  private onValidatorChange: () => void = () => undefined;

  constructor() {
    // Close on outside pointer-down while open (panel holds focus, so a
    // trigger-blur close is not possible here the way Select does it).
    effect((onCleanup) => {
      if (!this.open()) return;
      const listener = (event: MouseEvent) => {
        if (!this.host.nativeElement.contains(event.target as Node)) {
          this.closePanel(false);
        }
      };
      document.addEventListener('mousedown', listener);
      onCleanup(() => document.removeEventListener('mousedown', listener));
    });
  }

  /** Disabled comes only from the form (see TextInput note). */
  protected readonly isDisabled = computed(() => this.cvaDisabled());
  protected readonly showError = computed(() => this.error() || this.invalid());

  protected readonly wrapClasses = computed(() => {
    const base =
      'flex h-10 items-center gap-2 rounded-md border px-3 ' +
      'transition-[border-color,box-shadow,background-color] duration-150 ease-standard';
    if (this.isDisabled()) return `${base} cursor-not-allowed border-transparent bg-sunken`;
    if (this.showError()) {
      return `${base} border-danger bg-surface ${this.focused() ? 'shadow-focus-ring' : ''}`;
    }
    if (this.focused()) return `${base} border-brand bg-surface shadow-focus-ring`;
    return `${base} border-line-strong bg-surface hover:border-line-hover`;
  });

  protected readonly monthLabel = computed(() => {
    const { year, month } = this.activeParts();
    const name = new Intl.DateTimeFormat(this.locale(), { month: 'long' }).format(
      new Date(year, month - 1, 1),
    );
    return `${name} ${year}`;
  });

  /** Monday-first two-letter weekday headers (mock: `Mo Tu We…`). */
  protected readonly weekdayLabels = computed(() => {
    const format = new Intl.DateTimeFormat(this.locale(), { weekday: 'short' });
    // 2024-01-01 is a Monday; walk one week from it.
    return Array.from({ length: 7 }, (_, i) => format.format(new Date(2024, 0, 1 + i)).slice(0, 2));
  });

  protected readonly leadingBlanks = computed(() => {
    const { year, month } = this.activeParts();
    return Array.from({ length: mondayIndex({ year, month, day: 1 }) }, (_, i) => i);
  });

  protected readonly dayCells = computed<readonly DayCell[]>(() => {
    const { year, month } = this.activeParts();
    const selected = this.value();
    const active = partsToIso(this.activeParts());
    return Array.from({ length: daysInMonth(year, month) }, (_, i) => {
      const iso = partsToIso({ year, month, day: i + 1 });
      return {
        iso,
        day: i + 1,
        disabled: this.isOutOfRange(iso),
        selected: selected === iso,
        active: active === iso,
      };
    });
  });

  /** Mock DatePicker.jsx day cell: 28px, radius-sm, selected = primary fill. */
  protected dayClasses(cell: DayCell): string {
    const base =
      // h-7/gap-0.5 mirror the mock's literal 28px cell + 2px grid gap.
      'h-7 cursor-pointer rounded-sm border-none text-body-sm ' +
      'transition-colors duration-150 ease-standard disabled:cursor-not-allowed';
    if (cell.selected) return `${base} bg-brand text-on-brand`;
    if (cell.disabled) return `${base} bg-transparent text-ink-faint`;
    return `${base} bg-transparent text-ink hover:bg-page`;
  }

  private isOutOfRange(iso: string): boolean {
    const min = this.min();
    const max = this.max();
    return (!!min && compareIso(iso, min) < 0) || (!!max && compareIso(iso, max) > 0);
  }

  // --- text path -------------------------------------------------------------
  protected onInput(event: Event): void {
    const masked = maskTyped((event.target as HTMLInputElement).value);
    this.text.set(masked);
    (event.target as HTMLInputElement).value = masked;
    if (masked.length === 0) {
      this.setValidity(false);
      this.commit(null);
      return;
    }
    const iso = displayToIso(masked);
    if (iso) {
      this.setValidity(false);
      this.commit(iso);
      const parts = isoToParts(iso);
      if (parts) this.activeParts.set(parts);
    }
  }

  /** Incomplete/impossible text turns into a validator error on blur (§7). */
  protected onBlur(): void {
    this.focused.set(false);
    const text = this.text();
    if (text.length > 0 && displayToIso(text) === null) {
      this.setValidity(true);
      this.commit(null);
    }
    this.onTouched();
  }

  // --- panel path ------------------------------------------------------------
  protected togglePanel(): void {
    if (this.open()) {
      this.closePanel(true);
      return;
    }
    const iso = this.value();
    this.activeParts.set((iso ? isoToParts(iso) : null) ?? todayParts());
    this.open.set(true);
    this.focusActiveDay();
  }

  protected closePanel(refocusTrigger: boolean): void {
    if (!this.open()) return;
    this.open.set(false);
    this.onTouched();
    if (refocusTrigger) {
      this.queryByTestId(`${this.testId()}-trigger`)?.focus();
    }
  }

  protected selectDay(cell: DayCell): void {
    this.setValidity(false);
    this.commit(cell.iso);
    this.text.set(isoToDisplay(cell.iso));
    this.closePanel(true);
  }

  protected shiftView(months: number): void {
    this.activeParts.set(addMonths(this.activeParts(), months));
  }

  protected onPanelKeydown(event: KeyboardEvent): void {
    const move = (parts: DateParts): void => {
      event.preventDefault();
      this.activeParts.set(parts);
      this.focusActiveDay();
    };
    const active = this.activeParts();
    switch (event.key) {
      case 'ArrowLeft':
        move(addDays(active, -1));
        break;
      case 'ArrowRight':
        move(addDays(active, 1));
        break;
      case 'ArrowUp':
        move(addDays(active, -7));
        break;
      case 'ArrowDown':
        move(addDays(active, 7));
        break;
      case 'PageUp':
        move(addMonths(active, -1));
        break;
      case 'PageDown':
        move(addMonths(active, 1));
        break;
      case 'Home':
        move(addDays(active, -mondayIndex(active)));
        break;
      case 'End':
        move(addDays(active, 6 - mondayIndex(active)));
        break;
      case 'Escape':
        event.preventDefault();
        this.closePanel(true);
        break;
      case 'Tab':
        this.trapTab(event);
        break;
      default:
        break;
    }
  }

  /** Keep Tab cycling inside the panel (design note §7 focus trap). */
  private trapTab(event: KeyboardEvent): void {
    const panel = this.queryByTestId(`${this.testId()}-panel`);
    if (!panel) return;
    const focusables = Array.from(panel.querySelectorAll<HTMLElement>('button:not([disabled])'));
    if (focusables.length === 0) return;
    const index = focusables.indexOf(document.activeElement as HTMLElement);
    const nextIndex = (index + (event.shiftKey ? -1 : 1) + focusables.length) % focusables.length;
    event.preventDefault();
    focusables[nextIndex].focus();
  }

  /** Roving-tabindex focus follows the active day after the DOM settles. */
  private focusActiveDay(): void {
    setTimeout(() => {
      this.queryByTestId(`${this.testId()}-day-${partsToIso(this.activeParts())}`)?.focus();
    });
  }

  private queryByTestId(id: string): HTMLElement | null {
    return this.host.nativeElement.querySelector(`[data-testid="${id}"]`);
  }

  private commit(value: string | null): void {
    this.value.set(value);
    this.onChange(value);
  }

  private setValidity(invalid: boolean): void {
    if (this.invalid() !== invalid) {
      this.invalid.set(invalid);
      this.onValidatorChange();
    }
  }

  // ControlValueAccessor ------------------------------------------------------
  writeValue(value: string | null): void {
    const iso = value && isoToParts(value) ? value : null;
    this.value.set(iso);
    this.text.set(isoToDisplay(iso));
    this.invalid.set(false);
    if (iso) {
      const parts = isoToParts(iso);
      if (parts) this.activeParts.set(parts);
    }
  }
  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.cvaDisabled.set(isDisabled);
  }

  // Validator -----------------------------------------------------------------
  validate(_control: AbstractControl): ValidationErrors | null {
    return this.invalid() ? { invalidDate: true } : null;
  }
  registerOnValidatorChange(fn: () => void): void {
    this.onValidatorChange = fn;
  }
}
