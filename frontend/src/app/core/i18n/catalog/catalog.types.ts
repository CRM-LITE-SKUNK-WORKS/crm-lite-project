import type { Language } from '../language';

/** One catalogue entry: the same key resolved in every supported language.
 *  The shape makes "exists in EN but missing in TR" a compile error
 *  (FE-ADR-012 §g). */
export type Translation = Record<Language, string>;

export type Catalog = Record<string, Translation>;
