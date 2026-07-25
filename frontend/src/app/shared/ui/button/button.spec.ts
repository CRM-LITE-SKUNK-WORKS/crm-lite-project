import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Button, type ButtonSize, type ButtonVariant } from './button';

@Component({
  imports: [Button],
  template: `
    <app-button
      [variant]="variant()"
      [size]="size()"
      [disabled]="disabled()"
      [loading]="loading()"
      [fullWidth]="fullWidth()"
      [type]="type()"
      testId="spec-button"
      (click)="clicks = clicks + 1"
    >
      {{ 'UI-SPEC-LABEL' }}
    </app-button>
  `,
})
class Host {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly disabled = input<boolean>(false);
  readonly loading = input<boolean>(false);
  readonly fullWidth = input<boolean>(false);
  readonly type = input<'button' | 'submit'>('button');
  clicks = 0;
}

describe('Button', () => {
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
    const el = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="spec-button"]');
    if (!el) throw new Error('button not rendered');
    return el as HTMLButtonElement;
  }

  it('stamps the testId on the native button and projects the caller text', () => {
    const el = button(render());
    expect(el.tagName).toBe('BUTTON');
    expect(el.textContent).toContain('UI-SPEC-LABEL');
  });

  it('is type="button" by default — no implicit submit surprise', () => {
    expect(button(render()).type).toBe('button');
    expect(button(render({ type: 'submit' })).type).toBe('submit');
  });

  it('maps variants to the EDS action tokens', () => {
    expect(button(render()).className).toContain('bg-brand');
    expect(button(render({ variant: 'secondary' })).className).toContain('text-secondary-action');
    expect(button(render({ variant: 'danger' })).className).toContain('bg-danger');
  });

  it('maps sizes to the §3.4 table (md 40px / md padding 16, sm 32px / 12)', () => {
    const md = button(render()).className;
    expect(md).toContain('h-10');
    expect(md).toContain('px-4');
    const sm = button(render({ size: 'sm' })).className;
    expect(sm).toContain('h-8');
    expect(sm).toContain('px-3');
  });

  it('fullWidth only stretches layout', () => {
    expect(button(render({ fullWidth: true })).className).toContain('w-full');
    expect(button(render()).className).not.toContain('w-full');
  });

  it('disabled uses the native attribute and blocks clicks', () => {
    const fixture = render({ disabled: true });
    const el = button(fixture);
    expect(el.disabled).toBe(true);
    el.click();
    expect(fixture.componentInstance.clicks).toBe(0);
  });

  it('loading keeps the button focusable but inert: aria-busy + aria-disabled, no activation', () => {
    const fixture = render({ loading: true });
    const el = button(fixture);
    expect(el.disabled).toBe(false); // stays in the tab order (design note §2)
    expect(el.getAttribute('aria-busy')).toBe('true');
    expect(el.getAttribute('aria-disabled')).toBe('true');
    el.click();
    expect(fixture.componentInstance.clicks).toBe(0);
  });

  it('loading shows the motion-exempt EDS spinner', () => {
    const el = button(render({ loading: true }));
    const spinner = el.querySelector('svg[data-eds-motion-exempt]');
    expect(spinner).not.toBeNull();
    expect(spinner?.getAttribute('aria-hidden')).toBe('true');
  });

  it('clicks pass through when enabled', () => {
    const fixture = render();
    button(fixture).click();
    expect(fixture.componentInstance.clicks).toBe(1);
  });
});
