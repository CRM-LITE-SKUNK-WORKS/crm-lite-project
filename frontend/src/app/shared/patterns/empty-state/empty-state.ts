import { Component, input } from '@angular/core';
import { Icon, type IconName } from '../../ui';

/**
 * EmptyState pattern (shared/patterns) — the mock's "no records" card
 * (mock-ui-analysis §6.2 boş durum: 48px sunken circle + icon 24px + semibold
 * title + max-width body). Not an EDS component — §7.2 hand-built pattern
 * (FE-ADR-011 §d); first consumer is Customer Search's "No customer found".
 *
 * Purely presentational: which icon and which copy (searched-and-missed vs
 * browse-and-empty is a SCREEN decision — scope §2A.3) arrive from the caller,
 * already translated (`'KEY' | t`, scope §4.14).
 *
 * The host carries the centering layout so the pattern can sit directly in a
 * flex card body (as the inline markup it replaces did).
 */
@Component({
  selector: 'app-empty-state',
  imports: [Icon],
  host: {
    class: 'flex flex-1 flex-col items-center justify-center gap-3 px-6 py-10 text-center',
    '[attr.data-testid]': 'testId()',
  },
  template: `
    <span class="flex size-12 items-center justify-center rounded-full bg-sunken">
      <app-icon [name]="icon()" [size]="24" class="text-ink-muted" />
    </span>
    <span class="text-body-lg font-semibold text-ink">{{ title() }}</span>
    @if (body(); as bodyText) {
      <span class="max-w-90 text-body text-ink-soft">{{ bodyText }}</span>
    }
  `,
})
export class EmptyState {
  readonly icon = input.required<IconName>();
  /** Resolved title text (`'KEY' | t`), never a catalogue key (§4.14). */
  readonly title = input.required<string>();
  /** Resolved body text; optional — some empty states are title-only. */
  readonly body = input<string | undefined>(undefined);
  readonly testId = input.required<string>();
}
