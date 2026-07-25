import { Component, computed, input } from '@angular/core';

/** Visual families of the badge (mock-ui-analysis §6.4 status rozeti):
 *  `success` = Active (green family), `neutral` = Passive (sunken/secondary). */
export type StatusBadgeVariant = 'success' | 'neutral';

const VARIANT_CLASSES: Record<StatusBadgeVariant, { box: string; dot: string }> = {
  success: { box: 'bg-success-surface text-success-fg', dot: 'bg-success' },
  neutral: { box: 'bg-sunken text-ink-soft', dot: 'bg-ink-faint' },
};

/**
 * StatusBadge pattern (shared/patterns) — the mock's dot + text status chip
 * (mock-ui-analysis §6.4: inline-flex, gap 6px, padding 2px 8px, radius-sm,
 * caption/medium; §7.2 `EdsStatusBadge`). First consumer: the account table's
 * Active/Passive column; reusable wherever a status is displayed.
 *
 * Purely presentational: the caller maps its DOMAIN status onto a visual
 * `variant` and passes the RESOLVED label (`'KEY' | t`, scope §4.14) — the
 * badge knows no business values (FE-ADR-003). The dot is decorative; the
 * text carries the meaning for assistive tech.
 *
 * Metric note: the mock's 6px dot rounds onto the scale as `size-1.5` (6px —
 * exact; Tailwind's 4px base covers half steps).
 */
@Component({
  selector: 'app-status-badge',
  host: { class: 'inline-flex' },
  template: `
    <span [class]="boxClasses()" [attr.data-testid]="testId()">
      <span class="size-1.5 rounded-full" [class]="dotClass()" aria-hidden="true"></span>
      {{ label() }}
    </span>
  `,
})
export class StatusBadge {
  readonly variant = input.required<StatusBadgeVariant>();
  /** Resolved display text (`'KEY' | t`), never a raw domain value (§4.14). */
  readonly label = input.required<string>();
  readonly testId = input.required<string>();

  protected readonly boxClasses = computed(
    () =>
      'inline-flex items-center gap-1.5 rounded-sm px-2 py-0.5 text-caption font-medium ' +
      VARIANT_CLASSES[this.variant()].box,
  );
  protected readonly dotClass = computed(() => VARIANT_CLASSES[this.variant()].dot);
}
