import { NgTemplateOutlet } from '@angular/common';
import {
  Component,
  Directive,
  TemplateRef,
  computed,
  contentChild,
  contentChildren,
  inject,
  input,
} from '@angular/core';

/**
 * A column of the {@link Table} pattern. The definition comes ENTIRELY from the
 * caller — the table knows no field names and reads no row properties itself.
 */
export interface TableColumn {
  /** Stable column id; matched against the `appTableCell` template of the caller. */
  readonly id: string;
  /** RESOLVED header text — the caller translates (`i18n.translate(...)` /
   *  `'KEY' | t`); the pattern never does (FE-ADR-003 / scope §4.14). */
  readonly header: string;
  /** Extra classes for this column's body cells (EDS utilities only,
   *  FE-ADR-011 §f) — e.g. `'text-body text-ink'` or `'eds-tabular-nums …'`. */
  readonly cellClass?: string;
  /** Extra classes for this column's header cell — e.g. `'text-right'` for an
   *  actions column (mock §6.4). Same EDS-utilities-only rule. */
  readonly headerClass?: string;
}

/** Template context of an `appTableCell` template: `$implicit` is the row. */
export interface TableCellContext<T> {
  readonly $implicit: T;
}

/**
 * Marks the OPTIONAL expansion template rendered in a full-width row beneath an
 * expanded row: `<ng-template appTableExpansion let-row>…</ng-template>`.
 *
 * Added 2026-07-31 for the mock's expandable account rows (mock-ui-analysis
 * §6.4 tab 2 / AC-PROD-01-01). Strictly additive: a table without this template
 * behaves exactly as before, and the pattern still decides nothing — WHICH rows
 * are expanded is the caller's `isExpanded` predicate, and the expander control
 * itself is an ordinary column cell the caller renders.
 */
@Directive({ selector: 'ng-template[appTableExpansion]' })
export class TableExpansionDef<T> {
  readonly template = inject<TemplateRef<TableCellContext<T>>>(TemplateRef);
}

/**
 * Marks an `<ng-template>` as the cell renderer for one column:
 * `<ng-template appTableCell="customerId" let-row>…</ng-template>`.
 *
 * No `ngTemplateContextGuard` is declared deliberately: the table is generic
 * and the guard would erase the row type to `unknown`, breaking every caller
 * under `strictTemplates`. The `let-` variable is therefore untyped in the
 * template — the standard Angular trade-off for generic cell templates.
 */
@Directive({ selector: 'ng-template[appTableCell]' })
export class TableCellDef<T> {
  /** The {@link TableColumn.id} this template renders. */
  readonly columnId = input.required<string>({ alias: 'appTableCell' });
  readonly template = inject<TemplateRef<TableCellContext<T>>>(TemplateRef);
}

/**
 * Table pattern (shared/patterns) — the mock's hand-built results table
 * (mock-ui-analysis §6.2), extracted from Customer Search. Not an EDS
 * component: the design system has none, so this is §7.2 territory
 * (FE-ADR-011 §d).
 *
 * Contract (FE-ADR-011 §e is binding):
 * - SEMANTIC HTML — `<table><thead><tbody>`; no grid library, no
 *   virtualization, no sorting (sorting is fixed server-side).
 * - Purely presentational: columns, rows and cell markup come from the caller;
 *   the table decides nothing and formats nothing.
 * - Every row carries a `data-testid` derived from a BUSINESS key via the
 *   caller's `rowTestId` function — never an array index (FE-ADR-009 §5).
 * - Styling is the mock's verbatim table spec: sunken header row, zebra body
 *   rows (odd `bg-page`, even `bg-surface`), 1px top borders.
 * - OPTIONAL row expansion via {@link TableExpansionDef} + `isExpanded`
 *   (added 2026-07-31): opt-in, and the caller still owns both the expanded set
 *   and the expander control.
 *
 * Scrolling stays with the caller (wrap in an `overflow-auto` container) —
 * layout inside a card is the screen's concern, not the pattern's.
 */
@Component({
  selector: 'app-table',
  imports: [NgTemplateOutlet],
  host: { class: 'block' },
  template: `
    <table class="w-full border-collapse" [attr.data-testid]="testId()">
      <thead>
        <tr class="bg-sunken">
          @for (column of columns(); track column.id) {
            <th
              class="px-5 py-3 text-left text-body-sm font-semibold tracking-label whitespace-nowrap text-ink-soft"
              [class]="column.headerClass ?? ''"
            >
              {{ column.header }}
            </th>
          }
        </tr>
      </thead>
      <tbody>
        @for (row of rows(); track rowTestId()(row); let odd = $odd) {
          <tr
            class="border-t border-line"
            [class.bg-page]="odd"
            [class.bg-surface]="!odd"
            [attr.data-testid]="rowTestId()(row)"
          >
            @for (column of columns(); track column.id) {
              <td class="px-5 py-4 whitespace-nowrap" [class]="column.cellClass ?? ''">
                <ng-container
                  *ngTemplateOutlet="templateFor(column.id); context: { $implicit: row }"
                />
              </td>
            }
          </tr>
          <!-- OPTIONAL expansion: rendered only when the caller projected an
               appTableExpansion template AND its predicate says so. The row
               keeps the parent's zebra shade so the pair reads as one unit. -->
          @if (expansionTemplate(); as expansion) {
            @if (isExpanded()(row)) {
              <tr
                class="border-t border-line"
                [class.bg-page]="odd"
                [class.bg-surface]="!odd"
                [attr.data-testid]="rowTestId()(row) + '-expansion'"
              >
                <td class="p-0" [attr.colspan]="columns().length">
                  <ng-container *ngTemplateOutlet="expansion; context: { $implicit: row }" />
                </td>
              </tr>
            }
          }
        }
      </tbody>
    </table>
  `,
})
export class Table<T> {
  readonly columns = input.required<readonly TableColumn[]>();
  readonly rows = input.required<readonly T[]>();
  /** Business-keyed row testid (FE-ADR-009 §5): e.g.
   *  `(c) => 'customer-search-results-row-' + c.customerNumber`. */
  readonly rowTestId = input.required<(row: T) => string>();
  readonly testId = input.required<string>();
  /** OPTIONAL expansion predicate; only consulted when the caller projected an
   *  `appTableExpansion` template. Default: nothing expands (previous behaviour). */
  readonly isExpanded = input<(row: T) => boolean>(() => false);

  private readonly cellDefs = contentChildren<TableCellDef<T>>(TableCellDef);
  private readonly expansionDef = contentChild<TableExpansionDef<T>>(TableExpansionDef);

  /** `null` when the caller projected no expansion template — the table then
   *  renders exactly the markup it did before expansion existed. */
  protected readonly expansionTemplate = computed(
    () => this.expansionDef()?.template ?? null,
  );

  private readonly templatesById = computed(() => {
    const map = new Map<string, TemplateRef<TableCellContext<T>>>();
    for (const def of this.cellDefs()) {
      map.set(def.columnId(), def.template);
    }
    return map;
  });

  /** A column without a matching template is a wiring bug — fail loudly, not
   *  with a silently empty cell (the FE-ADR-008 §3 stance applied to markup). */
  protected templateFor(columnId: string): TemplateRef<TableCellContext<T>> {
    const template = this.templatesById().get(columnId);
    if (!template) {
      throw new Error(`app-table: no appTableCell template for column "${columnId}"`);
    }
    return template;
  }
}
