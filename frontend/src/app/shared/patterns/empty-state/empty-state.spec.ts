import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { EmptyState } from './empty-state';

@Component({
  imports: [EmptyState],
  template: `
    <app-empty-state icon="search-x" [title]="title()" [body]="body()" testId="spec-empty-state" />
  `,
})
class Host {
  readonly title = input<string>('No customer found');
  readonly body = input<string | undefined>('Use "Create new customer" above.');
}

describe('EmptyState', () => {
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
    return (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="spec-empty-state"]',
    );
  }

  it('renders the resolved title and body with the testId on the host', () => {
    const el = root(render());
    expect(el).not.toBeNull();
    expect(el?.textContent).toContain('No customer found');
    expect(el?.textContent).toContain('Use "Create new customer" above.');
  });

  it('renders the caller-chosen icon as decorative (aria-hidden)', () => {
    const svg = root(render())?.querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg?.getAttribute('aria-hidden')).toBe('true');
  });

  it('omits the body element when no body text is provided', () => {
    const el = root(render({ body: undefined }));
    expect(el?.textContent).toContain('No customer found');
    expect(el?.querySelectorAll('span.text-body').length).toBe(0);
  });
});
