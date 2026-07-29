import { type EnvironmentProviders, inject, provideAppInitializer } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Where a restored page with no session goes — the same full-page login redirect
 * `AuthService.login()` performs, spelled out here rather than delegated: a page
 * restored from the bfcache carries the JS heap it was frozen with, including
 * `login()`'s in-flight guard flag. If it froze mid-redirect that flag is still
 * set and `login()` would return without navigating, leaving the stale screen up.
 */
const LOGIN_URL = '/oauth2/authorization/keycloak';

/**
 * Closes the one hole neither `authGuard` nor `AuthService.logout` can reach:
 * the browser's back/forward cache (bfcache).
 *
 * When the browser leaves a page for another origin — which every sign-in and
 * sign-out does — it may freeze the whole document, JS heap included, and
 * restore it verbatim on Back. A restore is NOT a load and NOT a router
 * navigation: no app initializer runs, no `authGuard` runs, no HTTP request is
 * made. Everything that normally re-checks the session is skipped, so a fully
 * rendered customer screen can reappear for a user who signed out on the very
 * next tab — the data is stale, but it is real data on a shared machine.
 *
 * `pageshow` with `persisted === true` is the only signal a restore happened,
 * and it fires on every browser that implements bfcache. That makes this the
 * portable fix; forcing `Cache-Control: no-store` on the SPA document would
 * disable bfcache in some browsers only, and would cost every legitimate
 * back-navigation a full reload.
 *
 * Registered as an app initializer purely to get an injection context — it
 * installs a listener and returns; bootstrap is never blocked on it.
 */
export function provideSessionRestoreCheck(): EnvironmentProviders {
  return provideAppInitializer(() => {
    installSessionRestoreCheck(inject(AuthService));
  });
}

/**
 * The listener itself, separated from DI so it can be exercised directly.
 * Returns a teardown; the application never needs it (the listener lives as long
 * as the document), tests do.
 */
export function installSessionRestoreCheck(auth: AuthService): () => void {
  const onPageShow = (event: PageTransitionEvent): void => {
    // A normal load already ran the session probe in the initializer.
    if (!event.persisted) {
      return;
    }

    // Signed out before the browser froze this page (logout clears the signal
    // before navigating away). Leave immediately — waiting for a probe would
    // leave restored customer data on screen for the length of a round trip.
    if (!auth.isAuthenticated()) {
      leave();
      return;
    }

    // The signal says authenticated, but this page may have been frozen for
    // hours: the gateway session can have expired, or another tab may have
    // signed out. Verify before trusting what is on screen.
    auth.loadSession().subscribe((session) => {
      if (!session?.authenticated) {
        leave();
      }
    });
  };

  window.addEventListener('pageshow', onPageShow);
  return () => window.removeEventListener('pageshow', onPageShow);
}

/**
 * `replace`, not `assign`: the restored entry is the one holding the stale
 * screen, so it must be overwritten rather than pushed past — otherwise Forward
 * (or another Back) restores it again. Leaving for another origin also discards
 * the restored JS heap, which still holds the loaded customer data.
 */
function leave(): void {
  window.location.replace(LOGIN_URL);
}
