import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth';
import { TranslatePipe } from '../../core/i18n';

/**
 * Shown when the session is authenticated but lacks the `crm-user` role
 * (FE-ADR-005 §4 — the `MSG-AUTH-FORBIDDEN` case). There is no "try again" and
 * no login link: re-authenticating as the same user cannot grant a role. The one
 * useful action is signing out so a different account can be used.
 *
 * The body text reuses the backend's own `MSG-AUTH-FORBIDDEN` catalogue entry
 * rather than inventing a second wording (FE-ADR-008 §1).
 */
@Component({
  selector: 'app-access-denied',
  imports: [TranslatePipe],
  template: `
    <section
      class="rounded-lg border border-danger-border bg-danger-surface p-6"
      data-testid="access-denied"
    >
      <h1 class="text-h2 font-semibold text-danger-fg">
        {{ 'UI-ACCESS-DENIED-TITLE' | t }}
      </h1>
      <p class="mt-2 text-body text-ink-soft">{{ 'MSG-AUTH-FORBIDDEN' | t }}</p>
      <button
        type="button"
        class="mt-5 h-10 rounded-md bg-brand px-4 text-body font-medium text-on-brand"
        (click)="logout()"
        data-testid="access-denied-logout-button"
      >
        {{ 'UI-COMMON-LOGOUT' | t }}
      </button>
    </section>
  `,
})
export class AccessDenied {
  private readonly auth = inject(AuthService);

  logout(): void {
    this.auth.logout();
  }
}
