import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Pagination } from './pagination';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS, isPageSize, type PageSize } from './page-size';

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
  readonly rangeText = input<string>('1–15 of 137');
  // Whitelist values only (KR-04) — the harness must not model a size the API
  // would reject, or the spec would pin behaviour that cannot happen live.
  readonly size = input<PageSize>(DEFAULT_PAGE_SIZE);
  readonly sizeOptions = input<readonly PageSize[]>(PAGE_SIZE_OPTIONS);
  readonly pageLabelFor = (page: number): string => `Page ${page}`;
  readonly pageChanges: number[] = [];
  readonly sizeChanges: PageSize[] = [];
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
    expect(byTestId(render(), 'spec-pagination')?.textContent).toContain('1–15 of 137');
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
    expect(trigger.textContent).toContain('15');
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
    (byTestId(fixture, 'spec-page-size-select-option-15') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.sizeChanges).toEqual([]);
  });

  // --- KR-04 page-size contract (the 400 that took the customer list down) ---

  it('offers ONLY the server whitelist 15/30/50 — no superseded 20/50/100', () => {
    const fixture = render();
    (byTestId(fixture, 'spec-page-size-select') as HTMLButtonElement).click();
    fixture.detectChanges();

    const offered = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll(
        '[data-testid^="spec-page-size-select-option-"]',
      ),
    ).map((option) => Number(option.textContent?.trim()));

    expect(offered).toEqual([15, 30, 50]);
    // The exact values that produced `400 MSG-VALIDATION-ERROR` in the wild.
    for (const rejected of [20, 100]) {
      expect(offered).not.toContain(rejected);
    }
  });

  it('every emitted size is a value the API accepts', () => {
    const fixture = render();
    for (const option of PAGE_SIZE_OPTIONS) {
      (byTestId(fixture, 'spec-page-size-select') as HTMLButtonElement).click();
      fixture.detectChanges();
      (
        byTestId(fixture, `spec-page-size-select-option-${option}`) as HTMLButtonElement
      ).click();
      fixture.detectChanges();
    }
    const emitted = fixture.componentInstance.sizeChanges;
    expect(emitted.length).toBeGreaterThan(0);
    for (const size of emitted) {
      expect(isPageSize(size)).toBe(true);
    }
  });

  it('isPageSize accepts the whitelist and rejects everything else', () => {
    for (const allowed of PAGE_SIZE_OPTIONS) {
      expect(isPageSize(allowed)).toBe(true);
    }
    for (const rejected of [0, 1, 10, 20, 25, 100, -15]) {
      expect(isPageSize(rejected)).toBe(false);
    }
    expect(isPageSize(DEFAULT_PAGE_SIZE)).toBe(true);
  });
});
