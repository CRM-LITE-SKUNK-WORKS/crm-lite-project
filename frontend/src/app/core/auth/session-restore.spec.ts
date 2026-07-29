import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { installSessionRestoreCheck } from './session-restore';

/**
 * The back/forward-cache path (see `session-restore.ts`). It cannot be covered
 * by a guard or initializer test, because the whole point is that a bfcache
 * restore runs neither.
 */
describe('session restore check', () => {
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let replace: ReturnType<typeof vi.fn>;
  let realLocation: PropertyDescriptor;
  let removeListener: () => void;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);

    replace = vi.fn();
    realLocation = Object.getOwnPropertyDescriptor(window, 'location')!;
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { replace, assign: vi.fn(), pathname: '/customers' },
    });

    // The listener goes on the real window, so it must come back off between
    // tests or the handlers stack up.
    removeListener = installSessionRestoreCheck(auth);
  });

  afterEach(() => {
    removeListener();
    Object.defineProperty(window, 'location', realLocation);
    window.sessionStorage.clear();
  });

  /** jsdom's PageTransitionEvent support varies; the flag is what matters. */
  function firePageShow(persisted: boolean): void {
    const event = new Event('pageshow');
    Object.defineProperty(event, 'persisted', { value: persisted });
    window.dispatchEvent(event);
  }

  function signIn(): void {
    auth.loadSession().subscribe();
    httpMock
      .expectOne('/api/session/me')
      .flush({ authenticated: true, username: 'ayilmaz', subject: 'sub-1', roles: ['crm-user'] });
  }

  it('ignores an ordinary load — the app initializer already probed', () => {
    firePageShow(false);

    httpMock.verify(); // no probe
    expect(replace).not.toHaveBeenCalled();
  });

  /**
   * The reported bug: sign out, press Back, and the frozen customer screen comes
   * back with its data. Nothing may be awaited here — a probe round trip would
   * leave that data on screen while it runs.
   */
  it('leaves a restored page immediately when the session is already known to be gone', () => {
    firePageShow(true);

    expect(replace).toHaveBeenCalledWith('/oauth2/authorization/keycloak');
    httpMock.verify(); // and it did NOT wait for a probe to decide
  });

  it('verifies a restored page that still believes it is signed in, and keeps it', () => {
    signIn();

    firePageShow(true);
    httpMock
      .expectOne('/api/session/me')
      .flush({ authenticated: true, username: 'ayilmaz', subject: 'sub-1', roles: ['crm-user'] });

    expect(replace).not.toHaveBeenCalled();
  });

  /** Frozen for hours: the gateway session can have died in the meantime. */
  it('leaves a restored page whose session died while it was frozen', () => {
    signIn();

    firePageShow(true);
    httpMock
      .expectOne('/api/session/me')
      .flush(
        { messageKey: 'MSG-AUTH-UNAUTHORIZED', message: 'x', path: '/api/session/me' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(replace).toHaveBeenCalledWith('/oauth2/authorization/keycloak');
  });
});
