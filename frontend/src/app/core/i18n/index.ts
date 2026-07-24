/** Public surface of the i18n subsystem (FE-ADR-012). */
export { I18nService } from './i18n.service';
export { TranslatePipe } from './translate.pipe';
export {
  DEFAULT_LANGUAGE,
  LANGUAGES,
  LANGUAGE_STORAGE_KEY,
  isLanguage,
  type Language,
} from './language';
export { CATALOG, type TranslationKey } from './catalog';
