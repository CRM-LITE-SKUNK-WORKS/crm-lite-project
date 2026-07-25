import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Tabs, type TabItem } from './tabs';

const TABS: readonly TabItem[] = [
  { id: 'info', label: 'Customer info' },
  { id: 'account', label: 'Customer account' },
  { id: 'address', label: 'Address' },
  { id: 'contact', label: 'Contact medium' },
];

@Component({
  imports: [Tabs],
  template: `
    <app-tabs
      [tabs]="tabs()"
      [activeId]="activeId()"
      testId="spec-tabs"
      (tabChange)="changes.push($event)"
    />
  `,
})
class Host {
  readonly tabs = input<readonly TabItem[]>(TABS);
  readonly activeId = input<string>('info');
  readonly changes: string[] = [];
}

describe('Tabs', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Host] });
  });

  function render(inputs: Record<string, unknown> = {}): ComponentFixture<Host> {
    const fixture = TestBed.createComponent(Host);
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    fixture.detectChanges();
    return fixture;
  }

  function tab(fixture: ComponentFixture<Host>, id: string): HTMLButtonElement {
    const el = (fixture.nativeElement as HTMLElement).querySelector(
      `[data-testid="spec-tabs-${id}"]`,
    );
    if (!el) throw new Error(`tab ${id} not rendered`);
    return el as HTMLButtonElement;
  }

  it('renders a tablist of equal-width tabs with resolved labels', () => {
    const fixture = render();
    const list = (fixture.nativeElement as HTMLElement).querySelector('[role="tablist"]');
    expect(list?.getAttribute('data-testid')).toBe('spec-tabs');
    const buttons = Array.from(list?.querySelectorAll('[role="tab"]') ?? []);
    expect(buttons.length).toBe(4);
    expect(buttons[0].textContent).toContain('Customer info');
    expect(buttons.every((b) => b.className.includes('flex-1'))).toBe(true);
  });

  it('marks the active tab: aria-selected, tabbable, semibold; others tabindex -1', () => {
    const fixture = render({ activeId: 'address' });
    const active = tab(fixture, 'address');
    expect(active.getAttribute('aria-selected')).toBe('true');
    expect(active.tabIndex).toBe(0);
    expect(active.className).toContain('font-semibold');
    const inactive = tab(fixture, 'info');
    expect(inactive.getAttribute('aria-selected')).toBe('false');
    expect(inactive.tabIndex).toBe(-1);
  });

  it('slides the underline to the active index (width 100/n %, translateX)', () => {
    const fixture = render({ activeId: 'account' });
    const underline = (fixture.nativeElement as HTMLElement).querySelector(
      'span[aria-hidden="true"]',
    ) as HTMLElement;
    expect(underline.style.width).toBe('25%');
    expect(underline.style.transform).toBe('translateX(100%)');
  });

  it('emits tabChange on click but never for the already-active tab', () => {
    const fixture = render();
    tab(fixture, 'contact').click();
    tab(fixture, 'info').click(); // already active
    expect(fixture.componentInstance.changes).toEqual(['contact']);
  });

  it('wires each tab to its panel by convention (aria-controls / id)', () => {
    const fixture = render();
    const el = tab(fixture, 'address');
    expect(el.id).toBe('spec-tabs-address-tab');
    expect(el.getAttribute('aria-controls')).toBe('spec-tabs-address-panel');
  });

  it('ArrowRight/Left select the neighbour (activation follows focus), Home/End jump', () => {
    const fixture = render();
    const first = tab(fixture, 'info');
    first.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    first.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    first.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    expect(fixture.componentInstance.changes).toEqual(['account', 'contact', 'contact']);
  });
});
