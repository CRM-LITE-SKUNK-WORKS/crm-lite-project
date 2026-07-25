import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { IconButton, type IconButtonVariant } from './icon-button';

@Component({
  imports: [IconButton],
  template: `
    <app-icon-button
      icon="trash-2"
      [ariaLabel]="ariaLabel()"
      [variant]="variant()"
      [disabled]="disabled()"
      [loading]="loading()"
      testId="spec-icon-button"
      (click)="clicks = clicks + 1"
    />
  `,
})
class Host {
  readonly ariaLabel = input<string>('Sil');
  readonly variant = input<IconButtonVariant>('ghost');
  readonly disabled = input<boolean>(false);
  readonly loading = input<boolean>(false);
  clicks = 0;
}

describe('IconButton', () => {
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

  function button(fixture: ComponentFixture<Host>): HTMLButtonElement {
    const el = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="spec-icon-button"]',
    );
    if (!el) throw new Error('button not rendered');
    return el as HTMLButtonElement;
  }

  it('renders a 32×32 native button carrying the accessible name', () => {
    const el = button(render());
    expect(el.getAttribute('aria-label')).toBe('Sil');
    expect(el.className).toContain('h-8');
    expect(el.className).toContain('w-8');
    expect(el.type).toBe('button');
  });

  it('renders only an icon — never text of its own', () => {
    expect(button(render()).textContent?.trim()).toBe('');
    expect(button(render()).querySelector('svg')).not.toBeNull();
  });

  it('maps the danger-ghost variant to the feedback-danger tokens', () => {
    const el = button(render({ variant: 'danger-ghost' }));
    expect(el.className).toContain('bg-danger-surface');
    expect(el.className).toContain('text-danger');
  });

  it('disabled blocks activation via the native attribute', () => {
    const fixture = render({ disabled: true });
    expect(button(fixture).disabled).toBe(true);
    button(fixture).click();
    expect(fixture.componentInstance.clicks).toBe(0);
  });

  it('loading is focusable-but-inert with a motion-exempt spinner', () => {
    const fixture = render({ loading: true });
    const el = button(fixture);
    expect(el.disabled).toBe(false);
    expect(el.getAttribute('aria-busy')).toBe('true');
    expect(el.getAttribute('aria-disabled')).toBe('true');
    expect(el.querySelector('svg[data-eds-motion-exempt]')).not.toBeNull();
    el.click();
    expect(fixture.componentInstance.clicks).toBe(0);
  });

  it('clicks pass through when enabled', () => {
    const fixture = render();
    button(fixture).click();
    expect(fixture.componentInstance.clicks).toBe(1);
  });
});
