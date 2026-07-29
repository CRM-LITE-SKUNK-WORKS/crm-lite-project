import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  type ActivatedRouteSnapshot,
  Router,
  type RouterStateSnapshot,
  type UrlTree,
  provideRouter,
} from '@angular/router';
import { authGuard, crmUserGuard } from './auth.guard';
import { AuthService } from './auth.service';

const ROUTE = {} as ActivatedRouteSnapshot;
const STATE = {} as RouterStateSnapshot;

/** Guard behaviour (FE-ADR-005 §2/§4/§P6). */
describe('auth guards', () => {
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let loginSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    // Spy the redirect rather than letting jsdom attempt a navigation.
    loginSpy = vi.spyOn(AuthService.prototype, 'login').mockImplementation(() => undefined);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
    loginSpy.mockRestore();
  });

  function signIn(roles: readonly string[]): void {
    auth.loadSession().subscribe();
    httpMock
      .expectOne('/api/session/me')
      .flush({ authenticated: true, username: 'ayilmaz', subject: 'sub-1', roles });
  }

  it('lets an authenticated session through without issuing any HTTP call', () => {
    signIn(['crm-user']);
    const result = TestBed.runInInjectionContext(() => authGuard(ROUTE, STATE));
    expect(result).toBe(true);
    expect(loginSpy).not.toHaveBeenCalled();
    // httpMock.verify() in afterEach proves the guard itself probed nothing:
    // it reads the signal, it does not call /api/session/me.
  });

  /**
   * Signed out for ANY reason — never signed in, expired, or having just
   * confirmed the sign-out dialog — goes to Keycloak. There is no in-app landing
   * page to route to any more: sign-out itself ends on Keycloak's form, so this
   * is the only destination and the Back button cannot produce a different one.
   */
  it('starts a full-page login redirect and cancels navigation when signed out', () => {
    const result = TestBed.runInInjectionContext(() => authGuard(ROUTE, STATE));
    expect(result).toBe(false); // navigation cancelled; browser is leaving
    expect(loginSpy).toHaveBeenCalledOnce();
  });

  it('routes an authenticated session lacking crm-user to access-denied, NOT to login', () => {
    signIn(['some-other-role']);
    const result = TestBed.runInInjectionContext(() => crmUserGuard(ROUTE, STATE));
    const router = TestBed.inject(Router);
    expect(router.serializeUrl(result as UrlTree)).toBe('/access-denied');
    // Re-authenticating cannot add a role, so a login redirect would loop.
    expect(loginSpy).not.toHaveBeenCalled();
  });

  it('lets a crm-user session reach the feature', () => {
    signIn(['crm-user']);
    expect(TestBed.runInInjectionContext(() => crmUserGuard(ROUTE, STATE))).toBe(true);
  });
});
