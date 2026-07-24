import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { type Observable, catchError, finalize, of, tap, timeout } from 'rxjs';
import { SKIP_AUTH_REDIRECT } from '../http/http-context';
import type { SessionResponse } from './session.model';

/** All relative (FE-ADR-004 §1): the browser stays on its own origin, the dev
 *  proxy / nginx forward these to the gateway. No host, ever. */
const SESSION_ME_URL = '/api/session/me';
const LOGIN_URL = '/oauth2/authorization/keycloak';
const LOGOUT_URL = '/logout';
/** Guard-free landing page for a completed sign-out (see `logout`). */
const SIGNED_OUT_URL = '/signed-out';

/** Upper bound on how long sign-out waits for the logout response before
 *  navigating anyway. See `logout` for why waiting can never be unbounded. */
const LOGOUT_TIMEOUT_MS = 3000;

/**
 * The single source of authentication state (FE-ADR-005 §1). Exposes reactive
 * signals derived from `GET /api/session/me` and the two navigations the
 * frontend is allowed to trigger — a full-page login redirect (§2) and a
 * CSRF-protected logout (§3).
 *
 * It NEVER stores, reads, parses or logs a token (FE-ADR-005 §P3): there is no
 * token in the browser to begin with. Roles come only from the session probe,
 * never from a decoded JWT.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _session = signal<SessionResponse | null>(null);
  private redirecting = false;

  /** The last known session, or `null` when signed out / not yet probed. */
  readonly session = this._session.asReadonly();
  readonly isAuthenticated = computed(() => this._session()?.authenticated === true);
  readonly username = computed<string | null>(() => this._session()?.username ?? null);
  readonly roles = computed<readonly string[]>(() => this._session()?.roles ?? []);

  /** UX affordance only — the gateway enforces roles independently (FE-ADR-005 §P6). */
  hasRole(role: string): boolean {
    return this.roles().includes(role);
  }

  /**
   * Probe the session at startup and after every login/logout transition
   * (FE-ADR-005 §1). Any failure (incl. an anonymous `401`) resolves to
   * `null` = signed out; it does NOT auto-redirect, because the probe's purpose
   * is to observe state (it sets {@link SKIP_AUTH_REDIRECT}).
   */
  loadSession(): Observable<SessionResponse | null> {
    return this.probe().pipe(
      tap((session) => this._session.set(session)),
      catchError(() => {
        this.markSignedOut();
        return of(null);
      }),
    );
  }

  /**
   * Re-fetch the probe to (re)issue a fresh `XSRF-TOKEN` cookie. Used by the CSRF
   * retry path (FE-ADR-005 §4); errors propagate so the caller can react.
   */
  refreshSession(): Observable<SessionResponse> {
    return this.probe().pipe(tap((session) => this._session.set(session)));
  }

  /**
   * Start login as a FULL-PAGE redirect (FE-ADR-005 §2) — not an XHR. Guarded so
   * a burst of 401s cannot fire multiple navigations. Credentials are typed only
   * on Keycloak's page; the frontend has no login form (§P1).
   */
  login(): void {
    if (this.redirecting) {
      return;
    }
    this.redirecting = true;
    this.markSignedOut();
    window.location.assign(LOGIN_URL);
  }

  /**
   * CSRF-protected logout (FE-ADR-005 §3). Angular's default XSRF interceptor
   * attaches `X-XSRF-TOKEN` (the raw cookie value) — the ONLY form the gateway's
   * `SpaCsrfTokenRequestHandler` accepts, because its `_csrf` parameter path
   * XOR-decodes a value the browser never holds. A navigational form-POST
   * therefore cannot carry a valid token, so logout is an `HttpClient` POST.
   *
   * ⚠️ Two consequences of that, both observed on the running stack (2026-07-24)
   * and recorded in docs/frontend/scope-and-conflicts.md §5.7:
   *
   * 1. The gateway answers `302` to Keycloak `end_session`. An XHR cannot drive
   *    that cross-origin navigation and can **stall** while following it — the
   *    request then neither completes nor errors. Sign-out must therefore never
   *    depend on the response settling, hence the timeout: by the time the
   *    gateway emits its redirect the session is already invalidated, so
   *    abandoning the wait loses nothing.
   * 2. Keycloak's SSO session is NOT reliably terminated this way. Landing on
   *    `/` would hit the guard, bounce to Keycloak, be silently re-authenticated
   *    by the surviving SSO session and return to the app — making sign-out look
   *    like it did nothing. So we land on the guard-free `/signed-out` page,
   *    which states the outcome and offers an explicit sign-in link.
   *
   * Fully terminating SSO needs the backend affordance in §5.7 (a).
   */
  logout(): void {
    this.http
      .post(LOGOUT_URL, null, { observe: 'response', responseType: 'text' })
      .pipe(
        timeout(LOGOUT_TIMEOUT_MS),
        finalize(() => {
          this.markSignedOut();
          window.location.assign(SIGNED_OUT_URL);
        }),
      )
      .subscribe({ error: () => undefined });
  }

  /** Clear cached auth state. Public so the interceptors can mark sign-out. */
  markSignedOut(): void {
    this._session.set(null);
  }

  private probe(): Observable<SessionResponse> {
    return this.http.get<SessionResponse>(SESSION_ME_URL, {
      context: new HttpContext().set(SKIP_AUTH_REDIRECT, true),
    });
  }
}
