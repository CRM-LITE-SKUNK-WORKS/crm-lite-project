import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { EnvironmentProviders } from '@angular/core';
import { authErrorInterceptor } from './auth-error.interceptor';
import { errorNormalizationInterceptor } from './error-normalization.interceptor';

/**
 * Core HTTP wiring for the app config.
 *
 * XSRF is Angular's DEFAULT and is deliberately NOT customized (FE-ADR-004 §5):
 * `provideHttpClient()` already reads the `XSRF-TOKEN` cookie and echoes it as
 * the `X-XSRF-TOKEN` header on same-origin unsafe methods — exactly the gateway's
 * convention (ADR-008). No cookie/header name overrides, no manual header code.
 *
 * Interceptor order is load-bearing (FE-ADR-008 §5). `withInterceptors` wraps
 * left→right, so on the way OUT the list runs right→left:
 *   [authError (outer), errorNormalization (inner)]
 * → a failure is first normalized to `ApiError` (inner), then classified by the
 * auth interceptor (outer). Reversing them would hand the auth interceptor a raw
 * `HttpErrorResponse` it is not written to read.
 */
export function provideCoreHttp(): EnvironmentProviders {
  return provideHttpClient(withInterceptors([authErrorInterceptor, errorNormalizationInterceptor]));
}
