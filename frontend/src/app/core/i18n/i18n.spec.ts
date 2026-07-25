import { TestBed } from '@angular/core/testing';
import { CATALOG } from './catalog';
import { I18nService } from './i18n.service';
import { LANGUAGE_STORAGE_KEY, LANGUAGES } from './language';

/**
 * Catalogue-integrity + i18n behaviour (FE-ADR-008 §Consequences: "a
 * catalogue-integrity unit test closes that gap", decided 2026-07-23).
 */
describe('i18n catalogue', () => {
  it('every key has a non-empty string in every supported language', () => {
    for (const [key, entry] of Object.entries(CATALOG)) {
      for (const lang of LANGUAGES) {
        expect(typeof entry[lang], `${key}.${lang}`).toBe('string');
        expect(entry[lang].length, `${key}.${lang} is empty`).toBeGreaterThan(0);
      }
    }
  });

  it('EN and TR of every key use the SAME placeholder set (FE-ADR-012 §h)', () => {
    const placeholdersOf = (text: string) =>
      [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
    for (const [key, entry] of Object.entries(CATALOG)) {
      expect(placeholdersOf(entry.en), `placeholder mismatch in ${key}`).toEqual(
        placeholdersOf(entry.tr),
      );
    }
  });

  it('includes every backend messageKey documented in the API contract', () => {
    // These are the keys the backend can return (docs/api/*.md +
    // functional-requirements.md). A key added server-side without a translation
    // must turn this red rather than surface a generic message in production.
    const backendKeys = [
      'MSG-CUST-NOT-FOUND',
      'MSG-CUST-DUP-NATID',
      'MSG-CUST-HAS-PRODUCTS',
      'MSG-CUST-NATID-VERIFICATION-FAILED',
      'MSG-MERNIS-UNAVAILABLE',
      'MSG-ADDR-IN-USE',
      'MSG-ADDR-LAST-DELETE',
      'MSG-ADDR-PRIMARY-DELETE',
      'MSG-VAL-NATID',
      'MSG-VAL-BIRTHDATE',
      'MSG-VAL-AGE-MIN',
      'MSG-VAL-NAME',
      'MSG-VAL-EMAIL',
      'MSG-VAL-PHONE',
      'MSG-VALIDATION-ERROR',
      'MSG-INTERNAL-ERROR',
      'MSG-SERVICE-UNAVAILABLE',
      'MSG-FEATURE-NOT-IMPLEMENTED',
      'MSG-LOOKUP-NOT-FOUND',
      'MSG-AUTH-UNAUTHORIZED',
      'MSG-AUTH-FORBIDDEN',
      'MSG-AUTH-CSRF-REJECTED',
      // account-service (ADR-013 §6, account-service.md §Status/message matrix) —
      // keys the backend can return. Frontend-only MSG-ACCT-DELETE-CONFIRM /
      // MSG-ACCT-DELETED are deliberately not listed (never returned by backend).
      'MSG-ACCT-HAS-PRODUCTS',
      'MSG-ACCT-NOT-FOUND',
      'MSG-ACCT-NOT-ACTIVE',
      'MSG-ACCT-IMMUTABLE-FIELD',
      'MSG-ACCT-DUP-NUMBER',
      'MSG-ACCT-NUMBER-CAPACITY-EXCEEDED',
    ];
    for (const key of backendKeys) {
      expect(Object.prototype.hasOwnProperty.call(CATALOG, key), `missing ${key}`).toBe(true);
    }
  });
});

describe('I18nService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('defaults to English (navigator.language ignored)', () => {
    const i18n = TestBed.inject(I18nService);
    expect(i18n.lang()).toBe('en');
    expect(i18n.translate('LBL-SEARCH')).toBe('Search');
  });

  it('switches language instantly and persists the choice', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.setLanguage('tr');
    expect(i18n.lang()).toBe('tr');
    expect(i18n.translate('LBL-SEARCH')).toBe('Ara');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('tr');
  });

  it('interpolates named placeholders, TR reordering freely (FE-ADR-012 §h)', () => {
    const i18n = TestBed.inject(I18nService);
    expect(i18n.translate('UI-PAGINATION-RANGE', { from: 1, to: 20, total: 137 })).toBe(
      '1–20 of 137',
    );
    i18n.setLanguage('tr');
    expect(i18n.translate('UI-PAGINATION-RANGE', { from: 1, to: 20, total: 137 })).toBe(
      '137 kayıttan 1–20',
    );
  });

  it('leaves a missing placeholder as-is and warns (never degrades the whole string)', () => {
    const i18n = TestBed.inject(I18nService);
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    expect(i18n.translate('UI-PAGINATION-RANGE', { from: 1, to: 20 })).toBe('1–20 of {total}');
    expect(warn).toHaveBeenCalledOnce();
    warn.mockRestore();
  });

  it('falls back to generic text and warns on an unknown key', () => {
    const i18n = TestBed.inject(I18nService);
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const text = i18n.translate('MSG-DOES-NOT-EXIST');
    expect(text).toBe(i18n.translate('UI-ERROR-GENERIC'));
    expect(warn).toHaveBeenCalledOnce();
    warn.mockRestore();
  });
});
