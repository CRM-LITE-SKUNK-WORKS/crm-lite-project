import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../core/auth';
import { Shell } from './shell';

/**
 * Sign-out confirmation (the sidenav's logout button).
 *
 * Asserted on the component class rather than the rendered chrome: what matters
 * here is WHEN the session is destroyed, and the destroying call is
 * `AuthService.logout()`. The dialog's own markup, focus trap and buttons are
 * covered by `shared/patterns/confirm-dialog`.
 */
describe('Shell sign-out confirmation', () => {
  let shell: Shell;
  let logout: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    // Never let the real logout run: it ends in a full-page navigation.
    logout = vi.spyOn(TestBed.inject(AuthService), 'logout').mockImplementation(() => undefined);
    shell = TestBed.runInInjectionContext(() => new Shell());
  });

  afterEach(() => {
    logout.mockRestore();
  });

  it('starts closed and idle', () => {
    expect(shell.logoutConfirmOpen()).toBe(false);
    expect(shell.logoutBusy()).toBe(false);
  });

  /** The whole point of the dialog: the button asks, it does not sign out. */
  it('only opens the dialog when the sidenav button is pressed', () => {
    shell.requestLogout();

    expect(shell.logoutConfirmOpen()).toBe(true);
    expect(logout).not.toHaveBeenCalled();
  });

  it('signs out only once the user confirms', () => {
    shell.requestLogout();
    shell.confirmLogout();

    expect(logout).toHaveBeenCalledOnce();
    // Left open and busy on purpose: logout() ends in a full-page navigation, so
    // closing the dialog would only flash the chrome back before the browser goes.
    expect(shell.logoutBusy()).toBe(true);
    expect(shell.logoutConfirmOpen()).toBe(true);
  });

  it('leaves the session untouched when the user answers No', () => {
    shell.requestLogout();
    shell.cancelLogout();

    expect(shell.logoutConfirmOpen()).toBe(false);
    expect(shell.logoutBusy()).toBe(false);
    expect(logout).not.toHaveBeenCalled();
  });
});
