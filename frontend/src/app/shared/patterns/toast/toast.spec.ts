import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Toast, type ToastKind } from './toast';

@Component({
  imports: [Toast],
  template: `
    <app-toast
      [kind]="kind()"
      [message]="message()"
      dismissLabel="Dismiss notification"
      [autoDismissMs]="autoDismissMs()"
      testId="spec-toast"
      (dismissed)="dismissals = dismissals + 1"
    />
  `,
})
class Host {
  readonly kind = input<ToastKind>('success');
  readonly message = input<string>('Customer created.');
  readonly autoDismissMs = input<number>(6000);
  dismissals = 0;
}

describe('Toast', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ imports: [Host] });
  });

  afterEach(() => {
    vi.useRealTimers();
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
    return (fixture.nativeElement as HTMLElement).querySelector('[data-testid="spec-toast"]');
  }

  it('is an ARIA live region (role="status") showing the resolved message', () => {
    const el = root(render());
    expect(el?.getAttribute('role')).toBe('status');
    expect(el?.textContent).toContain('Customer created.');
  });

  it('maps the kind to the EDS feedback icon colour', () => {
    expect(root(render())?.querySelector('app-icon')?.className).toContain('text-success');
    expect(root(render({ kind: 'error' }))?.querySelector('app-icon')?.className).toContain(
      'text-danger',
    );
    expect(root(render({ kind: 'info' }))?.querySelector('app-icon')?.className).toContain(
      'text-info',
    );
  });

  it('emits dismissed from the ✕ button (caller removes the toast)', () => {
    const fixture = render();
    const dismiss = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="spec-toast-dismiss-button"]',
    ) as HTMLButtonElement;
    dismiss.click();
    expect(fixture.componentInstance.dismissals).toBe(1);
  });

  it('auto-dismisses after the §4.3 delay (6 s), not before', () => {
    const fixture = render();
    vi.advanceTimersByTime(5999);
    expect(fixture.componentInstance.dismissals).toBe(0);
    vi.advanceTimersByTime(1);
    expect(fixture.componentInstance.dismissals).toBe(1);
  });

  it('autoDismissMs=0 disables the timer entirely', () => {
    const fixture = render({ autoDismissMs: 0 });
    vi.advanceTimersByTime(60_000);
    expect(fixture.componentInstance.dismissals).toBe(0);
  });

  it('restarts the timer when the message is replaced mid-display', () => {
    const fixture = render();
    vi.advanceTimersByTime(4000);
    fixture.componentRef.setInput('message', 'Customer updated.');
    fixture.detectChanges();
    vi.advanceTimersByTime(4000); // old timer would have fired at 6s
    expect(fixture.componentInstance.dismissals).toBe(0);
    vi.advanceTimersByTime(2000); // new timer completes its own 6s
    expect(fixture.componentInstance.dismissals).toBe(1);
  });
});
