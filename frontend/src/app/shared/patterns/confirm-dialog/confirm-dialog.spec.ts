import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmDialog } from './confirm-dialog';

@Component({
  imports: [ConfirmDialog],
  template: `
    <app-confirm-dialog
      [open]="open()"
      title="Delete customer"
      message="Are you sure to delete this customer?"
      confirmLabel="Yes"
      cancelLabel="No"
      closeLabel="Close"
      [errorText]="errorText()"
      [busy]="busy()"
      testId="spec-confirm"
      (confirmed)="confirms = confirms + 1"
      (cancelled)="cancels = cancels + 1"
    />
  `,
})
class Host {
  readonly open = input<boolean>(true);
  readonly errorText = input<string | null>(null);
  readonly busy = input<boolean>(false);
  confirms = 0;
  cancels = 0;
}

describe('ConfirmDialog', () => {
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

  it('renders nothing while closed', () => {
    expect(byTestId(render({ open: false }), 'spec-confirm')).toBeNull();
  });

  it('is a HEADERLESS dialog named by its title (mock §7.2: no header bar)', () => {
    const fixture = render();
    const dialog = byTestId(fixture, 'spec-confirm');
    expect(dialog?.getAttribute('role')).toBe('dialog');
    expect(dialog?.getAttribute('aria-label')).toBe('Delete customer');
    expect(byTestId(fixture, 'spec-confirm-close-button')).toBeNull(); // no ✕, no error-state Close
    expect(dialog?.textContent).toContain('Are you sure to delete this customer?');
  });

  it('question state: No cancels, Yes confirms; dialog stays (caller owns visibility)', () => {
    const fixture = render();
    (byTestId(fixture, 'spec-confirm-cancel-button') as HTMLButtonElement).click();
    (byTestId(fixture, 'spec-confirm-confirm-button') as HTMLButtonElement).click();
    expect(fixture.componentInstance.cancels).toBe(1);
    expect(fixture.componentInstance.confirms).toBe(1);
    expect(byTestId(fixture, 'spec-confirm')).not.toBeNull();
  });

  it('busy renders the confirm button in its loading state and swallows activation', () => {
    const fixture = render({ busy: true });
    const confirm = byTestId(fixture, 'spec-confirm-confirm-button') as HTMLButtonElement;
    expect(confirm.getAttribute('aria-busy')).toBe('true');
    confirm.click();
    expect(fixture.componentInstance.confirms).toBe(0);
  });

  it('error state (mock §6.4 state 2): danger box with the translated messageKey, single Close', () => {
    const fixture = render({
      errorText: 'Since the customer has active products, the customer cannot be deleted.',
    });
    const error = byTestId(fixture, 'spec-confirm-error');
    expect(error?.getAttribute('role')).toBe('alert');
    expect(error?.textContent).toContain('cannot be deleted');
    expect(byTestId(fixture, 'spec-confirm-confirm-button')).toBeNull();
    expect(byTestId(fixture, 'spec-confirm-cancel-button')).toBeNull();
    (byTestId(fixture, 'spec-confirm-close-button') as HTMLButtonElement).click();
    expect(fixture.componentInstance.cancels).toBe(1);
  });

  it('Escape cancels (inherited Modal behaviour)', () => {
    const fixture = render();
    byTestId(fixture, 'spec-confirm')?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }),
    );
    expect(fixture.componentInstance.cancels).toBe(1);
  });
});
