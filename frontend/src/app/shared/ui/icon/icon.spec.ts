import { Component, input } from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Icon } from './icon';
import { ICON_PATHS, type IconName, type IconSize } from './icons';

/** Host so signal inputs can be driven the way screens will drive them. */
@Component({
  imports: [Icon],
  template: `<app-icon [name]="name()" [size]="size()" [ariaLabel]="ariaLabel()" />`,
})
class Host {
  readonly name = input<IconName>('check-circle-2');
  readonly size = input<IconSize>(20);
  readonly ariaLabel = input<string | undefined>(undefined);
}

describe('Icon', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Host] });
  });

  function render(inputs: Partial<{ name: IconName; size: IconSize; ariaLabel: string }> = {}) {
    const fixture: ComponentFixture<Host> = TestBed.createComponent(Host);
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    fixture.detectChanges();
    return fixture;
  }

  function svg(fixture: ComponentFixture<Host>): SVGSVGElement {
    const el = (fixture.nativeElement as HTMLElement).querySelector('svg');
    if (!el) throw new Error('svg not rendered');
    return el as SVGSVGElement;
  }

  it('renders the verbatim shim markup for the requested icon', () => {
    const fixture = render({ name: 'x' });
    // innerHTML reserializes (`<path/>` → `<path></path>`), so assert the DOM,
    // not the string: same elements, same attributes as the shim transcript.
    const paths = Array.from(svg(fixture).querySelectorAll('path'));
    expect(paths.map((p) => p.getAttribute('d'))).toEqual(['M18 6 6 18', 'm6 6 12 12']);
    expect(ICON_PATHS['x']).toContain('M18 6 6 18');
  });

  it('draws on the EDS contract: 24×24 viewBox, currentColor stroke, no fill', () => {
    const el = svg(render());
    expect(el.getAttribute('viewBox')).toBe('0 0 24 24');
    expect(el.getAttribute('stroke')).toBe('currentColor');
    expect(el.getAttribute('stroke-width')).toBe('1.75');
    expect(el.getAttribute('fill')).toBe('none');
  });

  it('sizes via the width/height attributes (default 20)', () => {
    expect(svg(render()).getAttribute('width')).toBe('20');
    expect(svg(render({ size: 16 })).getAttribute('width')).toBe('16');
  });

  it('is aria-hidden and unfocusable when no ariaLabel is given (decorative)', () => {
    const el = svg(render());
    expect(el.getAttribute('aria-hidden')).toBe('true');
    expect(el.getAttribute('role')).toBeNull();
    expect(el.getAttribute('focusable')).toBe('false');
  });

  it('becomes role="img" with the resolved label when ariaLabel is given', () => {
    const el = svg(render({ ariaLabel: 'Etiket' }));
    expect(el.getAttribute('role')).toBe('img');
    expect(el.getAttribute('aria-label')).toBe('Etiket');
    expect(el.getAttribute('aria-hidden')).toBeNull();
  });

  it('ships every icon of the mock shim with non-empty markup', () => {
    for (const [name, markup] of Object.entries(ICON_PATHS)) {
      expect(markup.length, `icon "${name}" is empty`).toBeGreaterThan(0);
    }
    expect(Object.keys(ICON_PATHS).length).toBe(38);
  });
});
