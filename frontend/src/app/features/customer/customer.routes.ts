import { type Routes } from '@angular/router';

/**
 * Customer feature routes, reached via `loadChildren` so the whole feature is a
 * lazy chunk (FE-ADR-003 §1). The shape is already the final one — Search,
 * Create and Detail slot in as they are built, each with its own
 * `loadComponent`, without touching the root routing table.
 */
export const CUSTOMER_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./search/customer-search').then((m) => m.CustomerSearch),
  },
  // 'new' MUST precede ':customerNumber' — otherwise "new" would be read as a
  // customer number by the param route.
  {
    path: 'new',
    loadComponent: () => import('./create/customer-create').then((m) => m.CustomerCreate),
  },
  // §2.7 product-sale wizard (FR-SALE-01/02). MUST precede ':customerNumber'
  // only in the sense of specificity — it does not collide, because it carries
  // a deeper path ('sales/new'); Angular matches the longer prefix first only
  // when the shorter route has no children, so it is declared ahead of the
  // bare param route to keep that independent of future edits.
  //
  // `accountNumber` is a QUERY param, not a path segment: it qualifies WHICH
  // billing account of this customer the sale is for (AC-SALE-01-01), it is
  // not part of the resource's identity. Nothing else about the flow is in the
  // URL — the basket is session-only (AC-SALE-01-16), so a deep link always
  // starts at step 1 with an empty basket, which is the required behaviour.
  {
    path: ':customerNumber/sales/new',
    loadComponent: () => import('./sales/sale-wizard').then((m) => m.SaleWizard),
  },
  {
    path: ':customerNumber',
    loadComponent: () => import('./detail/customer-detail').then((m) => m.CustomerDetail),
  },
];
