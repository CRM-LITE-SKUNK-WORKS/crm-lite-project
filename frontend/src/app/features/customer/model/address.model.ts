/**
 * Address sub-module contract (customer-service.md §K).
 *
 * Address and contact are internal modules of the customer aggregate, NOT
 * separate services (ADR-001, FE-ADR-003 §3): their endpoints are nested under
 * the customer.
 */

/**
 * Address create/update body (`POST/PUT /api/customers/{n}/addresses`).
 * Verified against the create JSON (customer-service.md §Atomic create and §K):
 * selects carry IDs (`cityId`/`districtId`), NOT names (scope §2.8).
 *
 * `primary` is only meaningful in the atomic-create address array; on the
 * standalone `POST .../addresses` the first address becomes primary
 * automatically and primacy is otherwise changed via
 * `PATCH .../addresses/{id}/primary`, so it is optional here.
 *
 * NOTE: the Swagger request schema (2026-07-25) also lists a `primaryRequested`
 * property alongside `primary` — a serializer artifact of the backend DTO (a
 * boolean field exposing two accessor names). The documented wire name, used by
 * every curl example in customer-service.md, is **`primary`**; this client
 * sends only that and never `primaryRequested`.
 */
export interface AddressRequest {
  readonly cityId: number;
  readonly districtId: number;
  readonly street: string;
  readonly houseFlatNumber: string;
  readonly addressDescription: string;
  readonly primary?: boolean;
}

/**
 * Address as returned by `GET /api/customers/{n}/addresses`, the create 201,
 * the address PUT and the set-primary PATCH — all four return this one shape.
 *
 * ✅ **VERIFIED against the running stack's Swagger (address-controller,
 * 2026-07-25, scope §4.18)** — resolving the earlier open question: the
 * response carries BOTH the ids (`cityId`/`districtId`, what the form posts
 * back) AND the resolved display names (`cityName`/`districtName`, what the
 * address card renders) — no extra lookup round-trip needed for display.
 */
export interface AddressResponse {
  readonly addressId: number;
  readonly cityId: number;
  readonly cityName: string;
  readonly districtId: number;
  readonly districtName: string;
  readonly street: string;
  readonly houseFlatNumber: string;
  readonly addressDescription: string;
  readonly primary: boolean;
}
