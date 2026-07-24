import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CATALOG } from '../i18n/catalog';
import { AuthService } from '../auth/auth.service';
import {
  FIELD_FALLBACK_KEY_VALUES,
  type ApiError,
  type ApiFieldError,
  isApiError,
  normalizeHttpError,
  parseFieldPath,
} from './api-error';
import { authErrorInterceptor } from './auth-error.interceptor';
import { errorNormalizationInterceptor } from './error-normalization.interceptor';
import { fieldErrorsAt, matchFieldErrors } from './field-errors';

/**
 * Error normalization + auth interceptor behaviour (FE-ADR-005 §4, FE-ADR-008).
 * The contract shapes flushed here are copied from the backend envelopes
 * (ErrorResponse.java / GatewayErrorResponse.java, verified 2026-07-24).
 */
describe('normalizeHttpError', () => {
  it('keeps the messageKey and carries no field errors for a business error', () => {
    const apiError = normalizeHttpError(
      new HttpErrorResponse({
        status: 409,
        statusText: 'Conflict',
        url: '/api/customers',
        error: {
          messageKey: 'MSG-CUST-DUP-NATID',
          message: 'A customer already exists with this Nationality ID',
          path: '/api/customers',
          validationErrors: null,
        },
      }),
    );
    expect(apiError.kind).toBe('api-error');
    expect(apiError.status).toBe(409);
    expect(apiError.messageKey).toBe('MSG-CUST-DUP-NATID');
    expect(apiError.fieldErrors).toEqual([]);
  });

  it('classifies validationErrors: catalogue keys pass through, raw text is downgraded', () => {
    const apiError = normalizeHttpError(
      new HttpErrorResponse({
        status: 400,
        statusText: 'Bad Request',
        error: {
          messageKey: 'MSG-VALIDATION-ERROR',
          message: 'Request validation failed',
          validationErrors: {
            'contactMedium.email': 'MSG-VAL-EMAIL', // a catalogue key
            cityId: 'cityId is required', // raw English
          },
        },
      }),
    );
    expect(apiError.messageKey).toBe('MSG-VALIDATION-ERROR');
    expect(apiError.fieldErrors).toEqual([
      {
        field: 'contactMedium.email',
        messageKey: 'MSG-VAL-EMAIL',
        backendText: 'MSG-VAL-EMAIL',
        leaf: 'email',
        scope: 'contactMedium',
        index: null,
        structural: false,
      },
      {
        // raw English -> per-field frontend key, never the raw text
        field: 'cityId',
        messageKey: 'UI-VAL-SELECT-REQUIRED',
        backendText: 'cityId is required',
        leaf: 'cityId',
        scope: null,
        index: null,
        structural: false,
      },
    ]);
  });

  it('falls back to a generic key when the body is not a backend envelope', () => {
    const apiError = normalizeHttpError(
      new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }),
    );
    expect(apiError.status).toBe(0);
    expect(apiError.messageKey).toBe('MSG-INTERNAL-ERROR');
    expect(apiError.backendText).toBeNull();
  });
});

describe('core http interceptors', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  // Spy the method the interceptor calls, not window.location (jsdom navigation
  // is not implemented): this asserts the redirect decision without navigating.
  let loginSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    loginSpy = vi.spyOn(AuthService.prototype, 'login').mockImplementation(() => undefined);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authErrorInterceptor, errorNormalizationInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    loginSpy.mockRestore();
  });

  it('rethrows a normalized ApiError (never a raw HttpErrorResponse)', () => {
    let caught: unknown;
    http.get('/api/customers').subscribe({ error: (e: unknown) => (caught = e) });
    httpMock.expectOne('/api/customers').flush(
      {
        messageKey: 'MSG-CUST-DUP-NATID',
        message: 'x',
        path: '/api/customers',
        validationErrors: null,
      },
      { status: 409, statusText: 'Conflict' },
    );
    expect(isApiError(caught)).toBe(true);
    expect((caught as ApiError).messageKey).toBe('MSG-CUST-DUP-NATID');
    expect(loginSpy).not.toHaveBeenCalled();
  });

  it('401 MSG-AUTH-UNAUTHORIZED starts the login redirect', () => {
    let caught: unknown;
    http.get('/api/customers').subscribe({ error: (e: unknown) => (caught = e) });
    httpMock
      .expectOne('/api/customers')
      .flush(
        { messageKey: 'MSG-AUTH-UNAUTHORIZED', message: 'x', path: '/api/customers' },
        { status: 401, statusText: 'Unauthorized' },
      );
    expect(loginSpy).toHaveBeenCalledOnce();
    expect(isApiError(caught)).toBe(true);
  });

  it('403 MSG-AUTH-CSRF-REJECTED refreshes the probe and retries once', () => {
    let result: unknown;
    http.post('/api/customers', {}).subscribe({ next: (r: unknown) => (result = r) });

    httpMock
      .expectOne((r) => r.url === '/api/customers' && r.method === 'POST')
      .flush(
        { messageKey: 'MSG-AUTH-CSRF-REJECTED', message: 'x', path: '/api/customers' },
        { status: 403, statusText: 'Forbidden' },
      );

    httpMock
      .expectOne('/api/session/me')
      .flush({ authenticated: true, username: 'ayilmaz', subject: 's', roles: ['crm-user'] });

    httpMock
      .expectOne((r) => r.url === '/api/customers' && r.method === 'POST')
      .flush({ ok: true });

    expect(result).toEqual({ ok: true });
    expect(loginSpy).not.toHaveBeenCalled();
  });

  it('403 MSG-AUTH-FORBIDDEN neither retries nor redirects', () => {
    let caught: unknown;
    http.delete('/api/customers/1').subscribe({ error: (e: unknown) => (caught = e) });
    httpMock
      .expectOne('/api/customers/1')
      .flush(
        { messageKey: 'MSG-AUTH-FORBIDDEN', message: 'x', path: '/api/customers/1' },
        { status: 403, statusText: 'Forbidden' },
      );
    expect(isApiError(caught)).toBe(true);
    expect((caught as ApiError).messageKey).toBe('MSG-AUTH-FORBIDDEN');
    expect(loginSpy).not.toHaveBeenCalled();
    // afterEach httpMock.verify() asserts NO retry request was made.
  });
});

