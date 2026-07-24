import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { isApiError } from './api-error';
import { CSRF_RETRIED, SKIP_AUTH_REDIRECT } from './http-context';

/**
 * OUTER interceptor (FE-ADR-008 §5): runs after normalization, so it classifies
 * the typed {@link isApiError} value, never a raw `HttpErrorResponse`. It handles
 * only the three cross-cutting auth cases (FE-ADR-005 §4), each DISTINCTLY by
 * `messageKey` — status alone is ambiguous (two different 403s):
 *
 *  - 401 `MSG-AUTH-UNAUTHORIZED`  → start the login redirect (unless opted out).
 *  - 403 `MSG-AUTH-CSRF-REJECTED` → refresh the XSRF cookie, retry ONCE.
 *  - 403 `MSG-AUTH-FORBIDDEN`     → role denial: surface as-is, no retry/redirect.
 *
 * Everything else (business 409, validation 400, 503, …) is passed straight
 * through for the feature to present in context.
 */
const MSG_AUTH_UNAUTHORIZED = 'MSG-AUTH-UNAUTHORIZED';
const MSG_AUTH_CSRF_REJECTED = 'MSG-AUTH-CSRF-REJECTED';

export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!isApiError(error)) {
        return throwError(() => error);
      }

      // 401 — no/expired session. The session probe opts out (it reports state).
      if (error.status === 401 && error.messageKey === MSG_AUTH_UNAUTHORIZED) {
        auth.markSignedOut();
        if (!req.context.get(SKIP_AUTH_REDIRECT)) {
          auth.login();
        }
        return throwError(() => error);
      }

      // 403 CSRF — refresh the XSRF-TOKEN cookie via the probe, then retry once.
      if (
        error.status === 403 &&
        error.messageKey === MSG_AUTH_CSRF_REJECTED &&
        !req.context.get(CSRF_RETRIED)
      ) {
        return auth.refreshSession().pipe(
          switchMap(() => next(req.clone({ context: req.context.set(CSRF_RETRIED, true) }))),
          catchError((retryError: unknown) => {
            // Refresh (or the single retry) revealed the session is truly gone.
            if (isApiError(retryError) && retryError.status === 401) {
              auth.markSignedOut();
              auth.login();
            }
            return throwError(() => retryError);
          }),
        );
      }

      // 403 role denial and all other errors — the feature decides presentation.
      return throwError(() => error);
    }),
  );
};
