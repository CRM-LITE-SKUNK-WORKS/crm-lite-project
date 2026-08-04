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
 * The page-size whitelist is NOT defined here. It is an app-wide server
 * contract shared by every paginated list, so it lives in
 * `shared/patterns/pagination/page-size.ts` — a sibling feature cannot import
 * `features/customer/model` (FE-ADR-003), and a per-feature copy is how the
 * superseded 20/50/100 list survived long enough to cause a 400 in the wild.
 * Re-exported here only so existing customer imports keep one spelling; the
 * values themselves have a single definition.
 */
export {
  PAGE_SIZE_OPTIONS,
  DEFAULT_PAGE_SIZE,
  isPageSize,
  type PageSize,
} from '../../../shared/patterns';

import type { PageSize } from '../../../shared/patterns';

/** Pagination request, kept separate from the filter criteria. */
export interface PageRequest {
  /** Zero-based page index (API default 0). */
  readonly page: number;
  /** Always sent explicitly (KR-04). */
  readonly size: PageSize;
}
