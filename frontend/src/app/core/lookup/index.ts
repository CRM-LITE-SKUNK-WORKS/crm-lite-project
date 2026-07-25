/** Public surface of core/lookup — shared reference data (ADR-002, FE-ADR-006 §1). */
export type { StatusLookup, TypeLookup, City, District } from './lookup.model';
export { LookupApiService } from './lookup-api.service';
export { LookupCacheService } from './lookup-cache.service';
