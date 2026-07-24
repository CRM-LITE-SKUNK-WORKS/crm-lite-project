/**
 * Supported UI languages (FR-LANG-01 / AC-LANG-01-01: TR + EN only, default EN).
 * The default is fixed to English by analyst requirement — navigator.language is
 * deliberately NOT consulted (FE-ADR-012 §d).
 */
export type Language = 'en' | 'tr';

export const LANGUAGES: readonly Language[] = ['en', 'tr'] as const;

export const DEFAULT_LANGUAGE: Language = 'en';

/** localStorage key. A language preference is a non-sensitive display setting —
 *  NOT an exception to FE-ADR-005 §P3 (which concerns credentials/tokens). */
export const LANGUAGE_STORAGE_KEY = 'crm.lang';

export function isLanguage(value: unknown): value is Language {
  return value === 'en' || value === 'tr';
}
