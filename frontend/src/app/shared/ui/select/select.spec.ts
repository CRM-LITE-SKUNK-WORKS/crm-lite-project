import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Select, type SelectOption, type SelectValue } from './select';

const OPTIONS: readonly SelectOption[] = [
  { value: 'Male', label: 'Erkek' },
  { value: 'Female', label: 'Kadın' },
];

@Component({
  imports: [Select, ReactiveFormsModule],
  template: `
    <app-select
      [formControl]="control"
      [options]="options()"
      controlId="spec-select"
      [placeholder]="placeholder()"
      [emptyText]="emptyText()"
      [error]="error()"
      [size]="size()"
      testId="spec-select"
    />
  `,
})
class Host {
  readonly control = new FormControl<SelectValue | null>(null);
  readonly options = input<readonly SelectOption[]>(OPTIONS);
  readonly placeholder = input<string | undefined>('Seçin');
  readonly emptyText = input<string | undefined>('Sonuç yok');
  readonly error = input<boolean>(false);
  readonly size = input<'md' | 'sm'>('md');
}

describe('Select', () => {
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

  function trigger(fixture: ComponentFixture<Host>): HTMLButtonElement {
    const el = byTestId(fixture, 'spec-select');
    if (!el) throw new Error('trigger not rendered');
    return el as HTMLButtonElement;
  }

  function press(fixture: ComponentFixture<Host>, key: string): void {
    trigger(fixture).dispatchEvent(new KeyboardEvent('keydown', { key, cancelable: true }));
    fixture.detectChanges();
  }

  it('is a combobox trigger showing the resolved placeholder when empty', () => {
    const el = trigger(render());
    expect(el.getAttribute('role')).toBe('combobox');
    expect(el.getAttribute('aria-expanded')).toBe('false');
    expect(el.textContent).toContain('Seçin');
  });

  it('opens on click: listbox panel with value-keyed option test ids', () => {
    const fixture = render();
    trigger(fixture).click();
    fixture.detectChanges();
    expect(trigger(fixture).getAttribute('aria-expanded')).toBe('true');
    const panel = byTestId(fixture, 'spec-select-panel');
    expect(panel?.getAttribute('role')).toBe('listbox');
    expect(byTestId(fixture, 'spec-select-option-Male')).not.toBeNull();
    expect(byTestId(fixture, 'spec-select-option-Female')).not.toBeNull();
  });

  it('click on an option commits the VALUE to the form and closes', () => {
    const fixture = render();
    trigger(fixture).click();
    fixture.detectChanges();
    byTestId(fixture, 'spec-select-option-Female')?.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.control.value).toBe('Female');
    expect(byTestId(fixture, 'spec-select-panel')).toBeNull();
    expect(trigger(fixture).textContent).toContain('Kadın');
  });

  it('renders a programmatic form value (form → CVA)', () => {
    const fixture = render();
    fixture.componentInstance.control.setValue('Male');
    fixture.detectChanges();
    expect(trigger(fixture).textContent).toContain('Erkek');
  });

  it('full keyboard path: open with ArrowDown, navigate, select with Enter', () => {
    const fixture = render();
    press(fixture, 'ArrowDown'); // opens, active = first
    expect(byTestId(fixture, 'spec-select-panel')).not.toBeNull();
    press(fixture, 'ArrowDown'); // active = second
    expect(trigger(fixture).getAttribute('aria-activedescendant')).toBe(
      'spec-select-option-Female',
    );
    press(fixture, 'Enter');
    expect(fixture.componentInstance.control.value).toBe('Female');
    expect(byTestId(fixture, 'spec-select-panel')).toBeNull();
  });

  it('Home/End jump and Escape closes without committing', () => {
    const fixture = render();
    press(fixture, 'ArrowDown');
    press(fixture, 'End');
    expect(trigger(fixture).getAttribute('aria-activedescendant')).toBe(
      'spec-select-option-Female',
    );
    press(fixture, 'Home');
    expect(trigger(fixture).getAttribute('aria-activedescendant')).toBe('spec-select-option-Male');
    press(fixture, 'Escape');
    expect(byTestId(fixture, 'spec-select-panel')).toBeNull();
    expect(fixture.componentInstance.control.value).toBeNull();
  });

  it('type-ahead jumps to the next option starting with the pressed letter', () => {
    const fixture = render();
    press(fixture, 'ArrowDown');
    press(fixture, 'k');
    expect(trigger(fixture).getAttribute('aria-activedescendant')).toBe(
      'spec-select-option-Female',
    );
  });

  it('marks the selected option with aria-selected and the bg-selected token', () => {
    const fixture = render();
    fixture.componentInstance.control.setValue('Male');
    fixture.detectChanges();
    trigger(fixture).click();
    fixture.detectChanges();
    const selected = byTestId(fixture, 'spec-select-option-Male');
    expect(selected?.getAttribute('aria-selected')).toBe('true');
    expect(selected?.className).toContain('bg-selected');
  });

  it('form-disabled blocks opening; error shows the danger border', () => {
    const disabledFixture = render();
    disabledFixture.componentInstance.control.disable();
    disabledFixture.detectChanges();
    trigger(disabledFixture).click();
    disabledFixture.detectChanges();
    expect(byTestId(disabledFixture, 'spec-select-panel')).toBeNull();
    expect(trigger(render({ error: true })).className).toContain('border-danger');
  });

  it('shows the resolved empty text when there are no options', () => {
    const fixture = render({ options: [] });
    trigger(fixture).click();
    fixture.detectChanges();
    expect(byTestId(fixture, 'spec-select-empty')?.textContent).toContain('Sonuç yok');
  });

  it('form-level disable (CVA) reaches the trigger', () => {
    const fixture = render();
    fixture.componentInstance.control.disable();
    fixture.detectChanges();
    expect(trigger(fixture).disabled).toBe(true);
  });
});
