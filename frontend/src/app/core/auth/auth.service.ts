import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { type Observable, catchError, of, tap, timeout } from 'rxjs';
import { SKIP_AUTH_REDIRECT } from '../http/http-context';
import type { SessionResponse } from './session.model';

/** All relative (FE-ADR-004 §1): the browser stays on its own origin, the dev
 *  proxy / nginx forward these to the gateway. No host, ever. */
const SESSION_ME_URL = '/api/session/me';
const LOGIN_URL = '/oauth2/authorization/keycloak';
const LOGOUT_URL = '/logout';
/** Where a sign-out that could not reach the gateway lands (see `logout`). */
const HOME_URL = '/';

/** Upper bound on how long sign-out waits for the logout response before
 *  falling back to a local-only sign-out. See `logout` for why. */
const LOGOUT_TIMEOUT_MS = 3000;

/** Body of `POST /logout` (see `logout`): the URL to navigate the browser to
 *  (top-level) to finish RP-initiated logout at Keycloak. */
interface LogoutResponse {
  readonly logoutUrl: string;
}

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
   *
   * `replace`, not `assign`: the entry being left is a page the user could not
   * see anyway (the guard just refused it, or an API call just 401'd on it).
   * Replacing it keeps that dead entry out of the history stack, so Back from
   * Keycloak's login form leaves the application instead of bouncing off a
   * screen that will only redirect again.
   */
  login(): void {
    if (this.redirecting) {
      return;
    }
    this.redirecting = true;
    this.markSignedOut();
    window.location.replace(LOGIN_URL);
  }

  /**
   * CSRF-protected logout (FE-ADR-005 §3). Angular's default XSRF interceptor
   * attaches `X-XSRF-TOKEN` (the raw cookie value) — the ONLY form the gateway's
   * `SpaCsrfTokenRequestHandler` accepts, because its `_csrf` parameter path
   * XOR-decodes a value the browser never holds. A navigational form-POST
   * therefore cannot carry a valid token, so the request itself is an
   * `HttpClient` POST (an XHR).
   *
   * Called only after the user confirms the dialog in `Shell` — by the time this
   * runs, ending the session is a decision already taken.
   *
   * The gateway invalidates the local session synchronously and responds with
   * `{ logoutUrl }` — Keycloak's `end_session` endpoint (RP-initiated logout).
   * Navigating to that `logoutUrl` (see {@link leaveForSignOut}) is this file's
   * one deliberate exception to the "no host, ever" convention (FE-ADR-004 §1):
   * the URL is server-supplied and inherently cross-origin (Keycloak), not
   * something frontend code constructs. A real top-level navigation is needed —
   * an XHR cannot drive Keycloak's own redirect chain and clear its SSO cookie
   * (see docs/frontend/scope-and-conflicts.md §5.7) — after which Keycloak
   * redirects the browser onto its own sign-in form.
   *
   * The timeout is a defensive fallback only, for a genuine network hang. It
   * reloads `/` rather than claiming success: if the POST never landed, the
   * gateway session may well be alive, and a page announcing a sign-out that did
   * not happen is worse than showing the truth. A reload re-probes and either
   * lands on Keycloak (session gone, via the guard) or back in the app with the
   * sign-out button still there to retry.
   */
  logout(): void {
    this.http
      .post<LogoutResponse>(LOGOUT_URL, null)
      .pipe(
        timeout(LOGOUT_TIMEOUT_MS),
        tap((response) => this.leaveForSignOut(response.logoutUrl)),
        catchError(() => {
          this.leaveForSignOut(HOME_URL);
          return of(null);
        }),
      )
      .subscribe();
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

  /**
   * The last thing this document does before the browser leaves for sign-out.
   *
   * `replace`, not `assign`, is the point: `assign` PUSHES, leaving the fully
   * rendered customer screen behind as the previous history entry. Pressing Back
   * later restores it from the back/forward cache — a bfcache restore re-runs no
   * initializer, no guard and no HTTP call, so real customer data reappears on
   * screen for a user who has signed out. `replace` overwrites that entry, so it
   * is not in the history stack to return to. (The remaining, older entries are
   * covered by `provideSessionRestoreCheck` and `authGuard`.)
   */
  private leaveForSignOut(target: string): void {
    this.markSignedOut();
    window.location.replace(target);
  }
}
