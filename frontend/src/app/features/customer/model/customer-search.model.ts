/**
 * Customer list/filter parameters (`GET /api/customers`, ADR-005 / KR-01).
 *
 * customer-service.md documents these query params: `firstName`, `lastName`,
 * `nationalityId`, `customerId`, `gsmNumber`, `accountNumber`, `orderNumber`,
 * `page`, `size`. All seven are representable here since 2026-08-05.
 *
 * ✅ `accountNumber` / `orderNumber` were excluded until 2026-08-05 because the
 * backend answered them with `501 MSG-FEATURE-NOT-IMPLEMENTED`. That gate is
 * gone: customer-service now resolves each number through its owning service
 * (account-service / order-service) and returns the owning customer (KR-02,
 * AC-CUST-01-04). Recorded in `scope-and-conflicts.md` §1.7c.
 *
 * Every value stays a **string**. Account and Order numbers are fixed-width
 * KR-11/KR-12 identifiers, not quantities: `Number('0261000010')` would drop the
 * leading zero and a 10-digit identifier is meaningfully a digit STRING.
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
  /** KR-11 billing-account number, **exact** match (KR-01: "birebir"); resolves
   *  to the customer that owns the account. Digits only, kept as a string. */
  readonly accountNumber?: string;
  /** KR-12 order number, **exact** match; resolves to the customer that owns
   *  the order. Digits only, kept as a string. */
  readonly orderNumber?: string;
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
