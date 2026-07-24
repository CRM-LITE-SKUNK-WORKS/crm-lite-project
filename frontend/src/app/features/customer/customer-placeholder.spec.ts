import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { I18nService } from '../../core/i18n';
import { CustomerPlaceholder } from './customer-placeholder';

/**
 * Proves the component-test harness works end to end (render → query → assert)
 * and demonstrates the house style from docs/frontend/testing-conventions.md:
 *
 *   - elements are located by `data-testid`, never by CSS class or DOM shape,
 *   - assertions on copy check the RENDERED text, so a raw catalogue key
 *     leaking into the DOM fails the test,
 *   - nothing is asserted against a hardcoded English string that a language
 *     switch would invalidate — both languages are checked explicitly.
 */
describe('CustomerPlaceholder', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [CustomerPlaceholder] });
  });

  function render(): ComponentFixture<CustomerPlaceholder> {
    const fixture = TestBed.createComponent(CustomerPlaceholder);
    fixture.detectChanges();
    return fixture;
  }

  function byTestId(
    fixture: ComponentFixture<CustomerPlaceholder>,
    id: string,
  ): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  it('exposes its root under a stable data-testid', () => {
    expect(byTestId(render(), 'customer-placeholder')).not.toBeNull();
  });

  it('renders translated copy and never leaks a catalogue key into the DOM', () => {
    const text = byTestId(render(), 'customer-placeholder')?.textContent ?? '';
    expect(text).toContain('You are signed in');
    expect(text).not.toContain('UI-PLACEHOLDER');
  });

  it('re-renders when the language changes', () => {
    const fixture = render();
    TestBed.inject(I18nService).setLanguage('tr');
    fixture.detectChanges();
    expect(byTestId(fixture, 'customer-placeholder')?.textContent).toContain('Oturumunuz açık');
  });
});
