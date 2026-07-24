import { HttpContextToken } from '@angular/common/http';

/**
 * Per-request flags read by the interceptors (core/http). Using an
 * `HttpContextToken` keeps these out of headers — they never leave the browser.
 */

/**
 * When `true`, a `401 MSG-AUTH-UNAUTHORIZED` on this request does NOT start the
 * login redirect. The session probe (`AuthService.loadSession/refreshSession`)
 * sets it: the probe's whole job is to *report* auth state, so it must be able
 * to observe a 401 without the interceptor navigating away (FE-ADR-005 §1/§4).
 */
export const SKIP_AUTH_REDIRECT = new HttpContextToken<boolean>(() => false);

/**
 * Marks a request that has already been retried once after a CSRF refresh, so a
 * second `403 MSG-AUTH-CSRF-REJECTED` cannot start an infinite retry loop
 * (FE-ADR-005 §4: "retry the request **once**").
 */
export const CSRF_RETRIED = new HttpContextToken<boolean>(() => false);
