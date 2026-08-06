import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Stepper, type StepItem, type StepperVariant } from './stepper';

const STEPS: readonly StepItem[] = [
  { id: 'demographic', label: 'Demographic' },
  { id: 'address', label: 'Address' },
  { id: 'contact', label: 'Contact' },
];

@Component({
  imports: [Stepper],
  template: `
    <app-stepper
      [steps]="steps()"
      [activeIndex]="activeIndex()"
      [variant]="variant()"
      testId="spec-stepper"
    />
  `,
})
class Host {
  readonly steps = input<readonly StepItem[]>(STEPS);
  readonly activeIndex = input<number>(0);
  readonly variant = input<StepperVariant>('wizard');
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
    expect(step(upcoming, 'contact').querySelector('.rounded-full')?.className).toContain(
      'bg-sunken',
    );
  });

  /**
   * The connector-length defect (2026-08-05): the two lines must stay equal
   * however long the labels are. The mock achieves that by giving every
   * non-last STEP an equal share of the free space (flex-grow with an AUTO
   * basis) and letting the connector — the item's only growing child — absorb
   * it. A zero basis (`flex-1`) instead equalizes the ITEMS, which makes the
   * connector "equal width minus this label" and therefore label-dependent.
   */
  it('grows every step but the last, so both connectors get an equal share', () => {
    const fixture = render();
    const connector = (id: string): HTMLElement | null =>
      step(fixture, id).querySelector(':scope > [aria-hidden="true"]');

    for (const id of ['demographic', 'address']) {
      expect(step(fixture, id).className).toContain('grow');
      expect(step(fixture, id).className).not.toContain('flex-1');
      expect(connector(id)?.className).toContain('grow');
      expect(connector(id)?.className).toContain('min-w-6');
    }
    // Last step: no growth, no trailing connector (the mock hides it).
    expect(step(fixture, 'contact').className).not.toContain('grow');
    expect(connector('contact')).toBeNull();
  });

  /** A long label must not be able to steal from its own connector: the
   *  circle+label block is shrink-0 and sits outside the growing region. */
  it('keeps the circle and label out of the growing region', () => {
    const fixture = render();
    const block = step(fixture, 'demographic').querySelector('span');
    expect(block?.className).toContain('shrink-0');
    expect(block?.className).toContain('gap-2');
  });

  /**
   * The mock carries TWO step strips (scope §4.33) and they are genuinely
   * different designs — a fact worth pinning, because "make them the same
   * component" is the obvious wrong simplification here.
   */
  describe('variants', () => {
    const circle = (fixture: ComponentFixture<Host>, id: string): HTMLElement | null =>
      step(fixture, id).querySelector('.rounded-full');
    // `:scope >` matters: a DONE step's check icon is an aria-hidden <svg>
    // deeper in the same item, and its `className` is an SVGAnimatedString.
    const connector = (fixture: ComponentFixture<Host>, id: string): HTMLElement | null =>
      step(fixture, id).querySelector(':scope > [aria-hidden="true"]');

    it('wizard pads the ordinal and fills the circle', () => {
      const fixture = render({ activeIndex: 1 });
      expect(step(fixture, 'address').textContent).toContain('02');
      expect(circle(fixture, 'address')?.className).toContain('size-7');
      // Completed step: solid brand, and the connector behind it turns brand.
      expect(circle(fixture, 'demographic')?.className).toContain('bg-brand');
      expect(connector(fixture, 'demographic')?.className).toContain('bg-brand');
    });

    it('sale prints a bare ordinal, outlines the circle and never repaints the line', () => {
      const fixture = render({ activeIndex: 1, variant: 'sale' });

      expect(step(fixture, 'address').textContent).toContain('2');
      expect(step(fixture, 'address').textContent).not.toContain('02');
      expect(circle(fixture, 'address')?.className).toContain('size-6');
      expect(circle(fixture, 'address')?.className).toContain('border-brand');

      // Completed: TINTED, not filled — and its label greys out like an
      // upcoming one, which the wizard variant does not do.
      expect(circle(fixture, 'demographic')?.className).toContain('bg-selected');
      expect(circle(fixture, 'demographic')?.className).not.toContain('bg-brand');

      // Upcoming: white with an outline rather than a sunken fill.
      expect(circle(fixture, 'contact')?.className).toContain('bg-surface');

      // The hairline stays one colour throughout — no done/undone repaint.
      expect(connector(fixture, 'demographic')?.className).toContain('bg-line-strong');
      expect(connector(fixture, 'demographic')?.className).toContain('h-px');
      expect(connector(fixture, 'address')?.className).toContain('bg-line-strong');
    });

    /** Whatever the paint, the geometry that keeps the two lines equal is the
     *  same in both variants (scope §4.32). */
    it('shares the equal-connector geometry across variants', () => {
      for (const variant of ['wizard', 'sale'] as const) {
        const fixture = render({ variant });
        for (const id of ['demographic', 'address']) {
          expect(step(fixture, id).className).toContain('grow');
          expect(connector(fixture, id)?.className).toContain('grow');
          expect(connector(fixture, id)?.className).toContain('min-w-6');
        }
      }
    });
  });

  it('is passive: contains no interactive elements (navigation is the wizard footer)', () => {
    const fixture = render();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('button, a').length).toBe(0);
  });
});
