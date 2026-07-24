import { inject } from '@angular/core';
import { type CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** KR-8's single operator role, required by the gateway for every /api/** call. */
const CRM_USER_ROLE = 'crm-user';

/**
 * UI-side authentication gate (FE-ADR-005 §2).
 *
 * Reads the AuthService SIGNAL and nothing else — it issues no HTTP call. The
 * session was already loaded once by the app initializer (§1), so this is a
 * synchronous state check, not a probe.
 *
 * Deliberately SEPARATE from the API-401 path in
 * `core/http/auth-error.interceptor.ts`: that one reacts to a request that has
 * already failed, this one stops a route from activating in the first place.
 * They share only the final step — `AuthService.login()`, a FULL-PAGE navigation,
 * because an OAuth2 authorization-code flow cannot be driven by an XHR.
 *
 * ⚠️ FE-ADR-005 §P6: this is a UX affordance, NEVER a security boundary. The
 * gateway and every resource server enforce authentication and the role
 * independently (ADR-009 §2). Nothing here makes an API call safe.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) {
    return true;
  }
  // Full-page redirect to Keycloak; cancel this navigation, the browser is leaving.
  auth.login();
  return false;
};

/**
 * Authenticated, but the session lacks the operator role → the access-denied
 * page. It deliberately does NOT redirect to login: signing in again with the
 * same account cannot add a role, so bouncing the user through Keycloak would be
 * a loop that explains nothing (FE-ADR-005 §4 makes the same distinction between
 * `MSG-AUTH-FORBIDDEN` and `MSG-AUTH-UNAUTHORIZED`).
 */
export const crmUserGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.hasRole(CRM_USER_ROLE) ? true : router.createUrlTree(['/access-denied']);
};
