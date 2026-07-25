import { Injectable, computed, signal } from '@angular/core';
import { CATALOG } from './catalog';
import type { Catalog } from './catalog';
import { DEFAULT_LANGUAGE, LANGUAGE_STORAGE_KEY, isLanguage, type Language } from './language';

const GENERIC_KEY = 'UI-ERROR-GENERIC';

/**
 * Runtime i18n (FE-ADR-012 §a). The active language is a signal, so every
 * template that reads a translation re-evaluates the instant the language
 * changes — AC-LANG-01-02 ("instantly") with no reload, and no external i18n
 * library. Works under zoneless change detection (FE-ADR-006 §7).
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly _lang = signal<Language>(this.readStoredLanguage());

  /** The active language (read-only signal). */
  readonly lang = this._lang.asReadonly();

  /** Whole catalogue resolved to the active language — handy for bulk lookups. */
  readonly dictionary = computed<Record<string, string>>(() => {
    const lang = this._lang();
    const out: Record<string, string> = {};
    for (const key of Object.keys(CATALOG)) {
      out[key] = (CATALOG as Catalog)[key][lang];
    }
    return out;
  });

  /**
   * Resolve a key to text in the active language. Reads the `_lang` signal, so
   * callers in templates stay reactive. Unknown key → generic text + a console
   * warning, never the raw key and never silently swallowed (FE-ADR-008 §3).
   *
   * Parameterized texts (FE-ADR-012 §h, approved 2026-07-25): catalogue entries
   * may carry NAMED placeholders (`"{from}–{to} of {total}"`) — never positional
   * ones, because TR/EN word order differs. `params` values replace them. A
   * placeholder with no matching param is LEFT AS-IS plus a console warning
   * (visible to developers, less harmful to users than degrading the whole
   * string — the FE-ADR-008 §3 stance). No ICU/plural engine, deliberately.
   */
  translate(key: string, params?: Record<string, string | number>): string {
    const entry = (CATALOG as Catalog)[key];
    if (!entry) {
      console.warn(`[i18n] missing translation key: ${key}`);
      return (CATALOG as Catalog)[GENERIC_KEY][this._lang()];
    }
    const text = entry[this._lang()];
    if (!params) {
      return text;
    }
    return text.replace(/\{(\w+)\}/g, (placeholder, name: string) => {
      const value = params[name];
      if (value === undefined) {
        console.warn(`[i18n] ${key}: no value for placeholder ${placeholder}`);
        return placeholder;
      }
      return String(value);
    });
  }

  /** Change the UI language and persist it (AC-LANG-01-03: kept for the session
   *  and beyond via localStorage; FE-ADR-012 §d). */
  setLanguage(lang: Language): void {
    if (lang === this._lang()) {
      return;
    }
    this._lang.set(lang);
    try {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, lang);
    } catch {
      // Private-mode / storage-disabled: language still switches for this session.
    }
  }

  /**
   * The value to append as `?ui_locales=` on the Keycloak login redirect so the
   * login page opens in the same language (AC-LANG-01-01 "including Login";
   * FE-ADR-012 §d). Consumed later by the auth redirect (not built in this phase).
   */
  keycloakUiLocales(): Language {
    return this._lang();
  }

  private readStoredLanguage(): Language {
    let stored: string | null;
    try {
      stored = localStorage.getItem(LANGUAGE_STORAGE_KEY);
    } catch {
      stored = null;
    }
    // Default is fixed to English; navigator.language is deliberately ignored
    // (AC-LANG-01-01 / FE-ADR-012 §d).
    return isLanguage(stored) ? stored : DEFAULT_LANGUAGE;
  }
}
