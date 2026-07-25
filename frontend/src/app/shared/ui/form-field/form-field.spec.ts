import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { FormField } from './form-field';

@Component({
  imports: [FormField],
  template: `
    <app-form-field
      [label]="label()"
      controlId="spec-control"
      [required]="required()"
      [optionalText]="optionalText()"
      [helperText]="helperText()"
      [errorText]="errorText()"
      testId="spec-field"
    >
      <input id="spec-control" data-testid="spec-control" />
    </app-form-field>
  `,
})
class Host {
  readonly label = input<string>('Ad');
  readonly required = input<boolean>(false);
  readonly optionalText = input<string | undefined>(undefined);
  readonly helperText = input<string | undefined>(undefined);
  readonly errorText = input<string | undefined>(undefined);
}

describe('FormField', () => {
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

  function byTestId(fixture: ComponentFixture<Host>, id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  it('binds the label to the projected control via for/controlId', () => {
    const fixture = render();
    const label = (fixture.nativeElement as HTMLElement).querySelector('label');
    expect(label?.getAttribute('for')).toBe('spec-control');
    expect(label?.textContent).toContain('Ad');
  });

  it('projects any control unchanged (content projection)', () => {
    expect(byTestId(render(), 'spec-control')).not.toBeNull();
  });

  it('renders the error row ALWAYS, so field height cannot jump (§3.8)', () => {
    const noError = byTestId(render(), 'spec-field-error');
    expect(noError).not.toBeNull();
    expect(noError?.className).toContain('min-h-4');
  });

  it('the error row id matches the aria-describedby contract: {controlId}-error', () => {
    expect(byTestId(render(), 'spec-field-error')?.id).toBe('spec-control-error');
  });

  it('error text wins over helper text and switches to the danger colour', () => {
    const fixture = render({ helperText: 'ipucu', errorText: 'hata metni' });
    const row = byTestId(fixture, 'spec-field-error');
    expect(row?.textContent).toContain('hata metni');
    expect(row?.textContent).not.toContain('ipucu');
    expect(row?.className).toContain('text-danger-fg');
    expect(row?.querySelector('svg')).not.toBeNull(); // alert-circle
  });

  it('helper text renders in the tertiary colour without an icon', () => {
    const row = byTestId(render({ helperText: 'ipucu' }), 'spec-field-error');
    expect(row?.textContent).toContain('ipucu');
    expect(row?.className).toContain('text-ink-muted');
    expect(row?.querySelector('svg')).toBeNull();
  });

  it('is polite, not an alert — bulk errors must not drown a screen reader', () => {
    const row = byTestId(render({ errorText: 'hata' }), 'spec-field-error');
    expect(row?.getAttribute('aria-live')).toBe('polite');
    expect(row?.getAttribute('role')).toBeNull();
  });

  it('required star is visual-only (aria-hidden); optional marker renders when provided', () => {
    const fixture = render({ required: true, optionalText: '(isteğe bağlı)' });
    const label = (fixture.nativeElement as HTMLElement).querySelector('label');
    const star = label?.querySelector('[aria-hidden="true"]');
    expect(star?.textContent).toBe('*');
    expect(label?.textContent).toContain('(isteğe bağlı)');
  });
});
