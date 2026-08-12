import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService.logout', () => {
  let httpMock: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /**
   * jsdom marks `window.location` unforgeable, so its methods cannot be spied
   * directly — the whole object is swapped for the duration of one test.
   */
  function withStubbedLocation(run: (nav: { replace: ReturnType<typeof vi.fn> }) => void): void {
    const real = Object.getOwnPropertyDescriptor(window, 'location');
    const stub = { replace: vi.fn(), assign: vi.fn(), pathname: '/customers' };
    Object.defineProperty(window, 'location', { configurable: true, value: stub });
    try {
      run(stub);
    } finally {
      Object.defineProperty(window, 'location', real!);
    }
  }

  function signIn(): void {
    auth.loadSession().subscribe();
    httpMock.expectOne('/api/session/me').flush({
      authenticated: true,
      username: 'ayilmaz',
      subject: 'sub-1',
      roles: ['crm-user'],
      fullName: 'Ali Yilmaz',
      titleCode: 'SALES_REP',
    });
  }

  /**
   * The header's display identity comes from the probe and nowhere else
   * (scope §2.20, revised 2026-08-12) — and both fields are legitimately absent,
   * so the signals must normalise that to `null` instead of leaking `undefined`
   * into the template.
   *
   * `titleCode` is exposed as the RAW CODE: localizing it belongs to the consumer
   * (`Shell.title`), not here, so this service stays free of catalogue knowledge.
   */
  it('exposes the display name and title code from the session, null when absent', () => {
    signIn();
    expect(auth.fullName()).toBe('Ali Yilmaz');
    expect(auth.titleCode()).toBe('SALES_REP');

    auth.refreshSession().subscribe();
    httpMock.expectOne('/api/session/me').flush({
      authenticated: true,
      username: 'ayilmaz',
      subject: 'sub-1',
      roles: ['crm-user'],
      fullName: null,
      titleCode: null,
    });
    expect(auth.fullName()).toBeNull();
    expect(auth.titleCode()).toBeNull();
    expect(auth.username()).toBe('ayilmaz');
  });

  it('sends the logout as a POST so Angular attaches the CSRF header', () => {
    withStubbedLocation(() => {
      auth.logout();
      const request = httpMock.expectOne('/logout');
      expect(request.request.method).toBe('POST');
      request.flush({ logoutUrl: 'http://localhost:8180/logout' });
    });
  });

  /**
   * The point of the fix: sign-out must navigate to the URL the GATEWAY supplied
   * (Keycloak's end_session), not to a locally-built one — that top-level
   * navigation is what actually clears the Keycloak SSO cookie.
   *
   * It must be `replace`, not `assign`. `assign` pushes, which leaves the fully
   * rendered customer screen behind as the previous history entry; Back then
   * restores it from the bfcache with no guard, no initializer and no HTTP call,
   * putting real customer data back on screen after sign-out.
   */
  it('REPLACES the current history entry with the gateway-provided logoutUrl', () => {
    const logoutUrl =
      'http://localhost:8180/realms/crm-lite/protocol/openid-connect/logout?id_token_hint=x';

    withStubbedLocation((nav) => {
      signIn();
      auth.logout();
      httpMock.expectOne('/logout').flush({ logoutUrl });

      expect(nav.replace).toHaveBeenCalledWith(logoutUrl);
      expect(auth.isAuthenticated()).toBe(false);
    });
  });

  /**
   * Regression (observed on the running stack 2026-07-24, before the gateway
   * returned `logoutUrl` as JSON): sign-out must never depend on the logout
   * response settling — if it never arrives (network hang), a bounded timeout
   * still signs the user out locally.
   *
   * The fallback reloads `/` rather than announcing a sign-out that may not have
   * happened: with the POST unanswered the gateway session can still be alive,
   * and a reload shows whichever of the two is true.
   */
  it('completes the sign-out even when the logout response never arrives', () => {
    vi.useFakeTimers();

    withStubbedLocation((nav) => {
      signIn();
      expect(auth.isAuthenticated()).toBe(true);

      auth.logout();
      httpMock.expectOne('/logout'); // issued, then deliberately left unanswered
      vi.advanceTimersByTime(3100);

      expect(auth.isAuthenticated()).toBe(false);
      expect(nav.replace).toHaveBeenCalledWith('/');
    });
  });

  /** Login is a full-page navigation to the gateway, and it replaces rather than
   *  pushes: the entry it leaves is one the guard just refused. */
  it('starts login by replacing the current entry with the gateway authorization URL', () => {
    withStubbedLocation((nav) => {
      auth.login();
      expect(nav.replace).toHaveBeenCalledWith('/oauth2/authorization/keycloak');
      expect(auth.isAuthenticated()).toBe(false);
    });
  });
});
