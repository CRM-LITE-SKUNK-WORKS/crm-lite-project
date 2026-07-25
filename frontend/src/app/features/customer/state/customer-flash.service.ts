import { Injectable, signal } from '@angular/core';

/**
 * One-shot flash message between customer screens — the Angular counterpart of
 * the mock's `eds_flash` localStorage key (mock-ui-analysis §4.4: "router
 * state + servis (signal/store)"; localStorage is NOT carried over).
 *
 * Usage: the screen that finishes an action sets a CATALOGUE KEY (never
 * resolved text — the reader translates at display time, so a language switch
 * between navigation and display still renders correctly), navigates, and the
 * destination screen `consume()`s it exactly once to show a Toast.
 *
 * Root-provided deliberately: unlike screen state (FE-ADR-006 §1), a flash
 * must SURVIVE the navigation that destroys the sending screen's store.
 * First consumer: customer delete → Customer Search toast (FR-CUST-05).
 */
@Injectable({ providedIn: 'root' })
export class CustomerFlashService {
  private readonly _messageKey = signal<string | null>(null);

  /** Queue a flash for the next customer screen. `key` is a catalogue key. */
  set(key: string): void {
    this._messageKey.set(key);
  }

  /** Read-and-clear (the mock's eds_flash "okununca silinir" semantics). */
  consume(): string | null {
    const key = this._messageKey();
    this._messageKey.set(null);
    return key;
  }
}
