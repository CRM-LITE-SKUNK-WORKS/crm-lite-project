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
   * Regression (observed on the running stack 2026-07-24): the gateway answers
   * 302 to Keycloak's cross-origin end_session, which the XHR can stall on. When
   * sign-out was chained to that response settling, the button did nothing at
   * all. The wait must therefore be bounded.
   */
  it('completes the sign-out even when the logout response never arrives', () => {
    vi.useFakeTimers();

    auth.loadSession().subscribe();
    httpMock
      .expectOne('/api/session/me')
      .flush({ authenticated: true, username: 'ayilmaz', subject: 'sub-1', roles: ['crm-user'] });
    expect(auth.isAuthenticated()).toBe(true);

    auth.logout();
    httpMock.expectOne('/logout'); // issued, then deliberately left unanswered

    vi.advanceTimersByTime(3100);

    expect(auth.isAuthenticated()).toBe(false);
  });

  it('sends the logout as a POST so Angular attaches the CSRF header', () => {
    auth.logout();
    const request = httpMock.expectOne('/logout');
    expect(request.request.method).toBe('POST');
    request.flush('', { status: 200, statusText: 'OK' });
  });
});
