import { Injectable, signal } from '@angular/core';

/**
 * A queued one-shot message: a CATALOGUE KEY plus the parameters it interpolates
 * (FE-ADR-012 §h). The key is never pre-resolved — the reader translates at
 * display time, so a language switch between navigating and displaying still
 * renders correctly, and the same applies to the parameters, which are plain
 * values (an order number, a count) rather than translated fragments.
 */
export interface FlashMessage {
  readonly key: string;
  readonly params?: Readonly<Record<string, string | number>>;
}

/**
 * One-shot flash message between customer screens — the Angular counterpart of
 * the mock's `eds_flash` localStorage key (mock-ui-analysis §4.4: "router
 * state + servis (signal/store)"; localStorage is NOT carried over).
 *
 * Usage: the screen that finishes an action queues a key (+ params), navigates,
 * and the destination screen `consume()`s it exactly once to show a Toast.
 *
 * Root-provided deliberately: unlike screen state (FE-ADR-006 §1), a flash
 * must SURVIVE the navigation that destroys the sending screen's store.
 * Consumers: customer delete → Customer Search; customer create → Customer
 * Info; and (2026-08-05) order submit → Customer Info, which is what made the
 * `params` half necessary (scope §4.33).
 */
@Injectable({ providedIn: 'root' })
export class CustomerFlashService {
  private readonly _message = signal<FlashMessage | null>(null);

  /** Queue a flash for the next customer screen. `key` is a catalogue key. */
  set(key: string, params?: Readonly<Record<string, string | number>>): void {
    this._message.set(params ? { key, params } : { key });
  }

  /** Read-and-clear (the mock's eds_flash "okununca silinir" semantics). */
  consume(): FlashMessage | null {
    const message = this._message();
    this._message.set(null);
    return message;
  }
}