describe('parseFieldPath', () => {
  it('handles every path convention the backend produces', () => {
    // body @Valid (nested), @RequestParam (bare), indexed list element, section
    expect(parseFieldPath('demographic.firstName')).toEqual({
      leaf: 'firstName',
      scope: 'demographic',
      index: null,
    });
    expect(parseFieldPath('firstName')).toEqual({ leaf: 'firstName', scope: null, index: null });
    expect(parseFieldPath('addresses[0].cityId')).toEqual({
      leaf: 'cityId',
      scope: 'addresses',
      index: 0,
    });
    expect(parseFieldPath('addresses[12].street')).toEqual({
      leaf: 'street',
      scope: 'addresses',
      index: 12,
    });
    expect(parseFieldPath('addresses')).toEqual({ leaf: 'addresses', scope: null, index: null });
  });
});

describe('field error binding', () => {
  /** Shaped exactly as normalizeFieldErrors would produce them. */
  function normalize(map: Record<string, string>): readonly ApiFieldError[] {
    return normalizeHttpError(
      new HttpErrorResponse({
        status: 400,
        error: { messageKey: 'MSG-VALIDATION-ERROR', validationErrors: map },
      }),
    ).fieldErrors;
  }

  it('marks whole-section paths structural so they cannot be mistaken for controls', () => {
    const errors = normalize({
      addresses: 'at least one address is required',
      'addresses[0].cityId': 'cityId is required',
    });
    expect(errors[0]).toMatchObject({
      leaf: 'addresses',
      structural: true,
      messageKey: 'UI-VAL-ADDRESS-REQUIRED',
    });
    // an indexed child is a real control, NOT structural
    expect(errors[1]).toMatchObject({ leaf: 'cityId', index: 0, structural: false });
  });

  it('binds nested and bare paths to the same control name', () => {
    const nested = matchFieldErrors(normalize({ 'demographic.firstName': 'MSG-VAL-NAME' }), [
      'firstName',
    ]);
    const bare = matchFieldErrors(normalize({ firstName: 'MSG-VAL-NAME' }), ['firstName']);
    expect(nested.matched.get('firstName')?.messageKey).toBe('MSG-VAL-NAME');
    expect(bare.matched.get('firstName')?.messageKey).toBe('MSG-VAL-NAME');
    expect(nested.unmatched).toEqual([]);
  });

  it('returns section-level errors as unmatched so they are never silently dropped', () => {
    const { matched, unmatched } = matchFieldErrors(
      normalize({
        'contactMedium.email': 'MSG-VAL-EMAIL',
        contactMedium: 'contactMedium is required',
      }),
      ['email'],
    );
    expect(matched.get('email')?.messageKey).toBe('MSG-VAL-EMAIL');
    expect(unmatched.map((e) => e.leaf)).toEqual(['contactMedium']);
    expect(unmatched[0].messageKey).toBe('UI-VAL-SECTION-INCOMPLETE');
  });

  it('never binds one error to two controls', () => {
    const { matched, unmatched } = matchFieldErrors(normalize({ 'addresses[0].street': 'x' }), [
      'street',
      'street',
    ]);
    expect(matched.size).toBe(1);
    expect(unmatched).toEqual([]);
  });

  it('separates repeated-section rows by index', () => {
    const errors = normalize({
      'addresses[0].cityId': 'cityId is required',
      'addresses[1].street': 'street is required',
    });
    expect(fieldErrorsAt(errors, 'addresses', 0).map((e) => e.leaf)).toEqual(['cityId']);
    expect(fieldErrorsAt(errors, 'addresses', 1).map((e) => e.leaf)).toEqual(['street']);
  });

  it('keeps the second row visible when two rows fail the SAME field', () => {
    // A naive leaf lookup would bind addresses[0].cityId and lose addresses[1].cityId.
    const errors = normalize({
      'addresses[0].cityId': 'cityId is required',
      'addresses[1].cityId': 'cityId is required',
    });
    const { matched, unmatched } = matchFieldErrors(errors, ['cityId']);
    expect(matched.get('cityId')?.index).toBe(0);
    expect(unmatched.map((e) => e.index)).toEqual([1]); // surfaced, never swallowed
  });

  it('warns for an unmapped field but stays silent for a deliberate generic', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    normalize({ street: 'street is required' }); // deliberate choice -> quiet
    expect(warn).not.toHaveBeenCalled();

    normalize({ someNewBackendField: 'must not be null' }); // unmapped gap -> loud
    expect(warn).toHaveBeenCalledOnce();

    warn.mockRestore();
  });

  it('every per-field fallback key exists in the catalogue', () => {
    for (const key of FIELD_FALLBACK_KEY_VALUES) {
      expect(Object.prototype.hasOwnProperty.call(CATALOG, key), `missing ${key}`).toBe(true);
    }
  });
});
