import { Component, computed } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Table, TableCellDef, type TableColumn } from './table';

interface Row {
  readonly id: number;
  readonly name: string;
}

const ROWS: readonly Row[] = [
  { id: 1001, name: 'Ali' },
  { id: 1002, name: 'Zeynep' },
  { id: 1003, name: 'Mehmet' },
];

@Component({
  imports: [Table, TableCellDef],
  template: `
    <app-table [columns]="columns()" [rows]="rows" [rowTestId]="rowTestId" testId="spec-table">
      <ng-template appTableCell="id" let-row>
        <span [attr.data-testid]="'spec-table-row-' + row.id + '-id-cell'">#{{ row.id }}</span>
      </ng-template>
      <ng-template appTableCell="name" let-row>{{ row.name }}</ng-template>
    </app-table>
  `,
})
class Host {
  readonly columns = computed<readonly TableColumn[]>(() => [
    { id: 'id', header: 'ID', cellClass: 'eds-tabular-nums' },
    { id: 'name', header: 'Name', cellClass: 'text-body text-ink' },
  ]);
  readonly rows = ROWS;
  readonly rowTestId = (row: Row): string => `spec-table-row-${row.id}`;
}

describe('Table', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Host] });
  });

  function render(): ComponentFixture<Host> {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    return fixture;
  }

  function byTestId(fixture: ComponentFixture<Host>, id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  it('renders SEMANTIC table markup (FE-ADR-011 §e) with the testId on the table', () => {
    const table = byTestId(render(), 'spec-table');
    expect(table?.tagName).toBe('TABLE');
    expect(table?.querySelector('thead')).not.toBeNull();
    expect(table?.querySelector('tbody')).not.toBeNull();
  });

  it('renders the caller-resolved headers in column order', () => {
    const headers = Array.from(byTestId(render(), 'spec-table')?.querySelectorAll('th') ?? []);
    expect(headers.map((th) => th.textContent?.trim())).toEqual(['ID', 'Name']);
  });

  it('stamps BUSINESS-keyed testids on rows (FE-ADR-009 §5, never an index)', () => {
    const fixture = render();
    expect(byTestId(fixture, 'spec-table-row-1001')?.tagName).toBe('TR');
    expect(byTestId(fixture, 'spec-table-row-1002')).not.toBeNull();
    expect(byTestId(fixture, 'spec-table-row-0')).toBeNull();
  });

  it('renders each cell through the matching appTableCell template', () => {
    const fixture = render();
    expect(byTestId(fixture, 'spec-table-row-1001-id-cell')?.textContent).toBe('#1001');
    expect(byTestId(fixture, 'spec-table-row-1002')?.textContent).toContain('Zeynep');
  });

  it('applies the column cellClass to the td and zebra-stripes the rows (mock §6.2)', () => {
    const fixture = render();
    const firstRow = byTestId(fixture, 'spec-table-row-1001');
    const cells = Array.from(firstRow?.querySelectorAll('td') ?? []);
    expect(cells[0]?.className).toContain('eds-tabular-nums');
    expect(cells[1]?.className).toContain('text-ink');
    // Zebra: row 0 (even) surface, row 1 (odd) page.
    expect(firstRow?.className).toContain('bg-surface');
    expect(byTestId(fixture, 'spec-table-row-1002')?.className).toContain('bg-page');
    expect(byTestId(fixture, 'spec-table-row-1003')?.className).toContain('bg-surface');
  });
});
