import type { Catalog } from './catalog.types';
import { LABELS } from './labels';
import { MESSAGES } from './messages';
import { UI } from './ui';

/**
 * The single merged catalogue. Two catalogues (contract: LBL/MSG keys, and UI:
 * UI keys) resolve through one lookup — no second mechanism, no fallback chain
 * between them (FE-ADR-012 §c). The prefix says which catalogue a key lives in.
 */
export const CATALOG = {
  ...LABELS,
  ...MESSAGES,
  ...UI,
} satisfies Catalog;

/** Every known translation key, derived from the catalogue itself so a typo in a
 *  template key is a compile error where the key type is used (FE-ADR-002 §2). */
export type TranslationKey = keyof typeof CATALOG;

export { LABELS, MESSAGES, UI };
export type { Translation, Catalog } from './catalog.types';
