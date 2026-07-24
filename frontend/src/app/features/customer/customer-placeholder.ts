import { Component } from '@angular/core';
import { TranslatePipe } from '../../core/i18n';

/**
 * Protected placeholder page. It exists so the routing + guard + shell skeleton
 * is verifiable end to end before any business screen exists; the Customer
 * Search screen (FE-ADR-013 golden path) replaces it.
 */
@Component({
  selector: 'app-customer-placeholder',
  imports: [TranslatePipe],
  template: `
    <section
      class="rounded-lg border border-line bg-surface p-6"
      data-testid="customer-placeholder"
    >
      <h1 class="text-h1 font-semibold text-ink">{{ 'UI-PLACEHOLDER-TITLE' | t }}</h1>
      <p class="mt-2 text-body text-ink-soft">{{ 'UI-PLACEHOLDER-BODY' | t }}</p>
    </section>
  `,
})
export class CustomerPlaceholder {}
