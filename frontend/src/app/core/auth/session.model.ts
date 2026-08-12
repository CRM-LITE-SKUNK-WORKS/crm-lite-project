/**
 * Exact shape of `GET /api/session/me` — the gateway's
 * `SessionController.SessionResponse` (verified against source 2026-08-12).
 *
 * It is the SINGLE source of auth state (FE-ADR-005 §1). It contains WHO is
 * logged in and their roles — and, by contract, NEVER a token of any kind
 * (ADR-007, FE-ADR-005 §P3). `roles` are role names with the `ROLE_` prefix
 * already stripped by the gateway (e.g. `["crm-user"]`).
 */
export interface SessionResponse {
  readonly authenticated: boolean;
  readonly username: string;
  readonly subject: string;
  readonly roles: readonly string[];
  /**
   * Display name and job title for the header (scope §2.20, revised 2026-08-12).
   *
   * `titleCode` is a CODE (`"SALES_REP"`), not display text: the Keycloak
   * attribute holds a stable value and this application localizes it through the
   * i18n catalogue, the same division of labour the wire value `"Male"` gets
   * (scope §2.7). Never render it directly — see `Shell.title`.
   *
   * Both OPTIONAL because the GATEWAY REALLY RETURNS `null` FOR THEM — not to keep
   * the compiler quiet. `fullName` is null for a principal that is not an
   * `OidcUser`; `titleCode` is additionally null on any Keycloak realm whose
   * `titleCode` attribute or ID-token mapper has not been reconciled yet
   * (`keycloak-init` applies both on every `up`, but a running gateway may hold an
   * older session). Consumers must therefore fall back — the header falls back to
   * {@link username}.
   *
   * `| null` as well as optional: the gateway serialises the absent case as an
   * explicit JSON `null` (no `NON_NULL` inclusion), so the value that actually
   * arrives is `null`, not a missing key. Same shape as `contact.model.ts`.
   */
  readonly fullName?: string | null;
  readonly titleCode?: string | null;
}
