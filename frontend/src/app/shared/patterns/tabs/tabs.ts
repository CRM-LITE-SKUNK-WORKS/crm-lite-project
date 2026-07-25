import { Component, computed, input, output } from '@angular/core';

/** One tab of the {@link Tabs} strip. `label` arrives RESOLVED (`'KEY' | t` at
 *  the call site, scope §4.14); `id` is a stable, untranslated slug. */
export interface TabItem {
  readonly id: string;
  readonly label: string;
}

/**
 * Tabs pattern (shared/patterns) — the mock's Customer Info tab strip
 * (mock-ui-analysis §6.4): equal-width buttons over a 1px bottom border, with
 * a 2px brand underline that SLIDES to the active tab
 * (`width = 100/n %`, `translateX(index · 100%)`, 220ms standard easing).
 * §7.2 hand-built pattern (FE-ADR-011 §d).
 *
 * Purely presentational: which tab is active is the CALLER's state (`activeId`
 * input + `tabChange` intent) — the strip never switches itself, so tab state
 * has one owner and the caller can reset sub-state on change (mock §6.4 rule).
 *
 * A11y (FE-ADR-011 §g): `role="tablist"` / `role="tab"` + `aria-selected`;
 * roving tabindex (only the active tab is tabbable); ←/→ move AND select
 * (activation follows focus), Home/End jump. Panel convention: each tab
 * carries id `{testId}-{id}-tab` and `aria-controls="{testId}-{id}-panel"` —
 * the caller gives its panel that id, `role="tabpanel"` and
 * `aria-labelledby="{testId}-{id}-tab"`.
 *
 * The underline's translate/width are computed style bindings (as the mock
 * does) — the values derive from the tab count, not from any design constant.
 */
@Component({
  selector: 'app-tabs',
  host: { class: 'relative block border-b border-line' },
  template: `
    <div role="tablist" class="flex" [attr.data-testid]="testId()">
      @for (tab of tabs(); track tab.id; let i = $index) {
        <button
          type="button"
          role="tab"
          [id]="testId() + '-' + tab.id + '-tab'"
          class="flex-1 px-1 py-3 text-center text-body transition-colors duration-100 ease-standard"
          [class.font-semibold]="tab.id === activeId()"
          [class.text-ink]="tab.id === activeId()"
          [class.font-medium]="tab.id !== activeId()"
          [class.text-ink-soft]="tab.id !== activeId()"
          [tabindex]="tab.id === activeId() ? 0 : -1"
          [attr.aria-selected]="tab.id === activeId()"
          [attr.aria-controls]="testId() + '-' + tab.id + '-panel'"
          [attr.data-testid]="testId() + '-' + tab.id"
          (click)="select(tab.id)"
          (keydown)="onKeydown($event, i)"
        >
          {{ tab.label }}
        </button>
      }
    </div>
    <span
      aria-hidden="true"
      class="absolute -bottom-px left-0 h-0.5 rounded-full bg-brand transition-transform duration-200 ease-standard"
      [style.width.%]="underlineWidth()"
      [style.transform]="underlineTransform()"
    ></span>
  `,
})
export class Tabs {
  readonly tabs = input.required<readonly TabItem[]>();
  /** The active tab id — caller-owned state; the strip only reflects it. */
  readonly activeId = input.required<string>();
  readonly testId = input.required<string>();

  /** Fired with the chosen tab id; never fires for the already-active tab. */
  readonly tabChange = output<string>();

  protected readonly activeIndex = computed(() =>
    Math.max(
      0,
      this.tabs().findIndex((tab) => tab.id === this.activeId()),
    ),
  );
  protected readonly underlineWidth = computed(() => 100 / Math.max(1, this.tabs().length));
  protected readonly underlineTransform = computed(
    () => `translateX(${this.activeIndex() * 100}%)`,
  );

  protected select(id: string): void {
    if (id === this.activeId()) return;
    this.tabChange.emit(id);
  }

  /** Roving tabindex: arrows move focus AND selection; Home/End jump. */
  protected onKeydown(event: KeyboardEvent, index: number): void {
    const count = this.tabs().length;
    let next: number;
    switch (event.key) {
      case 'ArrowRight':
        next = (index + 1) % count;
        break;
      case 'ArrowLeft':
        next = (index - 1 + count) % count;
        break;
      case 'Home':
        next = 0;
        break;
      case 'End':
        next = count - 1;
        break;
      default:
        return;
    }
    event.preventDefault();
    const tab = this.tabs()[next];
    this.select(tab.id);
    const target = event.target as HTMLElement;
    const buttons = target.closest('[role="tablist"]')?.querySelectorAll<HTMLElement>('[role="tab"]');
    buttons?.[next]?.focus();
  }
}
