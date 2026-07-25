/**
 * Server-side pagination envelope for `GET /api/customers`
 * (`Page<CustomerDetailResponse>`, ADR-005).
 *
 * ✅ **VERIFIED against the running stack's Swagger (customer-controller,
 * 2026-07-25, scope §4.18):** the wire shape is the FLAT `PageImpl`
 * serialization — metadata at the top level (`totalElements`, `totalPages`,
 * `size`, `content`, `number`, `first`, `last`, plus `pageable`/`sort`/
 * `numberOfElements`/`empty` noise we ignore) — NOT the nested `PagedModel`
 * shape. This closed the last open item of BRAIN §5.2b.
 *
 * ⚠ Still worth remembering (scope §4.4): the backend has NOT pinned
 * `spring.data.web.pageable.serialization-mode`, so this verified shape is the
 * framework default, not a deliberate contract — a Spring Boot upgrade could
 * change it. The recommendation to pin it (or return an explicit page DTO)
 * stands with the backend. Until then, the containment holds:
 *   - {@link SpringPage} is read in exactly ONE place ({@link toPageResult});
 *   - {@link PageResult} is the normalized shape the app consumes — zero
 *     framework leakage, so a future drift changes only this file.
 */

/** Raw Spring `PageImpl` wire shape (verified via Swagger, 2026-07-25). Only
 *  the fields the app needs are typed; extras (`pageable`, `sort`, …) are
 *  tolerated and ignored. */
export interface SpringPage<T> {
  readonly content: readonly T[];
  readonly totalElements: number;
  readonly totalPages: number;
  /** Zero-based current page index. */
  readonly number: number;
  /** Page size that was applied. */
  readonly size: number;
  /** Server-computed flags — present on the verified shape; optional so a
   *  minimal fixture (or a future slimmer DTO) still normalizes. */
  readonly first?: boolean;
  readonly last?: boolean;
}

/** Normalized pagination result the rest of the app consumes. Zero framework
 *  leakage: screens read these names, never the raw envelope. */
export interface PageResult<T> {
  readonly items: readonly T[];
  /** Zero-based page index. */
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly isFirst: boolean;
  readonly isLast: boolean;
}

/** The one and only place the framework envelope is read. Prefers the server's
 *  own `first`/`last` flags (verified on the wire), computing them only when
 *  absent. Defensive against missing numeric fields so a shape drift degrades
 *  to safe zeros rather than `NaN` on screen. */
export function toPageResult<T>(page: SpringPage<T>): PageResult<T> {
  const items = page.content ?? [];
  const size = page.size ?? items.length;
  const number = page.number ?? 0;
  const totalPages = page.totalPages ?? 0;
  return {
    items,
    page: number,
    size,
    totalElements: page.totalElements ?? items.length,
    totalPages,
    isFirst: page.first ?? number <= 0,
    isLast: page.last ?? (totalPages === 0 ? true : number >= totalPages - 1),
  };
}
