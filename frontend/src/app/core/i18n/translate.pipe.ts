import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nService } from './i18n.service';

/**
 * Translation pipe: `{{ 'LBL-SEARCH' | t }}`.
 *
 * `pure: false` on purpose — the active language is a signal on I18nService, not
 * a pipe argument, so a pure pipe would not re-run on a language change. Under
 * zoneless change detection the impure pipe re-evaluates when CD runs (which the
 * language signal triggers). The transform is a cheap object lookup.
 *
 * data-testid and any non-visible identifiers must NOT go through this pipe —
 * only user-visible text is translated (FE-ADR-012 §b, FE-ADR-009 §6).
 */
@Pipe({ name: 't', standalone: true, pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: string, params?: Record<string, string | number>): string {
    return this.i18n.translate(key, params);
  }
}
