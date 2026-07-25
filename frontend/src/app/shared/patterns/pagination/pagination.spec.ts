import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Pagination } from './pagination';

@Component({
  imports: [Pagination],
  template: `
    <app-pagination
      [page]="page()"
      [totalPages]="totalPages()"
      [isFirst]="isFirst()"
      [isLast]="isLast()"
      [rangeText]="rangeText()"
      prevLabel="Previous page"
      nextLabel="Next page"
      [pageLabelFor]="pageLabelFor"
      perPageLabel="Per page"
      [size]="size()"
      [sizeOptions]="sizeOptions()"
      testId="spec-pagination"
      sizeSelectTestId="spec-page-size-select"
      (pageChange)="pageChanges.push($event)"
      (sizeChange)="sizeChanges.push($event)"
    />
  `,
})
class Host {
  readonly page = input<number>(0);
  readonly totalPages = input<number>(7);
  readonly isFirst = input<boolean>(true);
  readonly isLast = input<boolean>(false);
  readonly rangeText = input<string>('1–20 of 137');
  readonly size = input<number>(20);
  readonly sizeOptions = input<readonly number[]>([20, 50, 100]);
  readonly pageLabelFor = (page: number): string => `Page ${page}`;
  readonly pageChanges: number[] = [];
  readonly sizeChanges: number[] = [];
}

describe('Pagination', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Host] });
  });

  function render(inputs: Record<string, unknown> = {}): ComponentFixture<Host> {
    const fixture = TestBed.createComponent(Host);
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    // Two passes: the size-sync effect writes into the Select's CVA signal,
    // which needs one more sync to reach the DOM under zoneless TestBed.
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture;
  }

  function byTestId(fixture: ComponentFixture<Host>, id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  it('renders the resolved range text on the footer host', () => {
    expect(byTestId(render(), 'spec-pagination')?.textContent).toContain('1–20 of 137');
  });

  it('shows all pages when totalPages ≤ 7, with aria-current on the active one', () => {
    const fixture = render();
    for (let n = 1; n <= 7; n++) {
      expect(byTestId(fixture, `spec-pagination-page-${n}`)).not.toBeNull();
    }
    expect(byTestId(fixture, 'spec-pagination-page-1')?.getAttribute('aria-current')).toBe('page');
    expect(byTestId(fixture, 'spec-pagination-page-2')?.getAttribute('aria-current')).toBeNull();
  });

  it('truncates per the mock rule: 1 … (p-1) p (p+1) … last (scope §2A.4)', () => {
    const fixture = render({ page: 4, totalPages: 12, isFirst: false });
    for (const visible of [1, 4, 5, 6, 12]) {
      expect(byTestId(fixture, `spec-pagination-page-${visible}`)).not.toBeNull();
    }
    for (const hidden of [2, 3, 7, 11]) {
      expect(byTestId(fixture, `spec-pagination-page-${hidden}`)).toBeNull();
    }
    expect(byTestId(fixture, 'spec-pagination-page-5')?.getAttribute('aria-current')).toBe('page');
  });

  it('emits ZERO-based pageChange from page buttons and arrows', () => {
    const fixture = render({ page: 4, totalPages: 12, isFirst: false });
    (byTestId(fixture, 'spec-pagination-page-6') as HTMLButtonElement).click();
    (byTestId(fixture, 'spec-pagination-prev') as HTMLButtonElement).click();
    (byTestId(fixture, 'spec-pagination-next') as HTMLButtonElement).click();
    expect(fixture.componentInstance.pageChanges).toEqual([5, 3, 5]);
  });

  it('never emits for the already-active page (inert active button)', () => {
    const fixture = render();
    (byTestId(fixture, 'spec-pagination-page-1') as HTMLButtonElement).click();
    expect(fixture.componentInstance.pageChanges).toEqual([]);
  });

  it('disables the arrows from the server flags (isFirst/isLast pass-through)', () => {
    const fixture = render({ isFirst: true, isLast: true });
    expect((byTestId(fixture, 'spec-pagination-prev') as HTMLButtonElement).disabled).toBe(true);
    expect((byTestId(fixture, 'spec-pagination-next') as HTMLButtonElement).disabled).toBe(true);
  });

  it('reflects the size input in the select and emits sizeChange on pick', () => {
    const fixture = render();
    const trigger = byTestId(fixture, 'spec-page-size-select') as HTMLButtonElement;
    expect(trigger.textContent).toContain('20');
    trigger.click();
    fixture.detectChanges();
    (byTestId(fixture, 'spec-page-size-select-option-50') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.sizeChanges).toEqual([50]);
  });

  it('does not echo a size that merely mirrors the input back', () => {
    const fixture = render();
    const trigger = byTestId(fixture, 'spec-page-size-select') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    (byTestId(fixture, 'spec-page-size-select-option-20') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.sizeChanges).toEqual([]);
  });
});
