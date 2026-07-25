/**
 * Contact-medium sub-module contract (customer-service.md §L).
 * One contact-medium record per customer (`GET/PUT
 * /api/customers/{n}/contact-medium`).
 */

/**
 * Contact update body (`PUT /api/customers/{n}/contact-medium`).
 * Field names verified against §Atomic create + §L: `email`, `mobilePhone`,
 * `homePhone`, `fax` (scope §2.10 — NOT the mock's `mobile`/`home`).
 *
 * The create pipeline requires email + mobile; home phone and fax are optional
 * (the doc's PUT example omits fax and sometimes homePhone). Client validation
 * is UX only; the backend is the authority (FE-ADR-007).
 */
export interface ContactMediumRequest {
  readonly email: string;
  readonly mobilePhone: string;
  readonly homePhone?: string | null;
  readonly fax?: string | null;
}

/**
 * Contact as returned by `GET /api/customers/{n}/contact-medium`.
 *
 * ✅ **VERIFIED against the running stack's Swagger (contact-medium-controller,
 * 2026-07-25, scope §4.18):** exactly `{email, homePhone, mobilePhone, fax}` —
 * no id, nothing else. Matches the field set the create/update bodies use.
 */
export interface ContactMediumResponse {
  readonly email: string;
  readonly mobilePhone: string;
  readonly homePhone: string | null;
  readonly fax: string | null;
}
