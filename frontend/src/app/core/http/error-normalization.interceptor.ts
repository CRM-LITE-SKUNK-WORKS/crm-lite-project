import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { type ApiError, normalizeHttpError } from './api-error';

/**
 * INNERMOST interceptor (FE-ADR-008 §5): normalizes every `HttpErrorResponse`
 * into a typed {@link ApiError} and rethrows THAT. Sitting closest to the
 * backend, it guarantees no other layer ever sees a raw `HttpErrorResponse`, and
 * that raw backend English is logged but never rendered (FE-ADR-008 §2).
 */
export const errorNormalizationInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const apiError = normalizeHttpError(error);
        logDiagnostics(req.method, req.urlWithParams, apiError);
        return throwError(() => apiError);
      }
      // Not an HTTP failure (should not originate from HttpClient) — pass through.
      return throwError(() => error);
    }),
  );

/** FE-ADR-008 §2: the raw backend text is logged for developers, never shown. */
function logDiagnostics(method: string, url: string, error: ApiError): void {
  const line = `[http] ${method} ${url} -> ${error.status} ${error.messageKey}`;
  const detail = {
    path: error.path,
    backendText: error.backendText,
    fieldErrors: error.fieldErrors,
  };
  if (error.status >= 500 || error.status === 0) {
    console.error(line, detail);
  } else {
    console.warn(line, detail);
  }
}
