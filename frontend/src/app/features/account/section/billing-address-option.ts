/**
 * A billing-address choice for the account dialogs, built by the CALLER
 * (Customer Info) from its own address data.
 *
 * This minimal shape exists so the account feature never imports the customer
 * feature's `AddressResponse` (FE-ADR-003: features do not reach into
 * siblings; the sanctioned dependency direction is customer → account —
 * FE-ADR-003 §Consequences). `addressId` is the customer-service address id
 * the contract expects; `label` is display-ready, resolved text.
 */
export interface BillingAddressOption {
  readonly addressId: number;
  readonly label: string;
}
