import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../core/auth';
import { I18nService } from '../core/i18n';
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

/**
 * Header identity — the rendered chrome this time, because the whole decision
 * (scope §2.20, revised 2026-08-12) is about what the user SEES: the display name
 * with its `username` fallback, and the title segment (mock §4.1's placement, left
 * of the avatar) that must be absent rather than empty when the session carries no
 * title.
 *
 * The session arrives the way it does in production — flushed through the real
 * `AuthService` probe — so a field that stopped flowing anywhere along
 * `SessionResponse → AuthService → template` fails here.
 */
describe('Shell header identity', () => {
  let http: HttpTestingController;

  const SESSION = {
    authenticated: true,
    username: 'ayilmaz',
    subject: 'sub-1',
    roles: ['crm-user'],
    fullName: 'Ali Yilmaz',
    titleCode: 'SALES_REP',
  };

  beforeEach(() => {
    // I18nService persists the language, so a TR test would leak into the next one.
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(session: Record<string, unknown>): ComponentFixture<Shell> {
    TestBed.inject(AuthService).loadSession().subscribe();
    http.expectOne('/api/session/me').flush(session);
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    return fixture;
  }

  function byTestId(fixture: ComponentFixture<Shell>, id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  it('shows the full name, and the title in its own segment before the avatar', () => {
    const fixture = render(SESSION);

    const user = byTestId(fixture, 'app-user');
    const title = byTestId(fixture, 'app-user-title');
    expect(byTestId(fixture, 'app-username')?.textContent?.trim()).toBe('Ali Yilmaz');
    // The RESOLVED text, never the `SALES_REP` code that came over the wire.
    expect(title?.textContent?.trim()).toBe('Sales Representative');

    // Mock §4.1 order: … | title | divider | avatar + name. The title is a sibling
    // BEFORE the user block, not a second line inside it.
    expect(user?.contains(title!)).toBe(false);
    expect(title!.compareDocumentPosition(user!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  /** The whole point of carrying a code instead of text (scope §2.20 / §2.7): the
   *  title follows the language switch like any other word on screen. */
  it('localizes the title code, and re-renders it on a language switch', () => {
    const fixture = render(SESSION);
    expect(byTestId(fixture, 'app-user-title')?.textContent?.trim()).toBe('Sales Representative');

    TestBed.inject(I18nService).setLanguage('tr');
    fixture.detectChanges();

    expect(byTestId(fixture, 'app-user-title')?.textContent?.trim()).toBe('Satış Temsilcisi');
    // The name is data, not a label — it must NOT change with the language.
    expect(byTestId(fixture, 'app-username')?.textContent?.trim()).toBe('Ali Yilmaz');
  });

  /** A code the catalogue has not learned yet renders RAW. It must not become
   *  UI-ERROR-GENERIC ("Something went wrong") in the header, which is what
   *  `I18nService.translate` would have produced for an unknown key. */
  it('falls back to the raw code when the catalogue has no entry for it', () => {
    const fixture = render({ ...SESSION, titleCode: 'REGIONAL_DIRECTOR' });

    const shown = byTestId(fixture, 'app-user-title')?.textContent?.trim();
    expect(shown).toBe('REGIONAL_DIRECTOR');
    expect(shown).not.toBe(TestBed.inject(I18nService).translate('UI-ERROR-GENERIC'));
  });

  /** A principal with no `name` claim still has to be identifiable. */
  it('falls back to the username when the session carries no full name', () => {
    const fixture = render({ ...SESSION, fullName: null });

    expect(byTestId(fixture, 'app-username')?.textContent?.trim()).toBe('ayilmaz');
  });

  /** No title → no segment at all (and no orphan divider with it). Not an empty
   *  span, not a placeholder dash: inventing text for data the system does not
   *  hold is what FE-ADR-013 §a forbids, and that part of the decision did NOT
   *  change on 2026-08-12. */
  it('renders no title segment when the session carries no title', () => {
    const fixture = render({ ...SESSION, titleCode: null });

    expect(byTestId(fixture, 'app-user-title')).toBeNull();
    expect(byTestId(fixture, 'app-username')?.textContent?.trim()).toBe('Ali Yilmaz');
  });
});
