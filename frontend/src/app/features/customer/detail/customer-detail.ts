import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { isApiError } from '../../../core/http';
import { I18nService, TranslatePipe } from '../../../core/i18n';
import {
  ConfirmDialog,
  EmptyState,
  Skeleton,
  Tabs,
  Toast,
  type TabItem,
} from '../../../shared/patterns';
import { Button, Icon, IconButton } from '../../../shared/ui';
// The sanctioned cross-feature imports (FE-ADR-003 §Consequences): the account
// tab consumes the components the account and product features EXPOSE. The
// direction is one-way in both cases — and account ⇄ product never touch: this
// screen is the only place they meet (scope §4.28).
import { AccountSection } from '../../account/section/account-section';
import {
  type BillingAddressOption,
  type NewBillingAddress,
} from '../../account/section/billing-address-option';
import { ProductSection } from '../../product/section/product-section';
import { AddressCards } from '../address/address-cards';
import { AddressFormDialog } from '../address/address-form-dialog';
import { CustomerDetailStore } from '../state/customer-detail.store';
import { CustomerFlashService, type FlashMessage } from '../state/customer-flash.service';
import { type AddressResponse, type CustomerDetailResponse } from '../model';
import { ContactFormDialog } from './contact-form-dialog';
import { DemographicFormDialog } from './demographic-form-dialog';

type TabId = 'info' | 'account' | 'address' | 'contact';

/** One read-only field of the info/contact grids (mock §6.4 read layout). */
interface ReadField {
  readonly id: string;
  readonly label: string;
  readonly value: string;
  readonly tabular: boolean;
}

/** Empty display value (mock §6.4: `—` em dash, not a translated word). */
const EM_DASH = '—';

/**
 * Customer Info — the second business screen (mock-ui-analysis §6.4; FR-CUST-02/04/05,
 * FR-ADDR-01..05, FR-CNTC-01/02).
 *
 * Golden-path conventions carried over from Customer Search:
 * - route-provided {@link CustomerDetailStore}; three INDEPENDENT section reads
 *   (detail has no addresses/contact — FRONTEND_BRAIN §4), each with its own
 *   loading/error/retry surface;
 * - `messageKey → i18n` exclusively; 401/403 are the core interceptors' job;
 * - every state carries a `data-testid`; all copy from the catalogue.
 *
 * Tab scope (FE-ADR-013 as amended twice):
 * - info / address / contact — fully functional;
 * - **account** — live since F6 via the account feature's `AccountSection`
 *   (FR-ACCT-01..04, KR-11; scope §4.24);
 * - **products** — live since 2026-07-31 via the product feature's
 *   `ProductSection`, rendered inside an EXPANDED account row exactly as the
 *   mock shows (FR-PROD-01..02; FE-ADR-013 §Amendment B; scope §4.28). The
 *   former "Coming soon" block is gone — §Amendment A3 no longer applies here.
 *   "Deactivate product" is the one piece still inert: it is a WRITE, and
 *   product-service Phase A has no write endpoint at all.
 *
 * Tab switches reset all sub-state — open dialogs, errors — per the mock §6.4
 * rule. Delete (FR-CUST-05) uses the shared ConfirmDialog: the 409
 * `MSG-CUST-HAS-PRODUCTS` answer (backend-enforced; never pre-computed) flips
 * the dialog into its documented error state; success flashes a toast onto
 * Customer Search via {@link CustomerFlashService}.
 */
