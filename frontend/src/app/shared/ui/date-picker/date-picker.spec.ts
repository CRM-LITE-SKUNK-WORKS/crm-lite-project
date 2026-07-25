import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { DatePicker } from './date-picker';
import { displayToIso, isoToDisplay, maskTyped } from './date-utils';

@Component({
  imports: [DatePicker, ReactiveFormsModule],
  template: `
    <app-date-picker
      [formControl]="control"
      controlId="spec-date"
      [placeholder]="placeholder()"
      [min]="min()"
      [max]="max()"
      [locale]="locale()"
      triggerAriaLabel="Takvimi aç"
      prevMonthAriaLabel="Önceki ay"
      nextMonthAriaLabel="Sonraki ay"
      prevYearAriaLabel="Önceki yıl"
      nextYearAriaLabel="Sonraki yıl"
      testId="spec-date-picker"
    />
  `,
})
class Host {
  readonly control = new FormControl<string | null>(null);
  readonly placeholder = input<string | undefined>('GG.AA.YYYY');
  readonly min = input<string | undefined>(undefined);
  readonly max = input<string | undefined>(undefined);
  readonly locale = input<'en' | 'tr'>('en');
}

describe('DatePicker date-utils', () => {
  it('masks typed input into DD.MM.YYYY progressively', () => {
    expect(maskTyped('0')).toBe('0');
    expect(maskTyped('021')).toBe('02.1');
    expect(maskTyped('02111996')).toBe('02.11.1996');
    expect(maskTyped('02.11.1996xx')).toBe('02.11.1996');
  });

  it('converts display ↔ ISO and rejects impossible dates', () => {
    expect(displayToIso('02.11.1996')).toBe('1996-11-02');
    expect(displayToIso('31.02.2000')).toBeNull(); // no Feb 31
    expect(displayToIso('02.11.96')).toBeNull(); // incomplete
    expect(isoToDisplay('1996-11-02')).toBe('02.11.1996');
    expect(isoToDisplay(null)).toBe('');
  });
});

