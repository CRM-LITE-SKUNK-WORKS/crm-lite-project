import { Component } from '@angular/core';
import { TranslatePipe } from '../../core/i18n';

/**
 * Landing page after a completed sign-out. It sits OUTSIDE `authGuard` on
 * purpose: the guard would immediately bounce a signed-out visitor to Keycloak,
 * where a surviving SSO session re-authenticates silently and drops the user
 * back in the app — which is exactly what made sign-out look like a no-op.
 *
 * It also renders outside the `Shell`: the chrome shows a username and a logout
 * button, neither of which means anything once signed out.
 */
@Component({
  selector: 'app-signed-out',
  imports: [TranslatePipe],
  template: `
    <main class="flex min-h-screen items-center justify-center bg-page p-6">
      <section
        class="w-full max-w-md rounded-lg border border-line bg-surface p-6 text-center"
        data-testid="signed-out"
      >
        <h1 class="text-h2 font-semibold text-ink">{{ 'UI-SIGNED-OUT-TITLE' | t }}</h1>
        <p class="mt-2 text-body text-ink-soft">{{ 'UI-SIGNED-OUT-BODY' | t }}</p>

        <!-- A plain href, NOT routerLink: signing in must be a full-page
             navigation the gateway handles, not client-side routing. And it is a
             link, never a form — no credential input exists anywhere in this
             application (FE-ADR-005 §P1). -->
        <a
          href="/oauth2/authorization/keycloak"
          class="mt-5 inline-flex h-10 items-center rounded-md bg-brand px-4 text-body font-medium text-on-brand"
          data-testid="signed-out-sign-in-link"
        >
          {{ 'UI-COMMON-SIGN-IN' | t }}
        </a>
      </section>
    </main>
  `,
})
export class SignedOut {}
