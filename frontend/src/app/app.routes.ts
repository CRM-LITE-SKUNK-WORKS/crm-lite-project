import { type Routes } from '@angular/router';
import { authGuard, crmUserGuard } from './core/auth';
import { Shell } from './layout/shell';

/**
 * Root routing table (FE-ADR-003 §1, FE-ADR-005 §2/§4).
 *
 * Everything the user can reach sits under the `Shell` layout and behind
 * `authGuard`, so the chrome never renders for an anonymous visitor and an
 * unauthenticated navigation turns into a full-page redirect to Keycloak.
 *
 * `access-denied` is guarded by `authGuard` but NOT by `crmUserGuard` — a role
 * denial must have somewhere to land that does not re-trigger the guard that
 * sent it there.
 *
 * The Shell itself is eager (it is on every page); features are lazy.
 */
export const routes: Routes = [
  // FIRST, and outside both the Shell and authGuard: a signed-out visitor must be
  // able to land somewhere that does NOT bounce them straight back to Keycloak.
  {
    path: 'signed-out',
    loadComponent: () => import('./features/signed-out/signed-out').then((m) => m.SignedOut),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'customers' },
      {
        path: 'customers',
        canActivate: [crmUserGuard],
        loadChildren: () =>
          import('./features/customer/customer.routes').then((m) => m.CUSTOMER_ROUTES),
      },
      {
        path: 'access-denied',
        loadComponent: () =>
          import('./features/access-denied/access-denied').then((m) => m.AccessDenied),
      },
    ],
  },
  // Unknown URL → the default screen (which re-runs the guards).
  { path: '**', redirectTo: '' },
];
