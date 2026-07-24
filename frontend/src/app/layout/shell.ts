import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth';
import { I18nService, LANGUAGES, TranslatePipe, type Language } from '../core/i18n';

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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './shell.html',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);

  /** Real username from GET /api/session/me. The mock's
   *  "Mobility · Resp. Sales Rep." is deliberately NOT rendered — no backend
   *  source exists for it (decision 2.20 / FE-ADR-013 §a). */
  readonly username = this.auth.username;
  readonly lang = this.i18n.lang;
  readonly languages = LANGUAGES;

  setLanguage(lang: Language): void {
    this.i18n.setLanguage(lang);
  }

  logout(): void {
    this.auth.logout();
  }
}
