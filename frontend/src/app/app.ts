import { Component, effect, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { I18nService } from './core/i18n';

/**
 * Root shell — skeleton only. Feature screens are lazily routed via app.routes.ts
 * (none built yet). The application chrome (header, sidenav, language switcher)
 * will live in shared/patterns and be composed here later.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly i18n = inject(I18nService);

  constructor() {
    // Keep <html lang> in sync with the active language (accessibility + correct
    // hyphenation). Effects run under zoneless change detection (FE-ADR-006 §7).
    effect(() => {
      document.documentElement.lang = this.i18n.lang();
    });
  }
}
