import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { type Observable } from 'rxjs';
import { type City, type District, type StatusLookup, type TypeLookup } from './lookup.model';

/**
 * HTTP client for shared reference data: the lookup-service catalogs
 * (`/api/lookups/**`, ADR-002) and customer-service's city/district cascade
 * (`/api/cities`, `/api/cities/{id}/districts`).
 *
 * Relative paths only (FE-ADR-004 §1); no error handling here — the core
 * interceptor normalizes failures (FE-ADR-008 §5). State/caching is the
 * `LookupCacheService`'s job (FE-ADR-006 §3, this service is a thin transport).
 */
@Injectable({ providedIn: 'root' })
export class LookupApiService {
  private readonly http = inject(HttpClient);

  /** `GET /api/lookups/statuses` (optional `?domain=`). */
  getStatuses(domain?: string): Observable<readonly StatusLookup[]> {
    return this.http.get<StatusLookup[]>('/api/lookups/statuses', {
      params: domain ? new HttpParams().set('domain', domain) : undefined,
    });
  }

  /** `GET /api/lookups/statuses/{shortCode}` (404 `MSG-LOOKUP-NOT-FOUND`). */
  getStatus(shortCode: string): Observable<StatusLookup> {
    return this.http.get<StatusLookup>(`/api/lookups/statuses/${encodeURIComponent(shortCode)}`);
  }

  /** `GET /api/lookups/types` (optional `?domain=`, e.g. `GENDER`). */
  getTypes(domain?: string): Observable<readonly TypeLookup[]> {
    return this.http.get<TypeLookup[]>('/api/lookups/types', {
      params: domain ? new HttpParams().set('domain', domain) : undefined,
    });
  }

  /** `GET /api/lookups/types/{shortCode}` (404 `MSG-LOOKUP-NOT-FOUND`). */
  getType(shortCode: string): Observable<TypeLookup> {
    return this.http.get<TypeLookup>(`/api/lookups/types/${encodeURIComponent(shortCode)}`);
  }

  /** `GET /api/cities` — all cities for the address cascade. */
  getCities(): Observable<readonly City[]> {
    return this.http.get<City[]>('/api/cities');
  }

  /** `GET /api/cities/{cityId}/districts` — districts of one city (cascades from
   *  the selected city). */
  getDistricts(cityId: number): Observable<readonly District[]> {
    return this.http.get<District[]>(`/api/cities/${cityId}/districts`);
  }
}
