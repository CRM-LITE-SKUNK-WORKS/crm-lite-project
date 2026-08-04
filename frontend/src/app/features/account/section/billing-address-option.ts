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

/**
 * A new address collected by the dialog's inline "New address" panel (BUG-1,
 * mock's Create/Edit billing account modal; scope §4.30).
 *
 * Structurally identical to the customer feature's `AddressRequest` on purpose,
 * and declared here for the same reason {@link BillingAddressOption} is: the
 * account feature must not import the customer feature's model (FE-ADR-003).
 *
 * The account feature NEVER creates the address itself — FR-ADDR-02 is a
 * customer-service endpoint owned by `features/customer/`. This value travels
 * OUT of the dialog, up through `AccountSection`, to Customer Info, which writes
 * it through the existing `CustomerDetailStore.addAddress()` — the same service
 * call the address dialog and the §2.7 sale wizard already use. No second
 * address-creation path exists.
 */
export interface NewBillingAddress {
  readonly cityId: number;
  readonly districtId: number;
  readonly street: string;
  readonly houseFlatNumber: string;
  readonly addressDescription: string;
}
