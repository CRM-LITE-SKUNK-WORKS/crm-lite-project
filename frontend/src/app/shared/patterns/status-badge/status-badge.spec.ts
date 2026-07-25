import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { StatusBadge, type StatusBadgeVariant } from './status-badge';

@Component({
  imports: [StatusBadge],
  template: ` <app-status-badge [variant]="variant()" [label]="label()" testId="spec-badge" /> `,
})
class Host {
  readonly variant = input<StatusBadgeVariant>('success');
  readonly label = input<string>('Active');
}

describe('StatusBadge', () => {
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

  function badge(fixture: ComponentFixture<Host>): HTMLElement {
    const el = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="spec-badge"]');
    if (!el) throw new Error('badge not rendered');
    return el as HTMLElement;
  }

  it('renders the resolved label with a decorative dot', () => {
    const el = badge(render());
    expect(el.textContent).toContain('Active');
    expect(el.querySelector('[aria-hidden="true"]')).not.toBeNull();
  });

  it('maps variants to the §6.4 families (success green / neutral sunken)', () => {
    const success = badge(render());
    expect(success.className).toContain('bg-success-surface');
    expect(success.querySelector('span')?.className).toContain('bg-success');
    const neutral = badge(render({ variant: 'neutral', label: 'Passive' }));
    expect(neutral.className).toContain('bg-sunken');
    expect(neutral.textContent).toContain('Passive');
  });
});
