import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { AuthService, provideSessionRestoreCheck } from './core/auth';
import { provideCoreHttp } from './core/http';

/**
 * Root application providers. Zoneless change detection is explicit
 * (FE-ADR-006 §7) — zone.js is not a dependency.
 *
 * `provideCoreHttp()` wires HttpClient (default XSRF) + the auth/normalization
 * interceptors (FE-ADR-004 §5, FE-ADR-008 §5). The app initializer runs the
 * session probe once at startup (FE-ADR-005 §1); it never blocks bootstrap on
 * failure — an anonymous/expired probe just resolves to "signed out".
 *
 * `provideSessionRestoreCheck()` covers the case that probe cannot: a page the
 * browser restored from the back/forward cache, where no initializer and no
 * guard runs at all.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideCoreHttp(),
    provideAppInitializer(() => inject(AuthService).loadSession()),
    provideSessionRestoreCheck(),
  ],
};
