import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TextInput } from './text-input';

@Component({
  imports: [TextInput, ReactiveFormsModule],
  template: `
    <app-text-input
      [formControl]="control"
      controlId="spec-input"
      [placeholder]="placeholder()"
      [inputMode]="inputMode()"
      [maxLength]="maxLength()"
      [showCount]="showCount()"
      [clearable]="clearable()"
      [clearAriaLabel]="clearAriaLabel()"
      [error]="error()"
      [readonly]="readonly()"
      describedBy="spec-input-error"
      testId="spec-text-input"
      (cleared)="clearedCount = clearedCount + 1"
    />
  `,
})
class Host {
  readonly control = new FormControl('', { nonNullable: true });
  readonly placeholder = input<string | undefined>(undefined);
  readonly inputMode = input<'numeric' | 'tel' | 'email' | 'text' | undefined>(undefined);
  readonly maxLength = input<number | undefined>(undefined);
  readonly showCount = input<boolean>(false);
  readonly clearable = input<boolean>(false);
  readonly clearAriaLabel = input<string | undefined>('Temizle');
  readonly error = input<boolean>(false);
  readonly readonly = input<boolean>(false);
  clearedCount = 0;
}

describe('TextInput', () => {
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

  function inputEl(fixture: ComponentFixture<Host>): HTMLInputElement {
    const el = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="spec-text-input"]',
    );
    if (!el) throw new Error('input not rendered');
    return el as HTMLInputElement;
  }

  function type(fixture: ComponentFixture<Host>, text: string): void {
    const el = inputEl(fixture);
    el.value = text;
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('carries the FormField id and the resolved placeholder', () => {
    const el = inputEl(render({ placeholder: 'TCKN girin' }));
    expect(el.id).toBe('spec-input');
    expect(el.getAttribute('placeholder')).toBe('TCKN girin');
  });

  it('supports inputmode="numeric" for identity-number fields', () => {
    expect(inputEl(render({ inputMode: 'numeric' })).getAttribute('inputmode')).toBe('numeric');
    expect(inputEl(render()).getAttribute('inputmode')).toBeNull();
  });

  it('propagates typed text into the FormControl (CVA → form)', () => {
    const fixture = render();
    type(fixture, '12345678901');
    expect(fixture.componentInstance.control.value).toBe('12345678901');
  });

  it('renders programmatic form values (form → CVA)', () => {
    const fixture = render();
    fixture.componentInstance.control.setValue('Ayşe');
    fixture.detectChanges();
    expect(inputEl(fixture).value).toBe('Ayşe');
  });

  it('marks the control touched on blur', () => {
    const fixture = render();
    expect(fixture.componentInstance.control.touched).toBe(false);
    inputEl(fixture).dispatchEvent(new Event('focus'));
    inputEl(fixture).dispatchEvent(new Event('blur'));
    expect(fixture.componentInstance.control.touched).toBe(true);
  });

  it('error state sets aria-invalid and the danger border on the wrapper', () => {
    const fixture = render({ error: true });
    const el = inputEl(fixture);
    expect(el.getAttribute('aria-invalid')).toBe('true');
    expect(el.parentElement?.className).toContain('border-danger');
  });

  it('wires aria-describedby to the FormField error row', () => {
    expect(inputEl(render()).getAttribute('aria-describedby')).toBe('spec-input-error');
  });

  it('focus switches the wrapper to border-focus + focus ring (§3.6)', () => {
    const fixture = render();
    inputEl(fixture).dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    const wrap = inputEl(fixture).parentElement;
    expect(wrap?.className).toContain('border-brand');
    expect(wrap?.className).toContain('shadow-focus-ring');
  });

  it('disabled (via the FORM, the only source) and readonly use their §3.6 sunken looks', () => {
    const fixture = render();
    fixture.componentInstance.control.disable();
    fixture.detectChanges();
    const disabled = inputEl(fixture);
    expect(disabled.disabled).toBe(true);
    expect(disabled.parentElement?.className).toContain('bg-sunken');
    const readonly = inputEl(render({ readonly: true }));
    expect(readonly.readOnly).toBe(true);
    expect(readonly.parentElement?.className).toContain('bg-sunken');
  });

  it('clear button appears only with content, clears the control and emits', () => {
    const fixture = render({ clearable: true });
    const clearId = '[data-testid="spec-text-input-clear"]';
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector(clearId)).toBeNull(); // empty → hidden
    type(fixture, 'abc');
    const clearButton = root.querySelector(clearId) as HTMLButtonElement;
    expect(clearButton).not.toBeNull();
    expect(clearButton.getAttribute('aria-label')).toBe('Temizle');
    clearButton.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.control.value).toBe('');
    expect(fixture.componentInstance.clearedCount).toBe(1);
    expect(root.querySelector(clearId)).toBeNull(); // empty again → hidden
  });

  it('shows the character counter when showCount + maxLength are set', () => {
    const fixture = render({ showCount: true, maxLength: 10 });
    type(fixture, 'abcd');
    const wrap = inputEl(fixture).parentElement;
    expect(wrap?.textContent).toContain('4/10');
    expect(inputEl(fixture).getAttribute('maxlength')).toBe('10');
  });

  it('form-level disable (CVA setDisabledState) also disables the input', () => {
    const fixture = render();
    fixture.componentInstance.control.disable();
    fixture.detectChanges();
    expect(inputEl(fixture).disabled).toBe(true);
  });
});
