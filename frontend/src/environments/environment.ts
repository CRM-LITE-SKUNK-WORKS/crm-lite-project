/**
 * Base environment — used by `ng serve` and `ng build --configuration development`.
 *
 * FE-ADR-004 §2: there is deliberately NO API base URL here. Not "", not a
 * token, not "for later". Every HTTP call in the app uses a RELATIVE path
 * (`/api/...`), so the browser always talks to its own origin; the dev proxy
 * (`proxy.conf.json`) and nginx (container, FE-ADR-010) forward those prefixes
 * to the gateway.
 *
 * Adding an `apiBaseUrl` here is the exact failure FE-ADR-004 forbids: it
 * silently turns same-origin into cross-origin, which stops Angular's
 * HttpClient from attaching `X-XSRF-TOKEN` (-> 403 MSG-AUTH-CSRF-REJECTED on
 * every write) and stops the SameSite=Lax session cookie from being sent
 * (-> 401 everywhere). The only permitted difference between environments is a
 * build/optimization flag — never a connection target.
 */
export const environment = {
  production: false,
} as const;
