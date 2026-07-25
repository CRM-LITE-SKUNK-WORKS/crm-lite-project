import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Modal, ModalFooter, type ModalSize } from './modal';

@Component({
  imports: [Modal, ModalFooter],
  template: `
    <button type="button" data-testid="spec-outside-button">outside</button>
    <app-modal
      [open]="open()"
      [size]="size()"
      titleText="Delete account"
      closeLabel="Close dialog"
      [closeOnBackdrop]="closeOnBackdrop()"
      testId="spec-modal"
      (closed)="closes = closes + 1"
    >
      <p>Are you sure?</p>
      @if (withFooter()) {
        <div appModalFooter>
          <button type="button" data-testid="spec-modal-no-button">No</button>
          <button type="button" data-testid="spec-modal-yes-button">Yes</button>
        </div>
      }
    </app-modal>
  `,
})
class Host {
  readonly open = input<boolean>(true);
  readonly size = input<ModalSize>('md');
  readonly closeOnBackdrop = input<boolean>(false);
  readonly withFooter = input<boolean>(true);
  closes = 0;
}

describe('Modal', () => {
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

  function panel(fixture: ComponentFixture<Host>): HTMLElement {
    const el = byTestId(fixture, 'spec-modal');
    if (!el) throw new Error('modal panel not rendered');
    return el;
  }

  it('renders nothing while closed', () => {
    const fixture = render({ open: false });
    expect(byTestId(fixture, 'spec-modal')).toBeNull();
    expect(byTestId(fixture, 'spec-modal-backdrop')).toBeNull();
  });

  it('renders an aria-modal dialog named by its title, sized per §7.2', () => {
    const fixture = render();
    const dialog = panel(fixture);
    expect(dialog.getAttribute('role')).toBe('dialog');
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(dialog.className).toContain('max-w-modal-md');
    const titleId = dialog.getAttribute('aria-labelledby');
    expect(titleId).toBe('spec-modal-title');
    expect(dialog.querySelector(`#${titleId}`)?.textContent).toContain('Delete account');
    expect(panel(render({ size: 'sm' })).className).toContain('max-w-modal-sm');
  });

  it('moves focus INTO the dialog on open and restores it on close (focus trap contract)', () => {
    const fixture = render({ open: false });
    const outside = byTestId(fixture, 'spec-outside-button') as HTMLButtonElement;
    outside.focus();
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    expect(panel(fixture).contains(document.activeElement)).toBe(true);
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();
    expect(document.activeElement).toBe(outside);
  });

  it('wraps Tab from the last focusable back to the first (and Shift+Tab in reverse)', () => {
    const fixture = render();
    const yes = byTestId(fixture, 'spec-modal-yes-button') as HTMLButtonElement;
    const close = byTestId(fixture, 'spec-modal-close-button') as HTMLButtonElement;
    yes.focus();
    panel(fixture).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }),
    );
    expect(document.activeElement).toBe(close); // wrapped to the first focusable
    panel(fixture).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }),
    );
    expect(document.activeElement).toBe(yes); // and back to the last
  });

  it('emits closed on Escape and on the ✕ button — never closes itself', () => {
    const fixture = render();
    panel(fixture).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }),
    );
    expect(fixture.componentInstance.closes).toBe(1);
    expect(byTestId(fixture, 'spec-modal')).not.toBeNull(); // caller owns visibility
    (byTestId(fixture, 'spec-modal-close-button') as HTMLButtonElement).click();
    expect(fixture.componentInstance.closes).toBe(2);
  });

  it('ignores backdrop clicks by default; honours them with closeOnBackdrop', () => {
    const fixture = render();
    (byTestId(fixture, 'spec-modal-backdrop') as HTMLElement).click();
    expect(fixture.componentInstance.closes).toBe(0);
    const dismissable = render({ closeOnBackdrop: true });
    (byTestId(dismissable, 'spec-modal-backdrop') as HTMLElement).click();
    expect(dismissable.componentInstance.closes).toBe(1);
  });

  it('renders the footer bar only when footer content is projected', () => {
    expect(byTestId(render(), 'spec-modal-yes-button')).not.toBeNull();
    const bare = render({ withFooter: false });
    expect(byTestId(bare, 'spec-modal-yes-button')).toBeNull();
    expect(panel(bare).querySelectorAll('.border-t').length).toBe(0);
  });
});
