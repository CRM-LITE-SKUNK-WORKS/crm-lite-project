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
  {
    path: ':customerNumber',
    loadComponent: () => import('./detail/customer-detail').then((m) => m.CustomerDetail),
  },
];
