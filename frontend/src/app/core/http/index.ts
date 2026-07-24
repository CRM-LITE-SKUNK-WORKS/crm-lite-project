/** Public surface of core/http. Features import the error type + guard and the
 *  field-binding helpers from here; the app config imports `provideCoreHttp`.
 *  Interceptors are internal. */
export {
  type ApiError,
  type ApiFieldError,
  type BackendErrorEnvelope,
  isApiError,
  parseFieldPath,
} from './api-error';
export { type FieldErrorMatch, fieldErrorsAt, matchFieldErrors } from './field-errors';
export { CSRF_RETRIED, SKIP_AUTH_REDIRECT } from './http-context';
export { provideCoreHttp } from './provide-core-http';
