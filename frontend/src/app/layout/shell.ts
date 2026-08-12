import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth';
import { I18nService, LANGUAGES, TranslatePipe, type Language } from '../core/i18n';
import { ConfirmDialog } from '../shared/patterns';

/**
 * The application chrome shared by every screen — header + sidenav + main
 * (`docs/frontend/mock-ui-analysis.md` §4, which prescribes ONE layout component
 * rather than the per-screen copies the mock template carries).
 *
 * It is a routed layout component wrapping the protected routes, so the chrome
 * renders only for an authenticated session and the guard runs before any of it
 * is shown.
 *
 * Why `layout/` and not one of FE-ADR-003's three layers: it injects core
 * services (auth, i18n), which `shared/` is forbidden to do (and the lint rule
 * enforces), yet it is not a user-facing capability so it is not a feature, and
 * it is a component so it is not core. Recorded in scope-and-conflicts §4.12.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, ConfirmDialog],
  templateUrl: './shell.html',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);

  /** Header identity, all from GET /api/session/me (decision 2.20, revised
   *  2026-08-12): the display name with `username` as its fallback, and the job
   *  title in its own segment left of the avatar, where mock §4.1 puts it. The
   *  mock's "Mobility · Resp. Sales Rep." role text now HAS a backend source — a
   *  Keycloak `titleCode` user attribute relayed through the ID token — so it is
   *  rendered instead of dropped. `title()` is null on a realm that has not been
   *  reconciled yet; the template then omits the segment entirely rather than
   *  inventing text (FE-ADR-013 §a still forbids that). */
  readonly username = this.auth.username;
  readonly fullName = this.auth.fullName;

  /**
   * The job title as TEXT: the session carries a code (`SALES_REP`) and this
   * resolves it through the i18n catalogue, so the title switches with TR/EN like
   * every other word on screen. Same division of labour as `displayGender`
   * (scope §2.7) — the identity provider owns a stable value, the catalogue owns
   * the wording.
   *
   * Reads `dictionary()` rather than `translate()` deliberately: `translate()`
   * answers UI-ERROR-GENERIC for an unknown key, which would turn a title this
   * catalogue has not learned yet into "Something went wrong" in the header. A
   * miss falls back to the RAW code instead — honest, obviously untranslated, and
   * a visible prompt to add the entry. Reading the signal also makes the title
   * re-render the instant the language changes.
   */
  readonly title = computed<string | null>(() => {
    const code = this.auth.titleCode();
    if (!code) {
      return null;
    }
    return this.i18n.dictionary()[`UI-TITLE-${code.toUpperCase().replace(/_/g, '-')}`] ?? code;
  });
  readonly lang = this.i18n.lang;
  readonly languages = LANGUAGES;

  private readonly _logoutConfirmOpen = signal(false);
  private readonly _logoutBusy = signal(false);

  /** Visibility and busy state of the sign-out confirmation (ConfirmDialog
   *  leaves both to the caller — it never closes itself). */
  readonly logoutConfirmOpen = this._logoutConfirmOpen.asReadonly();
  readonly logoutBusy = this._logoutBusy.asReadonly();

  setLanguage(lang: Language): void {
    this.i18n.setLanguage(lang);
  }

  /** The sidenav button only asks. Nothing is signed out yet. */
  requestLogout(): void {
    this._logoutConfirmOpen.set(true);
  }

  /**
   * Confirmed: THIS is where the session actually ends — the CSRF-protected
   * POST, the gateway session, the Keycloak SSO session and the cookies.
   *
   * The dialog is deliberately left open and busy: `AuthService.logout()` ends
   * in a full-page navigation to Keycloak, so closing it would only flash the
   * chrome back for the moment before the browser leaves. On the failure path
   * `logout()` navigates too (a reload of `/`), so there is likewise nothing
   * here to reset.
   */
  confirmLogout(): void {
    this._logoutBusy.set(true);
    this.auth.logout();
  }

  cancelLogout(): void {
    this._logoutConfirmOpen.set(false);
  }
}