@Component({
  selector: 'app-customer-detail',
  imports: [
    TranslatePipe,
    Button,
    Icon,
    IconButton,
    Tabs,
    Toast,
    Skeleton,
    EmptyState,
    ConfirmDialog,
    AccountSection,
    ProductSection,
    AddressCards,
    AddressFormDialog,
    ContactFormDialog,
    DemographicFormDialog,
  ],
  providers: [CustomerDetailStore],
  templateUrl: './customer-detail.html',
})
export class CustomerDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly flash = inject(CustomerFlashService);
  protected readonly store = inject(CustomerDetailStore);

  private readonly paramMap = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  /** Non-numeric path segment ⇒ treated as not-found without calling the API. */
  protected readonly badParam = computed(() => {
    const raw = this.paramMap().get('customerNumber') ?? '';
    return !/^\d+$/.test(raw);
  });

  /**
   * Entry point for another screen that needs Customer Info to OPEN somewhere
   * specific: `?tab=account&account={accountNumber}` lands on the account tab
   * with that billing account already expanded. The sale wizard uses it after a
   * successful submit, so the user sees the products the order just created
   * (scope §4.33).
   *
   * Deliberately URL state rather than a second flash channel: it describes
   * WHERE the user is, so it must survive a reload and be shareable, and an
   * unknown/absent value simply degrades to the default tab.
   */
  private readonly queryMap = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  private readonly requestedTab = computed<TabId | null>(() => {
    const raw = this.queryMap().get('tab');
    return raw === 'info' || raw === 'account' || raw === 'address' || raw === 'contact'
      ? raw
      : null;
  });
  /** Billing account to expand on arrival — read once, then owned by the user. */
  protected readonly expandAccountNumber = computed(() => this.queryMap().get('account'));

  protected readonly activeTab = signal<TabId>(this.requestedTab() ?? 'info');
  protected readonly tabs = computed<readonly TabItem[]>(() => [
    { id: 'info', label: this.i18n.translate('UI-DETAIL-TAB-INFO') },
    { id: 'account', label: this.i18n.translate('UI-DETAIL-TAB-ACCOUNT') },
    { id: 'address', label: this.i18n.translate('UI-DETAIL-TAB-ADDRESS') },
    { id: 'contact', label: this.i18n.translate('UI-DETAIL-TAB-CONTACT') },
  ]);

  // --- dialog / toast state (screen-owned; dialogs only emit intents) -------
  protected readonly infoDialogOpen = signal(false);
  protected readonly contactDialogOpen = signal(false);
  protected readonly addressDialogOpen = signal(false);
  /** Address being edited; `null` while adding. */
  protected readonly addressDialogTarget = signal<AddressResponse | null>(null);

  protected readonly deleteCustomerOpen = signal(false);
  protected readonly deleteCustomerError = signal<string | null>(null);
  protected readonly deleteCustomerBusy = signal(false);

  protected readonly deleteAddressTarget = signal<AddressResponse | null>(null);
  protected readonly deleteAddressError = signal<string | null>(null);
  protected readonly deleteAddressBusy = signal(false);

  /** Failure of the set-primary PATCH — surfaced as an address-tab banner. */
  protected readonly addressActionError = signal<string | null>(null);

  /** Success toast after an in-place save — seeded from the one-shot flash so
   *  the Create wizard's and the sale wizard's successes land here as a toast
   *  (mock §6.3: eds_flash is shown on Customer Info). */
  private readonly toastMessage = signal<FlashMessage | null>(this.flash.consume());

  /** Resolved toast text; the key is translated HERE, never by the sender. */
  protected readonly toastText = computed(() => {
    const message = this.toastMessage();
    return message ? this.i18n.translate(message.key, message.params) : null;
  });

  // --- read-view models -----------------------------------------------------
  protected readonly initials = computed(() => {
    const customer = this.store.customer();
    if (!customer) return '';
    return `${customer.firstName[0] ?? ''}${customer.lastName[0] ?? ''}`.toLocaleUpperCase();
  });
  protected readonly fullName = computed(() => {
    const customer = this.store.customer();
    if (!customer) return '';
    return [customer.firstName, customer.middleName, customer.lastName]
      .filter((part): part is string => !!part && part.trim() !== '')
      .join(' ');
  });

  protected readonly infoFields = computed<readonly ReadField[]>(() => {
    const customer = this.store.customer();
    if (!customer) return [];
    return [
      this.field('first-name', 'UI-CREATE-FIELD-FIRST-NAME', customer.firstName),
      this.field('second-name', 'UI-CREATE-FIELD-SECOND-NAME', customer.middleName),
      this.field('last-name', 'UI-CREATE-FIELD-LAST-NAME', customer.lastName),
      this.field('birth-date', 'UI-CREATE-FIELD-BIRTH-DATE', this.displayDate(customer.birthDate), true),
      this.field('gender', 'UI-CREATE-FIELD-GENDER', this.displayGender(customer)),
      this.field('father-name', 'UI-CREATE-FIELD-FATHER-NAME', customer.fatherName),
      this.field('mother-name', 'UI-CREATE-FIELD-MOTHER-NAME', customer.motherName),
      this.field('nationality-id', 'UI-CREATE-FIELD-NATIONALITY-ID', customer.nationalityId, true),
    ];
  });

  /** The customer's active addresses as billing-address choices for the
   *  account section — labels built HERE so the account feature never needs
   *  the customer's `AddressResponse` (FE-ADR-003; see BillingAddressOption). */
  protected readonly billingAddressOptions = computed<readonly BillingAddressOption[]>(() =>
    this.store.addresses().map((address) => ({
      addressId: address.addressId,
      label: `${address.districtName}, ${address.cityName} · ${address.street} ${address.houseFlatNumber}`,
    })),
  );

  // --- BUG-1: billing-address creation asked for by the account dialog -------
  protected readonly accountAddressBusy = signal(false);
  protected readonly accountAddressErrorText = signal<string | null>(null);
  protected readonly accountCreatedAddressId = signal<number | null>(null);

  /**
   * FR-ADDR-02 on behalf of the account dialog's inline panel. Deliberately the
   * SAME store method the Address tab's dialog and the §2.7 wizard call — there
   * is one address-creation path, and `addAddress` already refreshes the list,
   * so `billingAddressOptions` (and hence the dialog's Select) picks the new
   * address up without any extra fetch.
   */
  protected onAccountNewAddress(address: NewBillingAddress): void {
    if (this.accountAddressBusy()) return;
    this.accountAddressBusy.set(true);
    this.accountAddressErrorText.set(null);
    // Clear first, so creating a SECOND address is a real change for the
    // dialog's acknowledgement effect even if ids were somehow reused.
    this.accountCreatedAddressId.set(null);
    this.store.addAddress(address).subscribe({
      next: (created) => {
        this.accountAddressBusy.set(false);
        this.accountCreatedAddressId.set(created.addressId);
      },
      error: (error: unknown) => {
        // The panel shows it; the account dialog stays open and untouched.
        this.accountAddressBusy.set(false);
        this.accountAddressErrorText.set(
          this.i18n.translate(isApiError(error) ? error.messageKey : 'MSG-INTERNAL-ERROR'),
        );
      },
    });
  }

  protected readonly contactFields = computed<readonly ReadField[]>(() => {
    const contact = this.store.contact();
    if (!contact) return [];
    // Recorded §6.4 order: Email → Home → Mobile → Fax (read AND edit).
    return [
      this.field('email', 'UI-CREATE-FIELD-EMAIL', contact.email),
      this.field('home', 'UI-CREATE-FIELD-HOME', contact.homePhone, true),
      this.field('mobile', 'UI-CREATE-FIELD-MOBILE', contact.mobilePhone, true),
      this.field('fax', 'UI-CREATE-FIELD-FAX', contact.fax, true),
    ];
  });

  protected readonly detailErrorText = computed(() => {
    const error = this.store.detailError();
    if (this.store.detailStatus() !== 'error') return null;
    return this.i18n.translate(error?.messageKey ?? 'MSG-INTERNAL-ERROR');
  });
  protected readonly addressesErrorText = computed(() => {
    const error = this.store.addressesError();
    if (this.store.addressesStatus() !== 'error') return null;
    return this.i18n.translate(error?.messageKey ?? 'MSG-INTERNAL-ERROR');
  });
  protected readonly contactErrorText = computed(() => {
    const error = this.store.contactError();
    if (this.store.contactStatus() !== 'error') return null;
    return this.i18n.translate(error?.messageKey ?? 'MSG-INTERNAL-ERROR');
  });

  constructor() {
    effect(() => {
      const raw = this.paramMap().get('customerNumber') ?? '';
      if (/^\d+$/.test(raw)) {
        this.store.load(Number(raw));
      }
    });
  }

  /** Mock §6.4: a tab switch resets every sub-state (dialogs, errors). */
  protected onTab(id: string): void {
    this.activeTab.set(id as TabId);
    this.infoDialogOpen.set(false);
    this.contactDialogOpen.set(false);
    this.addressDialogOpen.set(false);
    this.addressDialogTarget.set(null);
    this.deleteCustomerOpen.set(false);
    this.deleteCustomerError.set(null);
    this.deleteAddressTarget.set(null);
    this.deleteAddressError.set(null);
    this.addressActionError.set(null);
    this.accountAddressBusy.set(false);
    this.accountAddressErrorText.set(null);
    this.accountCreatedAddressId.set(null);
  }

  // --- demographic ----------------------------------------------------------
  protected onInfoSaved(): void {
    this.infoDialogOpen.set(false);
    this.toastMessage.set({ key: 'UI-DETAIL-TOAST-INFO-SAVED' });
  }

  // --- contact --------------------------------------------------------------
  protected onContactSaved(): void {
    this.contactDialogOpen.set(false);
    this.toastMessage.set({ key: 'UI-DETAIL-TOAST-CONTACT-SAVED' });
  }

  // --- addresses ------------------------------------------------------------
  protected openAddAddress(): void {
    this.addressDialogTarget.set(null);
    this.addressDialogOpen.set(true);
  }
  protected openEditAddress(address: AddressResponse): void {
    this.addressDialogTarget.set(address);
    this.addressDialogOpen.set(true);
  }
  protected onAddressSaved(): void {
    this.addressDialogOpen.set(false);
    this.addressDialogTarget.set(null);
  }
  protected onAddressDialogClosed(): void {
    this.addressDialogOpen.set(false);
    this.addressDialogTarget.set(null);
  }

  protected askDeleteAddress(address: AddressResponse): void {
    this.deleteAddressError.set(null);
    this.deleteAddressTarget.set(address);
  }
  protected confirmDeleteAddress(): void {
    const target = this.deleteAddressTarget();
    if (!target || this.deleteAddressBusy()) return;
    this.deleteAddressBusy.set(true);
    this.store.deleteAddress(target.addressId).subscribe({
      next: () => {
        this.deleteAddressBusy.set(false);
        this.deleteAddressTarget.set(null);
      },
      error: (error: unknown) => {
        // The backend guards (last/primary/in-use) answer here — shown as the
        // ConfirmDialog's documented error state, never pre-computed.
        this.deleteAddressBusy.set(false);
        this.deleteAddressError.set(
          this.i18n.translate(isApiError(error) ? error.messageKey : 'MSG-INTERNAL-ERROR'),
        );
      },
    });
  }
  protected cancelDeleteAddress(): void {
    this.deleteAddressTarget.set(null);
    this.deleteAddressError.set(null);
  }

  protected setPrimary(address: AddressResponse): void {
    this.addressActionError.set(null);
    this.store.setPrimaryAddress(address.addressId).subscribe({
      error: (error: unknown) => {
        this.addressActionError.set(
          this.i18n.translate(isApiError(error) ? error.messageKey : 'MSG-INTERNAL-ERROR'),
        );
      },
    });
  }

  // --- delete customer (FR-CUST-05) ----------------------------------------
  protected askDeleteCustomer(): void {
    this.deleteCustomerError.set(null);
    this.deleteCustomerOpen.set(true);
  }
  protected confirmDeleteCustomer(): void {
    if (this.deleteCustomerBusy()) return;
    this.deleteCustomerBusy.set(true);
    this.store.deleteCustomer().subscribe({
      next: () => {
        // Success: flash onto Customer Search (mock §6.3 eds_flash pattern).
        this.flash.set('UI-DETAIL-TOAST-CUSTOMER-DELETED');
        void this.router.navigate(['/customers']);
      },
      error: (error: unknown) => {
        this.deleteCustomerBusy.set(false);
        this.deleteCustomerError.set(
          this.i18n.translate(isApiError(error) ? error.messageKey : 'MSG-INTERNAL-ERROR'),
        );
      },
    });
  }
  protected cancelDeleteCustomer(): void {
    this.deleteCustomerOpen.set(false);
    this.deleteCustomerError.set(null);
  }

  protected backToSearch(): void {
    void this.router.navigate(['/customers']);
  }

  /**
   * AC-SALE-01-01 entry point: open the §2.7 sale wizard for one billing
   * account. `accountNumber` rides as a QUERY param because it qualifies the
   * sale, it is not part of the route's identity.
   *
   * The button is disabled for Passive accounts, so this is only reachable for
   * an Active one; nothing is pre-checked here beyond that, because whether
   * the account may actually take an order is the backend's answer
   * (FE-ADR-013 §a).
   */
  protected startSale(accountNumber: string): void {
    const customerNumber = this.store.customerNumber();
    if (customerNumber === null) return;
    void this.router.navigate(['/customers', customerNumber, 'sales', 'new'], {
      queryParams: { accountNumber },
    });
  }

  protected dismissToast(): void {
    this.toastMessage.set(null);
  }

  // --- helpers --------------------------------------------------------------
  private field(id: string, labelKey: string, value: string | null, tabular = false): ReadField {
    return {
      id,
      label: this.i18n.translate(labelKey),
      value: value && value.trim() !== '' ? value : EM_DASH,
      tabular,
    };
  }

  /** ISO `yyyy-MM-dd` → fixed `dd.MM.yyyy` (scope §2.6 / §4.10). */
  private displayDate(iso: string): string {
    const [year, month, day] = iso.split('-');
    return day && month && year ? `${day}.${month}.${year}` : iso;
  }

  /** Localized DISPLAY of the wire gender value (scope §2.7 — data unchanged). */
  private displayGender(customer: CustomerDetailResponse): string {
    if (customer.gender === 'Male') return this.i18n.translate('UI-GENDER-MALE');
    return this.i18n.translate('UI-GENDER-FEMALE');
  }
}
