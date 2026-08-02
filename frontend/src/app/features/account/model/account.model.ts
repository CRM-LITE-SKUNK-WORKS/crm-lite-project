/**
 * account-service contract types (docs/api/account-service.md; ADR-013/014).
 *
 * ════════════════════════════════════════════════════════════════════════════
 * K-8 — THE 223 CUSTOMER ACCOUNT DOES NOT EXIST ON THE CLIENT.
 * ════════════════════════════════════════════════════════════════════════════
 * The automatic 223 "Customer Account" is a backend create-time side effect
 * (ADR-013 §4). It NEVER appears in any account-service response: the list is
 * 224-only, and a 223's number answers 404 on the single-account endpoints.
 * Therefore NO type, field, enum value, or call in this layer represents a 223.
 * The UI works exclusively with 224 Billing Accounts. Do NOT add a 223 type or
 * an `accountTypeCode: '223'` branch thinking something is "missing" — its
 * absence here is the contract.
 * ════════════════════════════════════════════════════════════════════════════
 */

/** Lifecycle status in the representation (`accountStatus`). Delete = passivation
 *  (FR-ACCT-04): a deleted account stays list-visible as `Passive`. */
export type AccountStatus = 'Active' | 'Passive';

/**
 * The single response shape (account-service.md §Representation). Every field
 * verified against the documented JSON — AND the exact key set is additionally
 * pinned by the Postman list test (docs/postman), which asserts
 * `Object.keys(r).sort() === ["accountName","accountNumber","accountStatus",
 * "accountTypeCode","accountTypeName","billingAddressId","customerNumber"]`:
 * this type matches it 1:1. The service never exposes internal ids or an
 * "Action" field (ADR-013 §3).
 */
export interface AccountResponse {
  /**
   * KR-11 Account Number: `VARCHAR(10)`, Luhn-checked, **immutable, never reused**
   * (ADR-014). A string, never a number — leading digits and the check digit are
   * significant. READ-ONLY in the UI: it is displayed, never edited, and never
   * appears in an update request (see {@link UpdateAccountRequest}).
   */
  readonly accountNumber: string;
  /**
   * The account owner's PUBLIC business customer number (the same value the list
   * endpoint takes as `customerId`) — never an internal id. Added by the ADR-013
   * §3.6 amendment for order-service (FR-SALE); the UI already knows which
   * customer it is showing, so nothing here needs to read it yet. It is typed
   * because the exact key set above is contract-pinned.
   */
  readonly customerNumber: number;
  /** User-facing name; the only freely editable field besides the address. */
  readonly accountName: string;
  /** Always `"224"` in responses — the 223 is never surfaced (K-8). Typed as the
   *  literal so no other value is expected. */
  readonly accountTypeCode: '224';
  /** e.g. `"Billing Account"`. */
  readonly accountTypeName: string;
  /** External reference to a customer-service address id (ADR-013 §2.4). NOTE the
   *  asymmetry: the response calls it `billingAddressId`, the create/update
   *  request calls it `addressId` (both documented). */
  readonly billingAddressId: number;
  readonly accountStatus: AccountStatus;
}

/**
 * Create body (`POST /api/accounts`, account-service.md §Semantics).
 * The account type is NOT client-selectable — the backend forces 224 (K-8
 * handles the 223 side effect). So there is deliberately no `accountType` field
 * here; sending one is rejected with 400 `MSG-ACCT-IMMUTABLE-FIELD`.
 */
export interface CreateAccountRequest {
  /** The public business customer number (e.g. 1001), NOT an internal id. */
  readonly customerId: number;
  readonly accountName: string;
  /** Must be in the customer's active address list (validated server-side). */
  readonly addressId: number;
}

/**
 * Update body (`PUT /api/accounts/{accountNumber}`). Mutable fields are EXACTLY
 * `accountName` and `addressId` (ADR-013 §3.4).
 *
 * KR-11 compliance by construction: `accountNumber` and `accountTypeCode` are
 * absent from this type, so the client can never send them. The backend rejects
 * any extra/immutable property with 400 `MSG-ACCT-IMMUTABLE-FIELD` — a
 * "send the whole object back" update would be a contract violation, which this
 * narrow type makes unrepresentable.
 */
export interface UpdateAccountRequest {
  readonly accountName: string;
  readonly addressId: number;
}