describe('DatePicker', () => {
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

  function inputEl(fixture: ComponentFixture<Host>): HTMLInputElement {
    return byTestId(fixture, 'spec-date-picker') as HTMLInputElement;
  }

  function type(fixture: ComponentFixture<Host>, text: string): void {
    const el = inputEl(fixture);
    el.value = text;
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function openPanel(fixture: ComponentFixture<Host>): void {
    byTestId(fixture, 'spec-date-picker-trigger')?.click();
    fixture.detectChanges();
  }

  it('typing a full valid date commits ISO to the form, display stays dd.MM.yyyy', () => {
    const fixture = render();
    type(fixture, '02111996');
    expect(inputEl(fixture).value).toBe('02.11.1996');
    expect(fixture.componentInstance.control.value).toBe('1996-11-02');
  });

  it('a programmatic ISO value renders as dd.MM.yyyy (form → CVA)', () => {
    const fixture = render();
    fixture.componentInstance.control.setValue('1990-05-14');
    fixture.detectChanges();
    expect(inputEl(fixture).value).toBe('14.05.1990');
  });

  it('incomplete text errors on BLUR, not per keystroke (design note §7)', () => {
    const fixture = render();
    type(fixture, '0211'); // partial — no error yet
    expect(fixture.componentInstance.control.errors).toBeNull();
    inputEl(fixture).dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(fixture.componentInstance.control.errors).toEqual({ invalidDate: true });
    expect(fixture.componentInstance.control.value).toBeNull();
    expect(inputEl(fixture).getAttribute('aria-invalid')).toBe('true');
  });

  it('clearing the text clears both the value and the error', () => {
    const fixture = render();
    type(fixture, '0211');
    inputEl(fixture).dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    type(fixture, '');
    inputEl(fixture).dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(fixture.componentInstance.control.errors).toBeNull();
    expect(fixture.componentInstance.control.value).toBeNull();
  });

  it('opens a dialog panel on the trigger with the resolved aria-label', () => {
    const fixture = render();
    const trigger = byTestId(fixture, 'spec-date-picker-trigger');
    expect(trigger?.getAttribute('aria-label')).toBe('Takvimi aç');
    openPanel(fixture);
    expect(byTestId(fixture, 'spec-date-picker-panel')?.getAttribute('role')).toBe('dialog');
  });

  it('clicking a day commits that ISO date, syncs the text and closes', () => {
    const fixture = render();
    fixture.componentInstance.control.setValue('1996-11-02');
    fixture.detectChanges();
    openPanel(fixture);
    byTestId(fixture, 'spec-date-picker-day-1996-11-15')?.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.control.value).toBe('1996-11-15');
    expect(inputEl(fixture).value).toBe('15.11.1996');
    expect(byTestId(fixture, 'spec-date-picker-panel')).toBeNull();
  });

  it('the grid starts on Monday and marks the selected day (aria-selected + brand fill)', () => {
    const fixture = render();
    fixture.componentInstance.control.setValue('1996-11-02');
    fixture.detectChanges();
    openPanel(fixture);
    // November 1st 1996 was a Friday → 4 leading blanks under a Monday-first week.
    const day = byTestId(fixture, 'spec-date-picker-day-1996-11-02');
    expect(day?.getAttribute('aria-selected')).toBe('true');
    expect(day?.getAttribute('aria-current')).toBe('date');
    expect(day?.className).toContain('bg-brand');
    expect(day?.className).toContain('text-on-brand');
  });

  it('localizes month names via Intl from the locale input', () => {
    const fixture = render({ locale: 'tr' });
    fixture.componentInstance.control.setValue('1996-11-02');
    fixture.detectChanges();
    openPanel(fixture);
    const panel = byTestId(fixture, 'spec-date-picker-panel');
    expect(panel?.textContent).toContain('Kasım 1996');
  });

  it('month/year navigation buttons shift the visible month', () => {
    const fixture = render();
    fixture.componentInstance.control.setValue('1996-11-02');
    fixture.detectChanges();
    openPanel(fixture);
    byTestId(fixture, 'spec-date-picker-next-month')?.click();
    fixture.detectChanges();
    expect(byTestId(fixture, 'spec-date-picker-panel')?.textContent).toContain('December 1996');
    byTestId(fixture, 'spec-date-picker-prev-year')?.click();
    fixture.detectChanges();
    expect(byTestId(fixture, 'spec-date-picker-panel')?.textContent).toContain('December 1995');
  });

  it('keyboard on the grid: ArrowRight moves a day, PageDown a month, Escape closes', () => {
    const fixture = render();
    fixture.componentInstance.control.setValue('1996-11-02');
    fixture.detectChanges();
    openPanel(fixture);
    const panel = byTestId(fixture, 'spec-date-picker-panel');
    panel?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', cancelable: true }));
    fixture.detectChanges();
    expect(byTestId(fixture, 'spec-date-picker-day-1996-11-03')?.tabIndex).toBe(0);
    panel?.dispatchEvent(new KeyboardEvent('keydown', { key: 'PageDown', cancelable: true }));
    fixture.detectChanges();
    expect(byTestId(fixture, 'spec-date-picker-panel')?.textContent).toContain('December 1996');
    panel?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }));
    fixture.detectChanges();
    expect(byTestId(fixture, 'spec-date-picker-panel')).toBeNull();
  });

  it('days outside min/max are disabled (UX only — backend stays the authority)', () => {
    const fixture = render({ min: '1996-11-10', max: '1996-11-20' });
    fixture.componentInstance.control.setValue('1996-11-15');
    fixture.detectChanges();
    openPanel(fixture);
    expect(
      (byTestId(fixture, 'spec-date-picker-day-1996-11-09') as HTMLButtonElement).disabled,
    ).toBe(true);
    expect(
      (byTestId(fixture, 'spec-date-picker-day-1996-11-15') as HTMLButtonElement).disabled,
    ).toBe(false);
    expect(
      (byTestId(fixture, 'spec-date-picker-day-1996-11-21') as HTMLButtonElement).disabled,
    ).toBe(true);
  });

  it('form-disable blocks both the input and the trigger', () => {
    const fixture = render();
    fixture.componentInstance.control.disable();
    fixture.detectChanges();
    expect(inputEl(fixture).disabled).toBe(true);
    expect((byTestId(fixture, 'spec-date-picker-trigger') as HTMLButtonElement).disabled).toBe(
      true,
    );
    openPanel(fixture);
    expect(byTestId(fixture, 'spec-date-picker-panel')).toBeNull();
  });

  it('an outside mousedown closes the panel', () => {
    const fixture = render();
    openPanel(fixture);
    expect(byTestId(fixture, 'spec-date-picker-panel')).not.toBeNull();
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    fixture.detectChanges();
    expect(byTestId(fixture, 'spec-date-picker-panel')).toBeNull();
  });
});
