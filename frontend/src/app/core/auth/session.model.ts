/**
 * Exact shape of `GET /api/session/me` — the gateway's
 * `SessionController.SessionResponse` (verified against source 2026-07-24).
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
}
