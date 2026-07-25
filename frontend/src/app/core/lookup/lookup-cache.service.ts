import { Injectable, computed, inject, signal } from '@angular/core';
import { LookupApiService } from './lookup-api.service';
import { type City, type District } from './lookup.model';

/**
 * Signal-backed cache for the address cascade (FE-ADR-006 §1/§6). Cities and
 * districts are immutable reference data — **load once, keep** — so this holds
 * them in signals and never re-fetches a city's districts twice.
 *
 * Read-only signals out, intent-named methods in (FE-ADR-006 §2). No global
 * cache-invalidation framework (FE-ADR-006 §6): reference data does not change
 * within a session.
 */
@Injectable({ providedIn: 'root' })
export class LookupCacheService {
  private readonly api = inject(LookupApiService);

  private readonly _cities = signal<readonly City[]>([]);
  private readonly _citiesLoaded = signal(false);
  /** districts keyed by cityId; a present key means "loaded" (even if empty). */
  private readonly _districtsByCity = signal<ReadonlyMap<number, readonly District[]>>(new Map());

  /** In-flight guards keep a burst of calls from firing duplicate requests.
   *  Plain sets (not signals) — they are control state, not view state. */
  private citiesInFlight = false;
  private readonly districtsInFlight = new Set<number>();

  /** All cities (empty until {@link ensureCities} completes). */
  readonly cities = this._cities.asReadonly();
  readonly citiesLoaded = this._citiesLoaded.asReadonly();

  /** Load the city list once. Idempotent: a second call while loaded or in
   *  flight is a no-op. */
  ensureCities(): void {
    if (this._citiesLoaded() || this.citiesInFlight) {
      return;
    }
    this.citiesInFlight = true;
    this.api.getCities().subscribe({
      next: (cities) => {
        this._cities.set(cities);
        this._citiesLoaded.set(true);
        this.citiesInFlight = false;
      },
      error: () => {
        // The interceptor already surfaced the error (FE-ADR-008); allow a retry.
        this.citiesInFlight = false;
      },
    });
  }

  /** Districts already cached for a city (empty array if not loaded yet). A
   *  computed so templates stay reactive as the cache fills. */
  districtsOf(cityId: number): readonly District[] {
    return this._districtsByCity().get(cityId) ?? [];
  }

  /** `true` once this city's districts have been fetched (distinguishes
   *  "loaded, none" from "not loaded"). */
  hasDistricts(cityId: number): boolean {
    return this._districtsByCity().has(cityId);
  }

  /** Load one city's districts once. Idempotent per cityId. */
  ensureDistricts(cityId: number): void {
    if (this._districtsByCity().has(cityId) || this.districtsInFlight.has(cityId)) {
      return;
    }
    this.districtsInFlight.add(cityId);
    this.api.getDistricts(cityId).subscribe({
      next: (districts) => {
        this._districtsByCity.update((map) => new Map(map).set(cityId, districts));
        this.districtsInFlight.delete(cityId);
      },
      error: () => {
        this.districtsInFlight.delete(cityId);
      },
    });
  }

  /** Number of cities whose districts are cached — exposed for diagnostics/tests. */
  readonly cachedDistrictCount = computed(() => this._districtsByCity().size);
}
