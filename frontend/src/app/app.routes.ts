import { type Routes } from '@angular/router';
import { authGuard, crmUserGuard } from './core/auth';
import { Shell } from './layout/shell';

/**
 * Root routing table (FE-ADR-003 §1, FE-ADR-005 §2/§4).
 *
 * EVERY route sits under the `Shell` layout and behind `authGuard`: the chrome
 * never renders for an anonymous visitor, and an unauthenticated navigation
 * turns into a full-page redirect to Keycloak. There is deliberately no
 * signed-out landing page — sign-out is confirmed in a dialog before it happens
 * and ends on Keycloak's own sign-in form, so an application page saying "you
 * have been signed out" would be an extra click and nothing else.
 *
 * `access-denied` is guarded by `authGuard` but NOT by `crmUserGuard` — a role
 * denial must have somewhere to land that does not re-trigger the guard that
 * sent it there.
 *
 * The Shell itself is eager (it is on every page); features are lazy.
 */
export const routes: Routes = [
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
