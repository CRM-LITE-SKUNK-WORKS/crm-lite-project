import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Skeleton } from './skeleton';

@Component({
  imports: [Skeleton],
  template: ` <app-skeleton [label]="label()" [lines]="lines()" testId="spec-skeleton" /> `,
})
class Host {
  readonly label = input<string>('Loading customers…');
  readonly lines = input<number>(6);
}

describe('Skeleton', () => {
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

  function root(fixture: ComponentFixture<Host>): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('[data-testid="spec-skeleton"]');
  }

  it('announces the loading state: role="status", aria-busy and the resolved label', () => {
    const el = root(render());
    expect(el?.getAttribute('role')).toBe('status');
    expect(el?.getAttribute('aria-busy')).toBe('true');
    expect(el?.getAttribute('aria-label')).toBe('Loading customers…');
  });

  it('renders six static placeholder lines by default (no shimmer — scope §4.20)', () => {
    const lines = root(render())?.querySelectorAll('div.bg-sunken');
    expect(lines?.length).toBe(6);
  });

  it('renders the requested number of lines, cycling the width rhythm', () => {
    const lines = Array.from(root(render({ lines: 8 }))?.querySelectorAll('div.bg-sunken') ?? []);
    expect(lines.length).toBe(8);
    expect(lines[2]?.className).toContain('w-3/4'); // mock rhythm position 3
    expect(lines[6]?.className).toContain('w-full'); // cycle restarts after 6
  });
});
