import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Stepper, type StepItem } from './stepper';

const STEPS: readonly StepItem[] = [
  { id: 'demographic', label: 'Demographic' },
  { id: 'address', label: 'Address' },
  { id: 'contact', label: 'Contact' },
];

@Component({
  imports: [Stepper],
  template: ` <app-stepper [steps]="steps()" [activeIndex]="activeIndex()" testId="spec-stepper" /> `,
})
class Host {
  readonly steps = input<readonly StepItem[]>(STEPS);
  readonly activeIndex = input<number>(0);
}

describe('Stepper', () => {
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

  function step(fixture: ComponentFixture<Host>, id: string): HTMLElement {
    const el = (fixture.nativeElement as HTMLElement).querySelector(
      `[data-testid="spec-stepper-step-${id}"]`,
    );
    if (!el) throw new Error(`step ${id} not rendered`);
    return el as HTMLElement;
  }

  it('renders a semantic ordered list with padStart step numbers (01/02/03)', () => {
    const fixture = render();
    expect((fixture.nativeElement as HTMLElement).querySelector('ol')).not.toBeNull();
    expect(step(fixture, 'demographic').textContent).toContain('01');
    expect(step(fixture, 'contact').textContent).toContain('03');
  });

  it('marks the active step with aria-current="step"; others not', () => {
    const fixture = render({ activeIndex: 1 });
    expect(step(fixture, 'address').getAttribute('aria-current')).toBe('step');
    expect(step(fixture, 'demographic').getAttribute('aria-current')).toBeNull();
  });

  it('done steps show a check instead of their number; upcoming stay sunken', () => {
    const fixture = render({ activeIndex: 2 });
    expect(step(fixture, 'demographic').querySelector('svg')).not.toBeNull(); // check icon
    expect(step(fixture, 'demographic').textContent).not.toContain('01');
    const upcoming = render({ activeIndex: 0 });
    expect(step(upcoming, 'contact').querySelector('svg')).toBeNull();
    expect(step(upcoming, 'contact').querySelector('span')?.className).toContain('bg-sunken');
  });

  it('is passive: contains no interactive elements (navigation is the wizard footer)', () => {
    const fixture = render();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('button, a').length).toBe(0);
  });
});
