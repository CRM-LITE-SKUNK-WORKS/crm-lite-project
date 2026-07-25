/**
 * Customer list/filter parameters (`GET /api/customers`, ADR-005 / KR-01).
 *
 * customer-service.md documents these query params: `firstName`, `lastName`,
 * `nationalityId`, `customerId`, `gsmNumber`, `accountNumber`, `orderNumber`,
 * `page`, `size`.
 *
 * 🔴 `accountNumber` and `orderNumber` are DELIBERATELY EXCLUDED from this type.
 * The backend answers them with `501 MSG-FEATURE-NOT-IMPLEMENTED` (customer
 * search still returns 501 even after account-service shipped — the conversion
 * is a separate follow-up PR, account-service.md §Deliberate limitations). Per
 * FE-ADR-013 §b the frontend renders those inputs disabled and NEVER sends them,
 * so they must not be representable here (a 501 reaching the interceptor is a
 * frontend bug, FE-ADR-008 §6).
 */
export interface CustomerSearchCriteria {
  /** Word-start over First + Middle combined, case-insensitive (KR-01). */
  readonly firstName?: string;
  /** Word-start over Last name only. */
  readonly lastName?: string;
  /** Exact match; digits only (backend rejects non-numeric with 400). */
  readonly nationalityId?: string;
  /** Exact match on the business customer number. The query param keeps the
   *  name `customerId` even though the response field is `customerNumber`
   *  (ADR-005 note; scope §2.11) — the two are never conflated. */
  readonly customerId?: string;
  /** Prefix match on the mobile phone. */
  readonly gsmNumber?: string;
}

/**
 * Per-page options (scope §2.4 — decided **20 / 50 / 100, default 20**). The
 * mock's 15/25/50 and the analyst's 15/30/50 were both superseded; the API
 * accepts any positive `size`, so 50/100 need no backend change.
 *
 * KR-04: `size` is ALWAYS sent explicitly (never relying on the API's own
 * default of 20), so UI and server can never disagree on page size.
 */
export const PAGE_SIZE_OPTIONS = [20, 50, 100] as const;
export type PageSize = (typeof PAGE_SIZE_OPTIONS)[number];
export const DEFAULT_PAGE_SIZE: PageSize = 20;

/** Pagination request, kept separate from the filter criteria. */
export interface PageRequest {
  /** Zero-based page index (API default 0). */
  readonly page: number;
  /** Always sent explicitly (KR-04). */
  readonly size: PageSize;
}
