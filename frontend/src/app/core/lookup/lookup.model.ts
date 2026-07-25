/**
 * Shared reference-data contracts: the GNL_ST / GNL_TP catalogs owned by
 * lookup-service (ADR-002, docs/api/shared-lookup-service.md) and the
 * city/district cascade owned by customer-service (§ Reference data).
 *
 * These live in `core/` because they are cross-feature, app-wide reference data
 * (FE-ADR-003 §Decision, FE-ADR-006 §1 designate the lookup cache to core/), not
 * a customer- or account-specific concern.
 */

/** Status catalog row (`GET /api/lookups/statuses`). Verified against
 *  shared-lookup-service.md response shape. */
export interface StatusLookup {
  /** Immutable, workbook-enumerated id — may be persisted as an external ref. */
  readonly id: number;
  readonly shortCode: string;
  readonly name: string;
  /** e.g. `GENERAL`. Consumers MUST validate the domain, not just the code. */
  readonly statusDomain: string;
}

/** Type catalog row (`GET /api/lookups/types`). Verified against
 *  shared-lookup-service.md response shape. */
export interface TypeLookup {
  readonly id: number;
  readonly shortCode: string;
  readonly name: string;
  /** e.g. `GENDER`, `PARTY_TYPE`. Consumers MUST validate the domain. */
  readonly typeDomain: string;
}

/**
 * City for the address cascade (`GET /api/cities`).
 *
 * ✅ **VERIFIED against the running stack's Swagger (city-controller,
 * 2026-07-25, scope §4.18):** the field is **`cityId`**, NOT a generic `id` —
 * the earlier `{id, name}` assumption was wrong and was corrected here before
 * any screen consumed it.
 */
export interface City {
  readonly cityId: number;
  readonly name: string;
}

/**
 * District for the address cascade (`GET /api/cities/{cityId}/districts`).
 * ✅ VERIFIED via Swagger (2026-07-25): `{ districtId, name }`.
 */
export interface District {
  readonly districtId: number;
  readonly name: string;
}
